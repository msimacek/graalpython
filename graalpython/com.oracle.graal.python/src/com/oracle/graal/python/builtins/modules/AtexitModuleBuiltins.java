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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContextFactory.GetThreadStateNodeGen;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@CoreFunctions(defineModule = "atexit")
public final class AtexitModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AtexitModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "register", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class RegisterNode extends PythonVarargsBuiltinNode {
        private static class AtExitRootNode extends RootNode {
            @Child private CallNode callNode = CallNode.create();
            @Child private GetThreadStateNode getThreadStateNode = GetThreadStateNodeGen.create();

            protected AtExitRootNode(TruffleLanguage<?> language) {
                super(language);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                PythonContext context = PythonContext.get(this);
                getThreadStateNode.setTopFrameInfoCached(context, PFrame.Reference.EMPTY);
                getThreadStateNode.executeCached(context).setCaughtException(PException.NO_EXCEPTION);

                Object callable = frame.getArguments()[0];
                Object[] arguments = (Object[]) frame.getArguments()[1];
                PKeyword[] keywords = (PKeyword[]) frame.getArguments()[2];

                // We deliberately pass 'null' frame here, the execution state will then be taken
                // from the context.
                try {
                    return callNode.execute(null, callable, arguments, keywords);
                } catch (PException e) {
                    handleException(context, e);
                    throw e;
                } finally {
                    getThreadStateNode.clearTopFrameInfoCached(context);
                    getThreadStateNode.executeCached(context).setCaughtException(null);
                }
            }

            @TruffleBoundary
            private static void handleException(PythonContext context, PException e) {
                Object pythonException = e.getEscapedException();
                if (!IsBuiltinClassProfile.profileClassSlowPath(GetClassNode.executeUncached(pythonException), PythonBuiltinClassType.SystemExit)) {
                    PyObjectCallMethodObjArgs callWrite = PyObjectCallMethodObjArgs.getUncached();
                    callWrite.execute(null, null, context.getStderr(), T_WRITE, toTruffleStringUncached("Error in atexit._run_exitfuncs:\n"));
                    try {
                        ExceptionUtils.printExceptionTraceback(context, pythonException);
                    } catch (PException pe) {
                        callWrite.execute(null, null, context.getStderr(), T_WRITE, toTruffleStringUncached("Failed to print traceback\n"));
                    }
                }
            }
        }

        @TruffleBoundary
        @Specialization
        static Object register(Object callable, Object[] arguments, PKeyword[] keywords) {
            PythonContext context = PythonContext.get(null);
            RootCallTarget callTarget = context.getLanguage().createCachedCallTarget(AtExitRootNode::new, AtExitRootNode.class);
            context.registerAtexitHook(callable, arguments, keywords, callTarget);
            return callable;
        }
    }

    @Builtin(name = "unregister", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UnregisterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object register(Object callable) {
            getContext().unregisterAtexitHook(callable);
            return PNone.NONE;
        }
    }

    @Builtin(name = "_clear")
    @GenerateNodeFactory
    abstract static class ClearNode extends PythonBuiltinNode {
        @Specialization
        Object clear() {
            getContext().clearAtexitHooks();
            return PNone.NONE;
        }
    }

    @Builtin(name = "_ncallbacks")
    @GenerateNodeFactory
    abstract static class NCallbacksNode extends PythonBuiltinNode {
        @Specialization
        int get() {
            return getContext().getAtexitHookCount();
        }
    }

    @Builtin(name = "_run_exitfuncs")
    @GenerateNodeFactory
    abstract static class RunExitfuncsNode extends PythonBuiltinNode {
        @Specialization
        Object run() {
            getContext().runAtexitHooks();
            return PNone.NONE;
        }
    }
}
