/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.list;

import static com.oracle.graal.python.nodes.SpecialMethodNames.SORT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CreateStorageFromIteratorNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory.ListReverseNodeFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.IndexNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PList)
public class ListBuiltins extends PythonBuiltins {

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        this.builtinConstants.put(__HASH__, PNone.NONE);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ListBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object repr(VirtualFrame frame, PList self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode repr,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItem) {
            StringBuilder result = new StringBuilder();
            sbAppend(result, "[");
            SequenceStorage storage = self.getSequenceStorage();
            boolean initial = true;
            Object value;
            Object reprString;
            for (int index = 0; index < lenNode.execute(storage); index++) {
                value = getItem.execute(frame, storage, index);
                if (self != value) {
                    reprString = repr.executeObject(frame, value);
                    if (reprString instanceof PString) {
                        reprString = ((PString) reprString).getValue();
                    }
                } else {
                    reprString = "[...]";
                }
                if (reprString instanceof String) {
                    if (initial) {
                        initial = false;
                    } else {
                        sbAppend(result, ", ");
                    }
                    sbAppend(result, (String) reprString);
                } else {
                    raise(PythonErrorType.TypeError, ErrorMessages.RETURNED_NON_STRING, "__repr__", reprString);
                }
            }
            return toString(sbAppend(result, "]"));
        }

        @TruffleBoundary(allowInlining = true)
        private static Object toString(StringBuilder sbAppend) {
            return sbAppend.toString();
        }

        @TruffleBoundary(allowInlining = true)
        private static StringBuilder sbAppend(StringBuilder result, String s) {
            return result.append(s);
        }
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ListInitNode extends PythonBinaryBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, PList list, Object source);

        @Specialization
        static PNone init(PList list, String value,
                        @Cached("create()") AppendNode appendNode) {
            clearStorage(list);
            char[] chars = value.toCharArray();
            for (char c : chars) {
                appendNode.execute(list, Character.toString(c));
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)")
        static PNone init(PList list, @SuppressWarnings("unused") PNone none) {
            clearStorage(list);
            return PNone.NONE;
        }

        @Specialization(guards = "range.getIntStep() > 0")
        static PNone listRange(PList list, PIntRange range) {
            clearStorage(list);
            int start = range.getIntStart();
            int stop = range.getIntStop();
            int step = range.getIntStep();
            int len = range.getIntLength();
            int[] ary = new int[len];
            int idx = 0;
            for (int i = start; i < stop; i += step) {
                ary[idx] = i;
                idx++;
            }
            list.setSequenceStorage(new IntSequenceStorage(ary));
            return PNone.NONE;
        }

        @Specialization(guards = "iterable.isPRangeIterator()", rewriteOn = UnexpectedResultException.class, limit = "1")
        static PNone listPGenerator(VirtualFrame frame, PList list, PGenerator iterable,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @CachedLibrary("iterable") PythonObjectLibrary lib,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile errorProfile) throws UnexpectedResultException {
            clearStorage(list);
            Object iterObj = lib.getIteratorWithFrame(iterable, frame);
            SequenceStorage storage = EmptySequenceStorage.INSTANCE;

            PIntRangeIterator range = (PIntRangeIterator) iterable.getIterator();
            final int estimatedMaxLen = range.getLength();
            int realLen = 0;
            if (estimatedMaxLen > 0) {
                Object value = null;
                try {
                    value = getNextNode.execute(frame, iterObj);
                    realLen++;
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                }
                if (value != null) {
                    storage = SequenceStorageFactory.createStorage(value, estimatedMaxLen);
                    storage = appendNode.execute(storage, value, ListGeneralizationNode.SUPPLIER);
                    while (true) {
                        try {
                            storage = appendNode.execute(storage, getNextNode.execute(frame, iterObj), ListGeneralizationNode.SUPPLIER);
                            realLen++;
                        } catch (PException e) {
                            e.expectStopIteration(errorProfile);
                            break;
                        }
                    }
                }
            }

            list.setSequenceStorage(storage);
            if (realLen == estimatedMaxLen) {
                return PNone.NONE;
            } else {
                throw new UnexpectedResultException(PNone.NONE);
            }
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isString(iterable)"}, limit = "getCallSiteInlineCacheMaxDepth()")
        static PNone listIterable(VirtualFrame frame, PList list, Object iterable,
                        @Cached IteratorNodes.GetLength lenNode,
                        @CachedLibrary("iterable") PythonObjectLibrary lib,
                        @Cached CreateStorageFromIteratorNode storageNode) {
            clearStorage(list);
            int len = lenNode.execute(frame, iterable);
            Object iterObj = lib.getIteratorWithFrame(iterable, frame);
            list.setSequenceStorage(storageNode.execute(frame, iterObj, len));
            return PNone.NONE;
        }

        private static void clearStorage(PList list) {
            if (EmptySequenceStorage.INSTANCE != list.getSequenceStorage()) {
                list.setSequenceStorage(EmptySequenceStorage.INSTANCE);
            }
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        protected Object doGeneric(VirtualFrame frame, PList self, Object key,
                        @Cached("create()") SequenceStorageNodes.DeleteNode deleteNode) {
            deleteNode.execute(frame, self.getSequenceStorage(), key);
            return PNone.NONE;
        }

        @Fallback
        protected Object doGeneric(Object self, @SuppressWarnings("unused") Object objectIdx) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__delitem__", "list", self);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "lib.canBeIndex(key) || isPSlice(key)")
        protected Object doScalar(VirtualFrame frame, PList self, Object key,
                        @Cached("createGetItemNode()") SequenceStorageNodes.GetItemNode getItemNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            return getItemNode.execute(frame, self.getSequenceStorage(), key);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!lib.canBeIndex(key)", "!isPSlice(key)"})
        public Object doListError(VirtualFrame frame, PList primary, Object key,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "list", key);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItemNode() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forList(), (s, f) -> f.createList(s));
        }

        protected static GetItemNode create() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__getitem__", "list", self);
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        private final ConditionProfile generalizedProfile = ConditionProfile.createBinaryProfile();

        @Specialization(guards = "lib.canBeIndex(key) || isPSlice(key)")
        public Object doGeneric(VirtualFrame frame, PList primary, Object key, Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            updateStorage(primary, setItemNode.execute(frame, primary.getSequenceStorage(), key, value));
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!lib.canBeIndex(key)", "!isPSlice(key)"})
        public Object doListError(VirtualFrame frame, PList primary, Object key, Object value,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "list", key);
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object objectIdx, Object value) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__setitem__", "list", self);
        }

        private void updateStorage(PList primary, SequenceStorage newStorage) {
            if (this.generalizedProfile.profile(primary.getSequenceStorage() != newStorage)) {
                primary.setSequenceStorage(newStorage);
            }
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forListAssign(), () -> ListGeneralizationNode.create());
        }

        protected static SetItemNode create() {
            return ListBuiltinsFactory.SetItemNodeFactory.create();
        }
    }

    // list.append(x)
    @Builtin(name = "append", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListAppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PNone appendObjectGeneric(PList list, Object arg,
                        @Cached ListNodes.AppendNode appendNode) {
            appendNode.execute(list, arg);
            return PNone.NONE;
        }
    }

    // list.extend(L)
    @Builtin(name = "extend", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListExtendNode extends PythonBinaryBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, PList list, Object source);

        @Specialization
        PNone extendSequence(VirtualFrame frame, PList list, Object iterable,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            int len = lenNode.execute(frame, iterable);
            updateSequenceStorage(list, extendNode.execute(frame, list.getSequenceStorage(), iterable, len));
            return PNone.NONE;
        }

        private static void updateSequenceStorage(PList list, SequenceStorage s) {
            if (list.getSequenceStorage() != s) {
                list.setSequenceStorage(s);
            }
        }

        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(ListGeneralizationNode.SUPPLIER);
        }

        public static ListExtendNode create() {
            return ListBuiltinsFactory.ListExtendNodeFactory.create();
        }
    }

    // list.copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListCopyNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        PList copySequence(PList self,
                        @Cached SequenceStorageNodes.CopyNode copy,
                        @CachedLibrary("self") PythonObjectLibrary plib) {
            return factory().createList(plib.getLazyPythonClass(self), copy.execute(self.getSequenceStorage()));
        }

    }

    // list.insert(i, x)
    @Builtin(name = "insert", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class ListInsertNode extends PythonBuiltinNode {
        protected static final String ERROR_MSG = ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER;

        @Child private SequenceStorageNodes.LenNode lenNode;

        public abstract PNone execute(VirtualFrame frame, PList list, Object index, Object value);

        @Specialization(guards = "isIntStorage(list)")
        PNone insertIntInt(PList list, int index, int value) {
            IntSequenceStorage target = (IntSequenceStorage) list.getSequenceStorage();
            target.insertIntItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        PNone insertLongLong(PList list, int index, int value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isLongStorage(list)")
        PNone insertLongLong(PList list, int index, long value) {
            LongSequenceStorage target = (LongSequenceStorage) list.getSequenceStorage();
            target.insertLongItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isDoubleStorage(list)")
        PNone insertDoubleDouble(PList list, int index, double value) {
            DoubleSequenceStorage target = (DoubleSequenceStorage) list.getSequenceStorage();
            target.insertDoubleItem(normalizeIndex(index, target.length()), value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNotSpecialCase(list, value)")
        PNone insert(PList list, int index, Object value,
                        @Cached SequenceStorageNodes.InsertItemNode insertItem) {
            SequenceStorage store = list.getSequenceStorage();
            list.setSequenceStorage(insertItem.execute(store, normalizeIndex(index, getLength(store)), value));
            return PNone.NONE;
        }

        @Specialization
        PNone insertLongIndex(VirtualFrame frame, PList list, long index, Object value,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            int where = index < Integer.MIN_VALUE ? 0 : index > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) index;
            where = normalizeIndex(where, getLength(list.getSequenceStorage()));
            return insertNode.execute(frame, list, where, value);
        }

        @Specialization
        PNone insertPIntIndex(VirtualFrame frame, PList list, PInt index, Object value,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            int where = normalizePIntForIndex(index);
            where = normalizeIndex(where, getLength(list.getSequenceStorage()));
            return insertNode.execute(frame, list, where, value);
        }

        @Specialization(guards = {"!isIntegerOrPInt(i)"})
        PNone insert(VirtualFrame frame, PList list, Object i, Object value,
                        @Cached("createInteger(ERROR_MSG)") IndexNode indexNode,
                        @Cached("createListInsertNode()") ListInsertNode insertNode) {
            Object indexValue = indexNode.execute(frame, i);
            return insertNode.execute(frame, list, indexValue, value);
        }

        @TruffleBoundary
        private static int normalizePIntForIndex(PInt index) {
            int where = 0;
            BigInteger bigIndex = index.getValue();
            if (bigIndex.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                where = 0;
            } else if (bigIndex.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                where = Integer.MAX_VALUE;
            } else {
                where = bigIndex.intValue();
            }
            return where;
        }

        private static int normalizeIndex(int index, int len) {
            int idx = index;
            if (idx < 0) {
                idx += len;
                if (idx < 0) {
                    idx = 0;
                }
            }
            if (idx > len) {
                idx = len;
            }
            return idx;
        }

        protected boolean isNotSpecialCase(PList list, Object value) {
            return !((PGuards.isIntStorage(list) && value instanceof Integer) || (PGuards.isLongStorage(list) && PGuards.isInteger(value)) ||
                            (PGuards.isDoubleStorage(list) && value instanceof Double));
        }

        protected boolean isIntegerOrPInt(Object index) {
            return index instanceof Integer || index instanceof PInt;
        }

        protected ListInsertNode createListInsertNode() {
            return ListBuiltinsFactory.ListInsertNodeFactory.create(null);
        }

        private int getLength(SequenceStorage s) {
            if (this.lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return this.lenNode.execute(s);
        }

    }

    // list.remove(x)
    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListRemoveNode extends PythonBinaryBuiltinNode {

        private static final String NOT_IN_LIST_MESSAGE = "list.index(x): x not in list";

        @Specialization
        PNone remove(VirtualFrame frame, PList list, Object value,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create()") SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            SequenceStorage listStore = list.getSequenceStorage();
            int len = lenNode.execute(listStore);
            ThreadState threadState = null;
            if (hasFrame.profile(frame != null)) {
                threadState = PArguments.getThreadState(frame);
            }
            for (int i = 0; i < len; i++) {
                Object object = getItemNode.execute(frame, listStore, i);
                final boolean hasItem;
                if (hasFrame.profile(frame != null)) {
                    hasItem = lib.equalsWithState(object, value, lib, threadState);
                } else {
                    hasItem = lib.equals(object, value, lib);
                }
                if (hasItem) {
                    deleteNode.execute(frame, listStore, i);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.ValueError, NOT_IN_LIST_MESSAGE);
        }
    }

    // list.pop([i])
    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListPopNode extends PythonBuiltinNode {

        private static final String POP_INDEX_OUT_OF_RANGE = "pop index out of range";

        @Child private SequenceStorageNodes.GetItemNode getItemNode;

        @Specialization
        public Object popLast(VirtualFrame frame, PList list, @SuppressWarnings("unused") PNone none,
                        @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode) {
            SequenceStorage store = list.getSequenceStorage();
            Object ret = getGetItemNode().execute(frame, store, -1);
            deleteNode.execute(frame, store, -1);
            return ret;
        }

        @Specialization(guards = {"!isNoValue(idx)", "!isPSlice(idx)"})
        public Object doIndex(VirtualFrame frame, PList list, Object idx,
                        @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode) {
            SequenceStorage store = list.getSequenceStorage();
            Object ret = getGetItemNode().execute(frame, store, idx);
            deleteNode.execute(frame, store, idx);
            return ret;
        }

        @Fallback
        public Object doError(@SuppressWarnings("unused") Object list, Object arg) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (this.getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.getItemNode = insert(SequenceStorageNodes.GetItemNode.create(createNormalize()));
            }
            return this.getItemNode;
        }

        protected static SequenceStorageNodes.DeleteNode createDelete() {
            return SequenceStorageNodes.DeleteNode.create(createNormalize());
        }

        private static NormalizeIndexNode createNormalize() {
            return NormalizeIndexNode.create(POP_INDEX_OUT_OF_RANGE);
        }
    }

    // list.index(x)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class ListIndexNode extends PythonBuiltinNode {
        protected static final String ERROR_TYPE_MESSAGE = "slice indices must be integers or have an __index__ method";

        @Child private SequenceStorageNodes.ItemIndexNode itemIndexNode;
        @Child private SequenceStorageNodes.LenNode lenNode;

        public abstract int execute(VirtualFrame frame, Object arg1, Object arg2, Object arg3, Object arg4);

        private int correctIndex(SequenceStorage s, long index) {
            long resultIndex = index;
            if (resultIndex < 0) {
                resultIndex += getLength(s);
                if (resultIndex < 0) {
                    return 0;
                }
            }
            return (int) Math.min(resultIndex, Integer.MAX_VALUE);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private int correctIndex(SequenceStorage s, PInt index) {
            BigInteger value = index.getValue();
            if (value.compareTo(BigInteger.ZERO) < 0) {
                BigInteger resultAdd = value.add(BigInteger.valueOf(getLength(s)));
                if (resultAdd.compareTo(BigInteger.ZERO) < 0) {
                    return 0;
                }
                return resultAdd.intValue();
            }
            return value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        }

        private int findIndex(VirtualFrame frame, SequenceStorage s, Object value, int start, int end) {
            int idx = getItemIndexNode().execute(frame, s, value, start, end);
            if (idx != -1) {
                return idx;
            }
            throw raise(PythonErrorType.ValueError, ErrorMessages.X_NOT_IN_LIST);
        }

        @Specialization
        int index(VirtualFrame frame, PList self, Object value, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, s, value, 0, getLength(s));
        }

        @Specialization
        int index(VirtualFrame frame, PList self, Object value, long start, @SuppressWarnings("unused") PNone end) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, s, value, correctIndex(s, start), getLength(s));
        }

        @Specialization
        int index(VirtualFrame frame, PList self, Object value, long start, long end) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        int indexPI(VirtualFrame frame, PList self, Object value, PInt start, @SuppressWarnings("unused") PNone end) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, s, value, correctIndex(s, start), getLength(s));
        }

        @Specialization
        int indexPIPI(VirtualFrame frame, PList self, Object value, PInt start, PInt end) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        int indexLPI(VirtualFrame frame, PList self, Object value, long start, PInt end) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        int indexPIL(VirtualFrame frame, PList self, Object value, PInt start, Long end) {
            SequenceStorage s = self.getSequenceStorage();
            return findIndex(frame, s, value, correctIndex(s, start), correctIndex(s, end));
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexDO(PTuple self, Object value, double start, Object end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization
        @SuppressWarnings("unused")
        int indexOD(PTuple self, Object value, Object start, double end) {
            throw raise(TypeError, ERROR_TYPE_MESSAGE);
        }

        @Specialization(guards = "!isNumber(start)")
        int indexO(VirtualFrame frame, PTuple self, Object value, Object start, PNone end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(frame, start);
            return indexNode.execute(frame, self, value, startValue, end);
        }

        @Specialization(guards = {"!isNumber(end)"})
        int indexLO(VirtualFrame frame, PTuple self, Object value, long start, Object end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object endValue = endNode.execute(frame, end);
            return indexNode.execute(frame, self, value, start, endValue);
        }

        @Specialization(guards = {"!isNumber(start) || !isNumber(end)"})
        int indexOO(VirtualFrame frame, PTuple self, Object value, Object start, Object end,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode startNode,
                        @Cached("createNumber(ERROR_TYPE_MESSAGE)") IndexNode endNode,
                        @Cached("createIndexNode()") ListIndexNode indexNode) {
            Object startValue = startNode.execute(frame, start);
            Object endValue = endNode.execute(frame, end);
            return indexNode.execute(frame, self, value, startValue, endValue);
        }

        protected ListIndexNode createIndexNode() {
            return ListBuiltinsFactory.ListIndexNodeFactory.create(null);
        }

        private SequenceStorageNodes.ItemIndexNode getItemIndexNode() {
            if (this.itemIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.itemIndexNode = insert(SequenceStorageNodes.ItemIndexNode.create());
            }
            return this.itemIndexNode;
        }

        private int getLength(SequenceStorage s) {
            if (this.lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return this.lenNode.execute(s);
        }

    }

    // list.count(x)
    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ListCountNode extends PythonBuiltinNode {

        @Specialization(limit = "5")
        long count(VirtualFrame frame, PList self, Object value,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("value") PythonObjectLibrary valueLib,
                        @CachedLibrary(limit = "16") PythonObjectLibrary otherLib) {
            long count = 0;
            SequenceStorage s = self.getSequenceStorage();
            for (int i = 0; i < lenNode.execute(s); i++) {
                Object object = getItemNode.execute(frame, s, i);
                if (valueLib.equals(value, object, otherLib)) {
                    count++;
                }
            }
            return count;
        }

    }

    // list.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListClearNode extends PythonBuiltinNode {

        @Specialization
        public PNone clear(PList list) {
            list.setSequenceStorage(EmptySequenceStorage.INSTANCE);
            return PNone.NONE;
        }

    }

    // list.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ListReverseNode extends PythonUnaryBuiltinNode {

        @Specialization
        PList reverse(PList list) {
            list.reverse();
            return list;
        }

        public static ListReverseNode create() {
            return ListReverseNodeFactory.create();
        }
    }

    // list.sort(key=, reverse=)
    @Builtin(name = SORT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, needsFrame = true)
    @GenerateNodeFactory
    public abstract static class ListSortNode extends PythonVarargsBuiltinNode {

        protected static final String SORT = "_sort";
        protected static final String KEY = "key";

        protected static boolean isSortable(PList list, SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(list.getSequenceStorage()) > 1;
        }

        protected static boolean maySideEffect(PList list, PKeyword[] keywords) {
            if (PGuards.isObjectStorage(list)) {
                return true;
            }
            if (keywords.length > 0) {
                if (keywords[0].getName().equals(KEY)) {
                    return true;
                }
                if (keywords.length > 1 && keywords[1].getName().equals(KEY)) {
                    return true;
                }
            }
            return false;
        }

        public final Object sort(VirtualFrame frame, PList list) {
            return this.execute(frame, list, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        }

        public abstract Object execute(VirtualFrame frame, PList list, Object[] arguments, PKeyword[] keywords);

        @Specialization(guards = "!isSortable(list, lenNode)")
        @SuppressWarnings("unused")
        Object none(VirtualFrame frame, PList list, Object[] arguments, PKeyword[] keywords,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            return PNone.NONE;
        }

        @Specialization(guards = {"isSortable(list, lenNode)", "maySideEffect(list, keywords)"})
        Object withKey(VirtualFrame frame, PList list, Object[] arguments, PKeyword[] keywords,
                        @Cached("create(SORT)") GetAttributeNode sort,
                        @Cached CallNode callSort,
                        @SuppressWarnings("unused") @Cached SequenceStorageNodes.LenNode lenNode) {
            list.getSequenceStorage().setLock();
            try {
                defaultSort(frame, list, arguments, keywords, sort, callSort, lenNode);
            } finally {
                list.getSequenceStorage().releaseLock();
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"isSortable(list, lenNode)", "!maySideEffect(list, keywords)"})
        Object defaultSort(VirtualFrame frame, PList list, Object[] arguments, PKeyword[] keywords,
                        @Cached("create(SORT)") GetAttributeNode sort,
                        @Cached CallNode callSort,
                        @SuppressWarnings("unused") @Cached SequenceStorageNodes.LenNode lenNode) {
            Object sortMethod = sort.executeObject(frame, list);
            callSort.execute(sortMethod, arguments, keywords);
            return PNone.NONE;
        }

        public static ListSortNode create() {
            return ListBuiltinsFactory.ListSortNodeFactory.create();
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization
        int doGeneric(PList list,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(list.getSequenceStorage());
        }
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        PList doPList(PList left, PList other,
                        @CachedLibrary("left") PythonObjectLibrary plib,
                        @Cached("createConcat()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage newStore = concatNode.execute(left.getSequenceStorage(), other.getSequenceStorage());
            return factory().createList(plib.getLazyPythonClass(left), newStore);
        }

        @Specialization(guards = "!isList(right)")
        Object doGeneric(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_CONCAT_S_NOT_P_TO_S, "list", right, "list");
        }

        protected static SequenceStorageNodes.ConcatNode createConcat() {
            return SequenceStorageNodes.ConcatNode.create(() -> SequenceStorageNodes.ListGeneralizationNode.create());
        }
    }

    @Builtin(name = __IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        PList extendSequence(VirtualFrame frame, PList list, Object iterable,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            int len = lenNode.execute(frame, iterable);
            updateSequenceStorage(list, extendNode.execute(frame, list.getSequenceStorage(), iterable, len));
            return list;
        }

        private static void updateSequenceStorage(PList list, SequenceStorage s) {
            if (list.getSequenceStorage() != s) {
                list.setSequenceStorage(s);
            }
        }

        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(ListGeneralizationNode.SUPPLIER);
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization
        PList doPListInt(VirtualFrame frame, PList left, Object right,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            try {
                SequenceStorage repeated = repeatNode.execute(frame, left.getSequenceStorage(), right);
                return factory().createList(repeated);
            } catch (ArithmeticException | OutOfMemoryError e) {
                throw raise(MemoryError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __IMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBuiltinNode {

        public abstract PList execute(VirtualFrame frame, PList list, Object value);

        @Specialization
        Object doGeneric(VirtualFrame frame, PList list, Object right,
                        @Cached("createBinaryProfile()") ConditionProfile updatedProfile,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {

            SequenceStorage store = list.getSequenceStorage();
            SequenceStorage updated = repeatNode.execute(frame, store, right);
            if (updatedProfile.profile(store != updated)) {
                list.setSequenceStorage(updated);
            }
            return list;
        }

        protected IMulNode createIMulNode() {
            return ListBuiltinsFactory.IMulNodeFactory.create(null);
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        protected static boolean isObjectStorage(PList left, PList right) {
            return PGuards.isObjectStorage(left) || PGuards.isObjectStorage(right);
        }

        @Specialization(guards = "!isObjectStorage(left, right)")
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        /**
         * This is a fix for the bpo-38588 bug. See
         * {@code test_list.py: ListTest.test_equal_operator_modifying_operand}
         *
         */
        @Specialization(guards = "isObjectStorage(left, right)")
        boolean doPListObjectStorage(VirtualFrame frame, PList left, PList right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode neNode) {
            final SequenceStorage leftStorage = left.getSequenceStorage();
            final SequenceStorage rightStorage = right.getSequenceStorage();
            final boolean result = neNode.execute(frame, leftStorage, rightStorage);
            /**
             * This will check if the underlying storage has been modified and if so, we do the
             * check again.
             */
            if (leftStorage == left.getSequenceStorage() && rightStorage == right.getSequenceStorage()) {
                return result;
            }
            /**
             * To avoid possible infinite recursion case, we call the default specialization.
             */
            return doPList(frame, left, right, neNode);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return !eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createGe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createLe()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createGt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doPList(VirtualFrame frame, PList left, PList right,
                        @Cached("createLt()") SequenceStorageNodes.CmpNode neNode) {
            return neNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }

        @Fallback
        PNotImplemented contains(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, PSequence self, Object other,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, getStorage.execute(self), other);
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"isIntStorage(primary)"})
        PIntegerSequenceIterator doPListInt(PList primary) {
            return factory().createIntegerSequenceIterator((IntSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isLongStorage(primary)"})
        PLongSequenceIterator doPListLong(PList primary) {
            return factory().createLongSequenceIterator((LongSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"isDoubleStorage(primary)"})
        PDoubleSequenceIterator doPListDouble(PList primary) {
            return factory().createDoubleSequenceIterator((DoubleSequenceStorage) primary.getSequenceStorage(), primary);
        }

        @Specialization(guards = {"!isIntStorage(primary)", "!isLongStorage(primary)", "!isDoubleStorage(primary)"})
        PSequenceIterator doPList(PList primary) {
            return factory().createSequenceIterator(primary);
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }
}
