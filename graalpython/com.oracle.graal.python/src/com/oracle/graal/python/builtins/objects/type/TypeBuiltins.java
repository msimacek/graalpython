/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.builtins.objects.str.StringUtils.containsNullCharacter;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FLAGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MRO__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ALLOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSES__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenPythonKey;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory.CallNodeFactory;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CheckCompatibleForAssigmentNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBestBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectGetBasesNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectIsSubclassNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonClass)
public class TypeBuiltins extends PythonBuiltins {

    public static final HiddenPythonKey TYPE_DICTOFFSET = new HiddenPythonKey(__DICTOFFSET__);
    public static final HiddenPythonKey TYPE_ITEMSIZE = new HiddenPythonKey(__ITEMSIZE__);
    public static final HiddenPythonKey TYPE_BASICSIZE = new HiddenPythonKey(__BASICSIZE__);
    public static final HiddenPythonKey TYPE_ALLOC = new HiddenPythonKey(__ALLOC__);
    public static final HiddenPythonKey TYPE_DEALLOC = new HiddenPythonKey("__dealloc__");
    public static final HiddenPythonKey TYPE_DEL = new HiddenPythonKey("__del__");
    public static final HiddenPythonKey TYPE_FREE = new HiddenPythonKey("__free__");
    public static final HiddenPythonKey TYPE_FLAGS = new HiddenPythonKey(__FLAGS__);
    public static final HiddenPythonKey TYPE_VECTORCALL_OFFSET = new HiddenPythonKey("__vectorcall_offset__");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static String repr(VirtualFrame frame, Object self,
                        @Cached("create(__MODULE__)") GetFixedAttributeNode readModuleNode,
                        @Cached("create(__QUALNAME__)") GetFixedAttributeNode readQualNameNode) {
            Object moduleName = readModuleNode.executeObject(frame, self);
            Object qualName = readQualNameNode.executeObject(frame, self);
            return concat(moduleName, qualName);
        }

        @TruffleBoundary
        private static String concat(Object moduleName, Object qualName) {
            if (moduleName != PNone.NO_VALUE && !moduleName.equals(BuiltinNames.BUILTINS)) {
                return String.format("<class '%s.%s'>", moduleName, qualName);
            }
            return String.format("<class '%s'>", qualName);
        }
    }

    @Builtin(name = __MRO__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MroAttrNode extends PythonBuiltinNode {
        @Specialization
        Object doit(Object klass,
                        @Cached("create()") TypeNodes.GetMroNode getMroNode,
                        @Cached TypeNodes.GetMroStorageNode getMroStorageNode,
                        @Cached("createBinaryProfile()") ConditionProfile notInitialized) {
            if (notInitialized.profile(klass instanceof PythonManagedClass && !getMroStorageNode.execute(klass).isInitialized())) {
                return PNone.NONE;
            }
            PythonAbstractClass[] mro = getMroNode.execute(klass);
            return factory().createTuple(mro);
        }
    }

    @Builtin(name = MRO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MroNode extends PythonBuiltinNode {
        @Specialization(guards = "lib.isLazyPythonClass(klass)")
        Object doit(Object klass,
                        @Cached("create()") GetMroNode getMroNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            PythonAbstractClass[] mro = getMroNode.execute(klass);
            return factory().createList(Arrays.copyOf(mro, mro.length, Object[].class));
        }

        @Specialization(guards = "!lib.isLazyPythonClass(object)")
        Object doit(Object object,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, MRO, "type", object);
        }
    }

    @Builtin(name = __INIT__, takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Child private SplitArgsNode splitArgsNode;

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.execute(arguments), keywords);
        }

        @Specialization
        Object init(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] kwds) {
            if (arguments.length != 1 && arguments.length != 3) {
                throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type.__init__()", 1, 3);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Child private CallVarargsMethodNode dispatchNew = CallVarargsMethodNode.create();
        @Child private LookupAndCallTernaryNode callNewGet = LookupAndCallTernaryNode.create(__GET__);
        @Child private LookupAttributeInMRONode lookupNew = LookupAttributeInMRONode.create(__NEW__);
        @Child private CallVarargsMethodNode dispatchInit = CallVarargsMethodNode.create();
        @Child private LookupSpecialMethodNode lookupInit = LookupSpecialMethodNode.create(__INIT__);
        @Child private IsSubtypeNode isSubTypeNode;
        @Child private TypeNodes.GetNameNode getNameNode;

        @CompilationFinal private boolean newWasDescriptor = false;

        public static CallNode create() {
            return CallNodeFactory.create();
        }

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, PNone.NO_VALUE, arguments, keywords);
        }

        protected static Object first(Object[] ary) {
            return ary[0];
        }

        protected static boolean accept(Object[] ary, Object cachedSelf, InteropLibrary lib) {
            Object first = first(ary);
            return first == cachedSelf && PGuards.isClass(first, lib);
        }

        /* self is in the arguments */
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"accept(arguments, cachedSelf, lib)",
                        "isPythonBuiltinClass(cachedSelf)"}, assumptions = "singleContextAssumption()")
        protected Object doItUnboxedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") PNone noSelf, Object[] arguments, PKeyword[] keywords,
                        @Cached("first(arguments)") Object cachedSelf,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            PythonBuiltinClassType type = ((PythonBuiltinClass) cachedSelf).getType();
            arguments[0] = type;
            return op(frame, type, arguments, keywords, false, plib);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"accept(arguments, cachedSelf, lib)",
                        "!isPythonBuiltinClass(cachedSelf)"}, assumptions = "singleContextAssumption()")
        protected Object doItUnboxedUser(VirtualFrame frame, @SuppressWarnings("unused") PNone noSelf, Object[] arguments, PKeyword[] keywords,
                        @Cached("first(arguments)") Object cachedSelf,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, cachedSelf, arguments, keywords, false, plib);

        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"accept(arguments, cachedSelf, lib)", "isPythonBuiltinClassType(cachedSelf)"})
        protected Object doItUnboxedBuiltinType(VirtualFrame frame, @SuppressWarnings("unused") PNone noSelf, Object[] arguments, PKeyword[] keywords,
                        @Cached("first(arguments)") Object cachedSelf,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, cachedSelf, arguments, keywords, false, plib);
        }

        @Specialization(replaces = {"doItUnboxedUser", "doItUnboxedBuiltin", "doItUnboxedBuiltinType"})
        protected Object doItUnboxedIndirect(VirtualFrame frame, PNone noSelf, Object[] arguments, PKeyword[] keywords,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            Object self = arguments[0];
            if (self instanceof PythonBuiltinClassType) {
                return doItUnboxedBuiltinType(frame, noSelf, arguments, keywords, self, lib, plib);
            } else if (PGuards.isPythonBuiltinClass(self)) {
                return doItUnboxedBuiltin(frame, noSelf, arguments, keywords, self, lib, plib);
            } else if (PGuards.isClass(self, lib)) {
                return doItUnboxedUser(frame, noSelf, arguments, keywords, self, lib, plib);
            } else {
                throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__call__", "type", self);
            }
        }

        /* self is first argument */
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf"}, assumptions = "singleContextAssumption()")
        protected Object doIt0BuiltinSingle(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self") PythonBuiltinClass cachedSelf,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, cachedSelf.getType(), arguments, keywords, true, plib);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf", "isPythonClass(cachedSelf)",
                        "!isPythonBuiltinClass(cachedSelf)"}, assumptions = "singleContextAssumption()")
        protected Object doIt0User(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self") Object cachedSelf,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, cachedSelf, arguments, keywords, true, plib);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self.getType() == cachedType"})
        protected Object doIt0BuiltinMulti(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self.getType()") PythonBuiltinClassType cachedType,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, cachedType, arguments, keywords, true, plib);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedType"})
        protected Object doIt0BuiltinType(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClassType self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self") PythonBuiltinClassType cachedType,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, cachedType, arguments, keywords, true, plib);
        }

        @Specialization(replaces = {"doIt0BuiltinSingle", "doIt0BuiltinMulti"})
        protected Object doItIndirect0Builtin(VirtualFrame frame, PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, self.getType(), arguments, keywords, true, plib);
        }

        @Specialization(replaces = "doIt0BuiltinType")
        protected Object doItIndirect0BuiltinType(VirtualFrame frame, PythonBuiltinClassType self, Object[] arguments, PKeyword[] keywords,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, self, arguments, keywords, true, plib);
        }

        @Specialization(replaces = {"doIt0User"}, guards = "!isPythonBuiltinClass(self)")
        protected Object doItIndirect0User(VirtualFrame frame, PythonAbstractClass self, Object[] arguments, PKeyword[] keywords,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, self, arguments, keywords, true, plib);
        }

        /* self is native */
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf"}, assumptions = "singleContextAssumption()")
        protected Object doIt1(VirtualFrame frame, @SuppressWarnings("unused") PythonNativeObject self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self") PythonNativeObject cachedSelf,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, PythonNativeClass.cast(cachedSelf), arguments, keywords, true, plib);
        }

        @Specialization(replaces = "doIt1")
        protected Object doItIndirect1(VirtualFrame frame, PythonNativeObject self, Object[] arguments, PKeyword[] keywords,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            return op(frame, PythonNativeClass.cast(self), arguments, keywords, true, plib);
        }

        private Object op(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs, PythonObjectLibrary lib) {
            Object newMethod = lookupNew.execute(self);
            if (newMethod != PNone.NO_VALUE) {
                CompilerAsserts.partialEvaluationConstant(doCreateArgs);
                Object[] newArgs = doCreateArgs ? PositionalArgumentsNode.prependArgument(self, arguments) : arguments;
                Object newInstance;
                if (!newWasDescriptor && (newMethod instanceof PFunction || newMethod instanceof PBuiltinFunction)) {
                    newInstance = dispatchNew.execute(frame, newMethod, newArgs, keywords);
                } else {
                    if (!newWasDescriptor) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        reportPolymorphicSpecialize();
                        newWasDescriptor = true;
                    }
                    newInstance = dispatchNew.execute(frame, callNewGet.execute(frame, newMethod, PNone.NONE, self), newArgs, keywords);
                }
                Object newInstanceKlass = lib.getLazyPythonClass(newInstance);
                if (isSubType(newInstanceKlass, self)) {
                    Object initMethod = lookupInit.execute(frame, newInstanceKlass, newInstance);
                    if (initMethod != PNone.NO_VALUE) {
                        Object[] initArgs;
                        if (doCreateArgs) {
                            initArgs = PositionalArgumentsNode.prependArgument(newInstance, arguments);
                        } else {
                            // XXX: (tfel) is this valid? I think it should be fine...
                            arguments[0] = newInstance;
                            initArgs = arguments;
                        }
                        Object initResult = dispatchInit.execute(frame, initMethod, initArgs, keywords);
                        if (initResult != PNone.NONE && initResult != PNone.NO_VALUE) {
                            throw raise(TypeError, ErrorMessages.SHOULD_RETURN_NONE, "__init__()");
                        }
                    }
                }
                return newInstance;
            } else {
                throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, getTypeName(self));
            }
        }

        private boolean isSubType(Object left, Object right) {
            if (isSubTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubTypeNode = insert(IsSubtypeNode.create());
            }
            return isSubTypeNode.execute(left, right);
        }

        private String getTypeName(Object clazz) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(clazz);
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = __GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBinaryBuiltinNode {
        public static GetattributeNode create() {
            return TypeBuiltinsFactory.GetattributeNodeFactory.create();
        }

        private final BranchProfile hasDescProfile = BranchProfile.create();
        private final BranchProfile isDescProfile = BranchProfile.create();
        private final BranchProfile hasValueProfile = BranchProfile.create();
        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile getClassProfile = ConditionProfile.createBinaryProfile();

        @Child private LookupAttributeInMRONode.Dynamic lookup = LookupAttributeInMRONode.Dynamic.create();
        @Child private LookupInheritedAttributeNode valueGetLookup;
        @Child private LookupAttributeInMRONode lookupGetNode;
        @Child private LookupAttributeInMRONode lookupSetNode;
        @Child private LookupAttributeInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode invokeGet;
        @Child private CallTernaryMethodNode invokeValueGet;
        @Child private LookupAttributeInMRONode.Dynamic lookupAsClass;
        @Child private TypeNodes.GetNameNode getNameNode;

        @Specialization(limit = "3")
        protected Object doIt(VirtualFrame frame, Object object, Object keyObj,
                        @CachedLibrary("object") PythonObjectLibrary libObj,
                        @CachedLibrary(limit = "3") PythonObjectLibrary libDesc,
                        @Cached CastToJavaStringNode castToString) {
            String key;
            try {
                key = castToString.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            Object type = libObj.getLazyPythonClass(object);
            Object descr = lookup.execute(type, key);
            Object get = null;
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                Object dataDescClass = libDesc.getLazyPythonClass(descr);
                get = lookupGet(dataDescClass);
                if (PGuards.isCallable(get)) {
                    Object delete = PNone.NO_VALUE;
                    Object set = lookupSet(dataDescClass);
                    if (set == PNone.NO_VALUE) {
                        delete = lookupDelete(dataDescClass);
                    }
                    if (set != PNone.NO_VALUE || delete != PNone.NO_VALUE) {
                        isDescProfile.enter();
                        // Only override if __get__ is defined, too, for compatibility with CPython.
                        if (invokeGet == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            invokeGet = insert(CallTernaryMethodNode.create());
                        }
                        return invokeGet.execute(frame, get, descr, object, getPythonClass(type, getClassProfile));
                    }
                }
            }
            Object value = readAttribute(object, key);
            if (value != PNone.NO_VALUE) {
                hasValueProfile.enter();
                Object valueGet = lookupValueGet(value);
                if (valueGet == PNone.NO_VALUE) {
                    return value;
                } else if (PGuards.isCallable(valueGet)) {
                    if (invokeValueGet == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invokeValueGet = insert(CallTernaryMethodNode.create());
                    }
                    return invokeValueGet.execute(frame, valueGet, value, PNone.NONE, object);
                }
            }
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                if (get == PNone.NO_VALUE) {
                    return descr;
                } else if (PGuards.isCallable(get)) {
                    if (invokeGet == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invokeGet = insert(CallTernaryMethodNode.create());
                    }
                    return invokeGet.execute(frame, get, descr, object, getPythonClass(type, getClassProfile));
                }
            }
            errorProfile.enter();
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, getTypeName(object), key);
        }

        private Object readAttribute(Object object, Object key) {
            if (lookupAsClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAsClass = insert(LookupAttributeInMRONode.Dynamic.create());
            }
            return lookupAsClass.execute(object, key);
        }

        private Object lookupDelete(Object dataDescClass) {
            if (lookupDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupDeleteNode = insert(LookupAttributeInMRONode.create(__DELETE__));
            }
            return lookupDeleteNode.execute(dataDescClass);
        }

        private Object lookupSet(Object dataDescClass) {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupAttributeInMRONode.create(__SET__));
            }
            return lookupSetNode.execute(dataDescClass);
        }

        private Object lookupGet(Object dataDescClass) {
            if (lookupGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetNode = insert(LookupAttributeInMRONode.create(__GET__));
            }
            return lookupGetNode.execute(dataDescClass);
        }

        private Object lookupValueGet(Object value) {
            if (valueGetLookup == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                valueGetLookup = insert(LookupInheritedAttributeNode.create(__GET__));
            }
            return valueGetLookup.execute(value);
        }

        private String getTypeName(Object clazz) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(clazz);
        }
    }

    @Builtin(name = __PREPARE__, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class PrepareNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doIt(Object args, Object kwargs) {
            return factory().createDict();
        }
    }

    @Builtin(name = __BASES__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object getBases(Object self, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") TypeNodes.GetBaseClassesNode getBaseClassesNode) {
            return factory().createTuple(getBaseClassesNode.execute(self));
        }

        @Specialization
        Object setBases(VirtualFrame frame, PythonClass cls, PTuple value,
                        @Cached GetNameNode getName,
                        @Cached GetObjectArrayNode getArray,
                        @Cached GetBaseClassNode getBase,
                        @Cached GetBestBaseClassNode getBestBase,
                        @Cached CheckCompatibleForAssigmentNode checkCompatibleForAssigment,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetMroNode getMroNode) {

            Object[] a = getArray.execute(value);
            if (a.length == 0) {
                throw raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_NON_EMPTY_TUPLE_TO_P, cls);
            }
            PythonAbstractClass[] baseClasses = new PythonAbstractClass[a.length];
            for (int i = 0; i < a.length; i++) {
                if (PGuards.isPythonClass(a[i])) {
                    if (isSubtypeNode.execute(frame, a[i], cls) ||
                                    hasMRO(getMroNode, a[i]) && typeIsSubtypeBaseChain(a[i], cls, getBase, isSameTypeNode)) {
                        throw raise(TypeError, ErrorMessages.BASES_ITEM_CAUSES_INHERITANCE_CYCLE);
                    }
                    baseClasses[i] = (PythonAbstractClass) a[i];
                } else {
                    throw raise(TypeError, ErrorMessages.MUST_BE_TUPLE_OF_CLASSES_NOT_P, getName.execute(cls), "__bases__", a[i]);
                }
            }

            Object newBestBase = getBestBase.execute(baseClasses);
            if (newBestBase == null) {
                return null;
            }

            Object oldBase = getBase.execute(cls);
            checkCompatibleForAssigment.execute(frame, oldBase, newBestBase);

            cls.setSuperClass(baseClasses);

            return PNone.NONE;
        }

        private static boolean hasMRO(GetMroNode getMroNode, Object i) {
            PythonAbstractClass[] mro = getMroNode.execute(i);
            return mro != null && mro.length > 0;
        }

        private static boolean typeIsSubtypeBaseChain(Object a, Object b, GetBaseClassNode getBaseNode, IsSameTypeNode isSameTypeNode) {
            Object base = a;
            do {
                if (isSameTypeNode.execute(base, b)) {
                    return true;
                }
                base = getBaseNode.execute(base);
            } while (base != null);

            return (isSameTypeNode.execute(b, PythonBuiltinClassType.PythonObject));
        }

        @Specialization(guards = "!isPTuple(value)")
        Object setObject(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") Object value,
                        @Cached GetNameNode getName) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_S_S_NOT_P, "tuple", getName.execute(cls), "__bases__", value);
        }

        @Specialization
        Object setBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value,
                        @Cached GetNameNode getName) {
            throw raise(TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE_S, getName.execute(cls));
        }

    }

    @Builtin(name = __BASE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BaseNode extends PythonBuiltinNode {
        @Specialization
        static Object base(Object self,
                        @Cached TypeNodes.GetBaseClassNode getBaseClassNode) {
            Object baseClass = getBaseClassNode.execute(self);
            return baseClass != null ? baseClass : PNone.NONE;
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doType(PythonBuiltinClassType self,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            return doManaged(getCore().lookupType(self), lib);
        }

        @Specialization(limit = "1")
        Object doManaged(PythonManagedClass self,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            PDict dict = lib.getDict(self);
            if (dict == null) {
                dict = factory().createDictFixedStorage(self);
                // The mapping is unmodifiable, so we don't have to assign it back
            }
            return factory().createMappingproxy(dict);
        }

        @Specialization
        static Object doNative(PythonNativeClass self,
                        @Cached CExtNodes.GetTypeMemberNode getTpDictNode) {
            return getTpDictNode.execute(self, NativeMember.TP_DICT);
        }
    }

    @Builtin(name = __INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Child private LookupAndCallBinaryNode getAttributeNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child private AbstractObjectIsSubclassNode abstractIsSubclassNode = AbstractObjectIsSubclassNode.create();
        @Child private AbstractObjectGetBasesNode getBasesNode = AbstractObjectGetBasesNode.create();

        private final ConditionProfile typeErrorProfile = ConditionProfile.createBinaryProfile();

        public abstract boolean executeWith(VirtualFrame frame, Object cls, Object instance);

        public static InstanceCheckNode create() {
            return TypeBuiltinsFactory.InstanceCheckNodeFactory.create();
        }

        private PythonObject getInstanceClassAttr(VirtualFrame frame, Object instance) {
            Object classAttr = getAttributeNode.executeObject(frame, instance, __CLASS__);
            if (classAttr instanceof PythonObject) {
                return (PythonObject) classAttr;
            }
            return null;
        }

        @Specialization(guards = "plib.isLazyPythonClass(cls)")
        boolean isInstance(VirtualFrame frame, Object cls, Object instance,
                        @Cached("create()") IsSubtypeNode isSubtypeNode,
                        @CachedLibrary(limit = "4") PythonObjectLibrary plib) {
            if (instance instanceof PythonObject && isSubtypeNode.execute(frame, plib.getLazyPythonClass(instance), cls)) {
                return true;
            }

            Object instanceClass = getAttributeNode.executeObject(frame, instance, __CLASS__);
            return PGuards.isManagedClass(instanceClass) && isSubtypeNode.execute(frame, instanceClass, cls);
        }

        @Fallback
        boolean isInstance(VirtualFrame frame, Object cls, Object instance) {
            if (typeErrorProfile.profile(getBasesNode.execute(frame, cls) == null)) {
                throw raise(TypeError, ErrorMessages.ISINSTANCE_ARG_2_MUST_BE_TYPE_OR_TUPLE_OF_TYPE, instance);
            }

            PythonObject instanceClass = getInstanceClassAttr(frame, instance);
            return instanceClass != null && abstractIsSubclassNode.execute(frame, instanceClass, cls);
        }
    }

    @Builtin(name = __SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();
        @Child private TypeNodes.IsSameTypeNode isSameTypeNode = IsSameTypeNodeGen.create();
        @Child private GetFixedAttributeNode getBasesAttrNode;

        @Child private IsBuiltinClassProfile isAttrErrorProfile;
        @Child private IsBuiltinClassProfile isTupleProfile;

        @Specialization(guards = {"!isNativeClass(cls)", "!isNativeClass(derived)"})
        boolean doManagedManaged(VirtualFrame frame, Object cls, Object derived) {
            return isSameType(cls, derived) || isSubtypeNode.execute(frame, derived, cls);
        }

        @Specialization
        boolean doObjectObject(VirtualFrame frame, Object cls, Object derived,
                        @Cached("create()") TypeNodes.IsTypeNode isClsTypeNode,
                        @Cached("create()") TypeNodes.IsTypeNode isDerivedTypeNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib) {
            if (isSameType(cls, derived)) {
                return true;
            }

            // no profiles required because IsTypeNode profiles already
            if (isClsTypeNode.execute(cls) && isDerivedTypeNode.execute(derived)) {
                return isSubtypeNode.execute(frame, derived, cls);
            }
            if (!checkClass(frame, derived, plib)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_D_MUST_BE_S, "issubclass()", 1, "class");
            }
            if (!checkClass(frame, cls, plib)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ISINSTANCE_ARG_2_MUST_BE_TYPE_OR_TUPLE_OF_CLSS_WAS);
            }
            return false;
        }

        // checks if object has '__bases__' (see CPython 'abstract.c' function
        // 'recursive_issubclass')
        private boolean checkClass(VirtualFrame frame, Object obj, PythonObjectLibrary plib) {
            if (getBasesAttrNode == null || isAttrErrorProfile == null || isTupleProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getBasesAttrNode = insert(GetFixedAttributeNode.create(SpecialAttributeNames.__BASES__));
                isAttrErrorProfile = insert(IsBuiltinClassProfile.create());
                isTupleProfile = insert(IsBuiltinClassProfile.create());
            }
            Object basesObj;
            try {
                basesObj = getBasesAttrNode.executeObject(frame, obj);
            } catch (PException e) {
                e.expectAttributeError(isAttrErrorProfile);
                return false;
            }
            return isTupleProfile.profileClass(plib.getLazyPythonClass(basesObj), PythonBuiltinClassType.PTuple);
        }

        protected boolean isSameType(Object a, Object b) {
            return isSameTypeNode.execute(a, b);
        }
    }

    @Builtin(name = __SUBCLASSES__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SubclassesNode extends PythonUnaryBuiltinNode {

        @Specialization
        PList getSubclasses(Object cls,
                        @Cached("create()") GetSubclassesNode getSubclassesNode) {
            // TODO: missing: keep track of subclasses
            return factory().createList(toArray(getSubclassesNode.execute(cls)));
        }

        @TruffleBoundary
        private static <T> Object[] toArray(Set<T> subclasses) {
            return subclasses.toArray();
        }
    }

    @GenerateNodeFactory
    @ImportStatic(NativeMember.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class AbstractSlotNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class NameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static String getNameType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static String getNameBuiltin(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            try {
                String string = castToJavaStringNode.execute(value);
                if (containsNullCharacter(string)) {
                    throw raise(PythonBuiltinClassType.ValueError, "type name must not contain null characters");
                }
                cls.setName(string);
                return PNone.NONE;
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, "can only assign string to %p.__name__, not '%p'", cls, value);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = (String) getTpNameNode.execute(cls, NativeMember.TP_NAME);
            return getQualName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object getModule(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }

        @TruffleBoundary
        private static String getQualName(String fqname) {
            int firstDot = fqname.indexOf('.');
            if (firstDot != -1) {
                return fqname.substring(firstDot + 1);
            }
            return fqname;
        }

    }

    @Builtin(name = __MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class ModuleNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        static Object getModuleType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            String module = cls.getPublicInModule();
            return module == null ? BuiltinNames.BUILTINS : module;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModuleBuiltin(PythonBuiltinClass cls, @SuppressWarnings("unused") PNone value) {
            return getModuleType(cls.getType(), value);
        }

        @Specialization(guards = "isNoValue(value)")
        Object getModule(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") ReadAttributeFromObjectNode readAttrNode) {
            Object module = readAttrNode.execute(cls, __MODULE__);
            if (module == PNone.NO_VALUE) {
                throw raise(AttributeError);
            }
            return module;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModule(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(cls, __MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = (String) getTpNameNode.execute(cls, NativeMember.TP_NAME);
            return getModuleName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonNativeClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }

        @TruffleBoundary
        private static Object getModuleName(String fqname) {
            int firstDotIdx = fqname.indexOf('.');
            if (firstDotIdx != -1) {
                return fqname.substring(0, firstDotIdx);
            }
            return BuiltinNames.BUILTINS;
        }
    }

    @Builtin(name = __QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class QualNameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static String getName(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getQualifiedName();
        }

        @Specialization(guards = "isNoValue(value)")
        static String getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getQualName();
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            try {
                cls.setQualName(castToJavaStringNode.execute(value));
                return PNone.NONE;
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, "can only assign string to %p.__qualname__, not '%p'", cls, value);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static String getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = (String) getTpNameNode.execute(cls, NativeMember.TP_NAME);
            return getQualName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonNativeClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }

        @TruffleBoundary
        private static String getQualName(String fqname) {
            int firstDot = fqname.indexOf('.');
            if (firstDot != -1) {
                return fqname.substring(firstDot + 1);
            }
            return fqname;
        }
    }

    @Builtin(name = __DICTOFFSET__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class DictoffsetNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        Object getDictoffsetType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            return getDictoffsetManaged(getCore().lookupType(cls), value, profile, getName);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getDictoffsetManaged(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                return getName.execute(cls, TYPE_DICTOFFSET);
            }
            return getName.execute(cls, __DICTOFFSET__);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setDictoffsetType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setDictoffsetBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        static Object setDictoffsetClass(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __DICTOFFSET__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMember.TP_DICTOFFSET);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }
    }

    @Builtin(name = __ITEMSIZE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class ItemsizeNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        Object getItemsizeType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            return getItemsizeManaged(getCore().lookupType(cls), value, profile, getName);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getItemsizeManaged(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            Object itemsize;
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                itemsize = getName.execute(cls, TYPE_ITEMSIZE);
            } else {
                itemsize = getName.execute(cls, __ITEMSIZE__);
            }
            return itemsize != PNone.NO_VALUE ? itemsize : 0;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setItemsizeType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setItemsizeBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        static Object setItemsize(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __ITEMSIZE__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMember.TP_ITEMSIZE);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }
    }

    @Builtin(name = __BASICSIZE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class BasicsizeNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        Object getBasicsizeType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            return getBasicsizeManaged(getCore().lookupType(cls), value, profile, getName);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getBasicsizeManaged(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            Object basicsize;
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                basicsize = getName.execute(cls, TYPE_BASICSIZE);
            } else {
                basicsize = getName.execute(cls, __BASICSIZE__);
            }
            return basicsize != PNone.NO_VALUE ? basicsize : 0;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setBasicsizeType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setBasicsizeBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        static Object setBasicsize(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __BASICSIZE__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMember.TP_BASICSIZE);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }
    }

    @Builtin(name = __FLAGS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FlagsNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        Object doGeneric(Object self,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached GetTypeFlagsNode getTypeFlagsNode) {
            if (PGuards.isClass(self, lib)) {
                return getTypeFlagsNode.execute(self);
            }
            throw raise(PythonErrorType.TypeError, "descriptor '__flags__' for 'type' objects doesn't apply to '%p' object", self);
        }
    }
}
