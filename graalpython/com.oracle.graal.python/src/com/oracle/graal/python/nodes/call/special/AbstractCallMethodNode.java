/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.PythonUtils.NodeCounterWithLimit;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@ImportStatic({PythonOptions.class, PGuards.class})
@NodeField(name = "maxSizeExceeded", type = boolean.class)
abstract class AbstractCallMethodNode extends PNodeWithContext {

    /**
     * for interpreter performance: cache if we exceeded the max caller size. We never allow
     * inlining in the uncached case.
     */
    protected abstract boolean isMaxSizeExceeded();

    protected abstract void setMaxSizeExceeded(boolean value);

    protected PythonBuiltinBaseNode getBuiltin(VirtualFrame frame, PBuiltinFunction func, int nargs) {
        CompilerAsserts.neverPartOfCompilation();
        NodeFactory<? extends PythonBuiltinBaseNode> builtinNodeFactory = func.getBuiltinNodeFactory();
        if (builtinNodeFactory == null) {
            return null; // see for example MethodDescriptorRoot and subclasses
        }
        if (TpSlotBuiltin.isSlotFactory(builtinNodeFactory)) {
            // slot wrapper must perform validation implemented in WrapperDescrCall inside the
            // PBuiltinRootNode
            return null;
        }
        Class<? extends PythonBuiltinBaseNode> nodeClass = builtinNodeFactory.getNodeClass();
        int builtinNodeArity = getBuiltinNodeArity(nodeClass);
        if (builtinNodeArity == -1 || builtinNodeArity < nargs) {
            return null;
        }
        SlotSignature slotSignature = nodeClass.getAnnotation(SlotSignature.class);
        if (slotSignature != null) {
            if (slotSignature.needsFrame() || nargs < slotSignature.minNumOfPositionalArgs()) {
                return null;
            }
            int maxArgs = Math.max(Math.max(slotSignature.maxNumOfPositionalArgs(), slotSignature.minNumOfPositionalArgs()), slotSignature.parameterNames().length);
            if (nargs > maxArgs) {
                return null;
            }

        } else {
            Builtin[] builtinAnnotations = nodeClass.getAnnotationsByType(Builtin.class);
            if (builtinAnnotations.length > 0) {
                Builtin builtinAnnotation = builtinAnnotations[0];
                if (builtinAnnotation.needsFrame() && frame == null) {
                    return null;
                }
                int maxArgs = Math.max(Math.max(builtinAnnotation.maxNumOfPositionalArgs(), builtinAnnotation.minNumOfPositionalArgs()), builtinAnnotation.parameterNames().length);
                if (nargs < builtinAnnotation.minNumOfPositionalArgs() || nargs > maxArgs) {
                    return null;
                }
            }
        }
        PythonBuiltinBaseNode builtinNode = builtinNodeFactory.createNode();
        if (!callerExceedsMaxSize(builtinNode)) {
            return builtinNode;
        }
        return null;
    }

    private static int getBuiltinNodeArity(Class<? extends PythonBuiltinBaseNode> nodeClass) {
        if (PythonQuaternaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            return 4;
        } else if (PythonTernaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            return 3;
        } else if (PythonBinaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            return 2;
        } else if (PythonUnaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
            return 1;
        }
        return -1;
    }

    private <T extends PythonBuiltinBaseNode> boolean callerExceedsMaxSize(T builtinNode) {
        CompilerAsserts.neverPartOfCompilation();
        if (isAdoptable() && !isMaxSizeExceeded()) {
            // to avoid building up AST of recursive builtin calls we check that the same builtin
            // isn't already our parent.
            Class<? extends PythonBuiltinBaseNode> builtinClass = builtinNode.getClass();
            Node parent = getParent();
            int recursiveCalls = 0;
            PythonLanguage language = PythonLanguage.get(this);
            while (parent != null && !(parent instanceof RootNode)) {
                if (parent.getClass() == builtinClass) {
                    int recursionLimit = language.getEngineOption(PythonOptions.NodeRecursionLimit);
                    if (recursiveCalls == recursionLimit) {
                        return true;
                    }
                    recursiveCalls++;
                }
                parent = parent.getParent();
            }

            RootNode root = getRootNode();
            // nb: option 'BuiltinsInliningMaxCallerSize' is defined as a compatible option, i.e.,
            // ASTs will only be shared between contexts that have the same value for this option.
            int maxSize = language.getEngineOption(PythonOptions.BuiltinsInliningMaxCallerSize);
            if (root instanceof PRootNode) {
                PRootNode pRoot = (PRootNode) root;
                int rootNodeCount = pRoot.getNodeCountForInlining();
                if (rootNodeCount < maxSize) {
                    NodeCounterWithLimit counter = new NodeCounterWithLimit(rootNodeCount, maxSize);
                    builtinNode.accept(counter);
                    if (counter.isOverLimit()) {
                        setMaxSizeExceeded(true);
                        return true;
                    }
                    pRoot.setNodeCountForInlining(counter.getCount());
                }
            } else {
                NodeCounterWithLimit counter = new NodeCounterWithLimit(maxSize);
                root.accept(counter);
                if (!counter.isOverLimit()) {
                    builtinNode.accept(counter);
                }
                if (counter.isOverLimit()) {
                    setMaxSizeExceeded(true);
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    protected static boolean takesSelfArg(Object func) {
        if (func instanceof PBuiltinFunction) {
            RootNode functionRootNode = ((PBuiltinFunction) func).getFunctionRootNode();
            if (functionRootNode instanceof BuiltinFunctionRootNode) {
                return ((BuiltinFunctionRootNode) functionRootNode).declaresExplicitSelf();
            }
        } else if (func instanceof PBuiltinMethod) {
            return takesSelfArg(((PBuiltinMethod) func).getFunction());
        }
        return true;
    }

    protected static RootCallTarget getCallTarget(PMethod meth, GetCallTargetNode getCtNode) {
        return getCtNode.execute(meth.getFunction());
    }

    protected static RootCallTarget getCallTarget(PBuiltinMethod meth, GetCallTargetNode getCtNode) {
        return getCtNode.execute(meth.getFunction());
    }

    protected static Object callUnaryBuiltin(VirtualFrame frame, PythonBuiltinBaseNode builtin, Object arg1) {
        CompilerAsserts.partialEvaluationConstant(builtin);
        if (builtin instanceof PythonUnaryBuiltinNode) {
            return ((PythonUnaryBuiltinNode) builtin).execute(frame, arg1);
        } else if (builtin instanceof PythonBinaryBuiltinNode) {
            return ((PythonBinaryBuiltinNode) builtin).execute(frame, arg1, PNone.NO_VALUE);
        } else if (builtin instanceof PythonTernaryBuiltinNode) {
            return ((PythonTernaryBuiltinNode) builtin).execute(frame, arg1, PNone.NO_VALUE, PNone.NO_VALUE);
        } else if (builtin instanceof PythonQuaternaryBuiltinNode) {
            return ((PythonQuaternaryBuiltinNode) builtin).execute(frame, arg1, PNone.NO_VALUE, PNone.NO_VALUE, PNone.NO_VALUE);
        }
        throw CompilerDirectives.shouldNotReachHere("Unexpected builtin node type");
    }

    protected static Object callBinaryBuiltin(VirtualFrame frame, PythonBuiltinBaseNode builtin, Object arg1, Object arg2) {
        CompilerAsserts.partialEvaluationConstant(builtin);
        if (builtin instanceof PythonBinaryBuiltinNode) {
            return ((PythonBinaryBuiltinNode) builtin).execute(frame, arg1, arg2);
        } else if (builtin instanceof PythonTernaryBuiltinNode) {
            return ((PythonTernaryBuiltinNode) builtin).execute(frame, arg1, arg2, PNone.NO_VALUE);
        } else if (builtin instanceof PythonQuaternaryBuiltinNode) {
            return ((PythonQuaternaryBuiltinNode) builtin).execute(frame, arg1, arg2, PNone.NO_VALUE, PNone.NO_VALUE);
        }
        throw CompilerDirectives.shouldNotReachHere("Unexpected builtin node type");
    }

    protected static Object callTernaryBuiltin(VirtualFrame frame, PythonBuiltinBaseNode builtin, Object arg1, Object arg2, Object arg3) {
        CompilerAsserts.partialEvaluationConstant(builtin);
        if (builtin instanceof PythonTernaryBuiltinNode) {
            return ((PythonTernaryBuiltinNode) builtin).execute(frame, arg1, arg2, arg3);
        } else if (builtin instanceof PythonQuaternaryBuiltinNode) {
            return ((PythonQuaternaryBuiltinNode) builtin).execute(frame, arg1, arg2, arg3, PNone.NO_VALUE);
        }
        throw CompilerDirectives.shouldNotReachHere("Unexpected builtin node type");
    }

    protected static Object callQuaternaryBuiltin(VirtualFrame frame, PythonBuiltinBaseNode builtin, Object arg1, Object arg2, Object arg3, Object arg4) {
        CompilerAsserts.partialEvaluationConstant(builtin);
        if (builtin instanceof PythonQuaternaryBuiltinNode) {
            return ((PythonQuaternaryBuiltinNode) builtin).execute(frame, arg1, arg2, arg3, arg4);
        }
        throw CompilerDirectives.shouldNotReachHere("Unexpected builtin node type");
    }
}
