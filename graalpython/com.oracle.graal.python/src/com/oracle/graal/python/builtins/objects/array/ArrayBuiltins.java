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
package com.oracle.graal.python.builtins.objects.array;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltinsFactory.ArrayNoGeneralizationNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GenNodeSupplier;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GeneralizationNode;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PArray)
public class ArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        PArray doPArray(PArray left, PArray right,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            return factory().createArray(concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage()));
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        PArray mul(VirtualFrame frame, PArray self, Object times,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            return factory().createArray(repeatNode.execute(frame, self.getSequenceStorage(), times));
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(VirtualFrame frame, PArray self, Object other,
                        @Cached("create()") SequenceStorageNodes.ContainsNode containsNode) {
            return containsNode.execute(frame, self.getSequenceStorage(), other);
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean lessThan(VirtualFrame frame, PArray left, PArray right,
                        @Cached("createLt()") SequenceStorageNodes.CmpNode eqNode) {
            return eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean lessThan(VirtualFrame frame, PArray left, PArray right,
                        @Cached("createLe()") SequenceStorageNodes.CmpNode eqNode) {
            return eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean lessThan(VirtualFrame frame, PArray left, PArray right,
                        @Cached("createGt()") SequenceStorageNodes.CmpNode eqNode) {
            return eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean lessThan(VirtualFrame frame, PArray left, PArray right,
                        @Cached("createGe()") SequenceStorageNodes.CmpNode eqNode) {
            return eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean lessThan(VirtualFrame frame, PArray left, PArray right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return !eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        protected abstract boolean executeWith(VirtualFrame frame, Object left, Object right);

        @Specialization
        boolean eq(VirtualFrame frame, PArray left, PArray right,
                        @Cached("createEq()") SequenceStorageNodes.CmpNode eqNode) {
            return eqNode.execute(frame, left.getSequenceStorage(), right.getSequenceStorage());
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {

        @TruffleBoundary
        private static String itoString(int[] values) {
            return Arrays.toString(values);
        }

        @TruffleBoundary
        private static String btoString(byte[] values) {
            return Arrays.toString(values);
        }

        @TruffleBoundary
        private static String dtoString(double[] values) {
            return Arrays.toString(values);
        }

        @TruffleBoundary
        private static String format(String typeName, String typeCode, String array, int storagelen) {
            if (storagelen == 0) {
                return String.format("%s('%s')", typeName, typeCode);
            } else {
                return String.format("%s('%s', %s)", typeName, typeCode, array);
            }
        }

        protected static String getTypeName(PArray self, GetNameNode getName, PythonObjectLibrary lib) {
            return getName.execute(lib.getLazyPythonClass(self));
        }

        @Specialization(guards = "isIntStorage(self)", limit = "2")
        String intstr(PArray self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib,
                        @SuppressWarnings("unused") @Cached GetNameNode getName,
                        @Cached("getTypeName(self, getName, lib)") String typeName) {
            IntSequenceStorage sequenceStorage = (IntSequenceStorage) self.getSequenceStorage();
            return format(typeName, "i", itoString(sequenceStorage.getInternalIntArray()), sequenceStorage.length());
        }

        @Specialization(guards = "isByteStorage(self)", limit = "2")
        String bytestr(PArray self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib,
                        @SuppressWarnings("unused") @Cached GetNameNode getName,
                        @Cached("getTypeName(self, getName, lib)") String typeName) {
            ByteSequenceStorage sequenceStorage = (ByteSequenceStorage) self.getSequenceStorage();
            return format(typeName, "b", btoString(sequenceStorage.getInternalByteArray()), sequenceStorage.length());
        }

        @Specialization(guards = "isDoubleStorage(self)", limit = "2")
        String doublestr(PArray self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib,
                        @SuppressWarnings("unused") @Cached GetNameNode getName,
                        @Cached("getTypeName(self, getName, lib)") String typeName) {
            DoubleSequenceStorage sequenceStorage = (DoubleSequenceStorage) self.getSequenceStorage();
            return format(typeName, "d", dtoString(sequenceStorage.getInternalDoubleArray()), sequenceStorage.length());
        }

        @TruffleBoundary
        @Specialization(replaces = {"intstr", "bytestr", "doublestr"}, limit = "2")
        String str(PArray self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib) {
            // TODO: this needs to be enhanced, but it is slow path and not critical for now
            // mostly cosmetic
            String typeCode = "?";
            String array = "?";
            SequenceStorage sequenceStorage = self.getSequenceStorage();
            if (sequenceStorage instanceof IntSequenceStorage) {
                typeCode = "i";
                array = itoString(((IntSequenceStorage) sequenceStorage).getInternalIntArray());
            } else if (sequenceStorage instanceof ByteSequenceStorage) {
                typeCode = "b";
                array = btoString(((ByteSequenceStorage) sequenceStorage).getInternalByteArray());
            } else if (sequenceStorage instanceof DoubleSequenceStorage) {
                typeCode = "d";
                array = dtoString(((DoubleSequenceStorage) sequenceStorage).getInternalDoubleArray());
            }
            String typeName = TypeNodes.GetNameNode.doSlowPath(lib.getLazyPythonClass(self));
            if (sequenceStorage.length() == 0) {
                return String.format("%s('%s')", typeName, typeCode);
            } else {
                return String.format("%s('%s', %s)", typeName, typeCode, array);
            }
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object getitem(VirtualFrame frame, PArray self, Object idx,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, self.getSequenceStorage(), idx);
        }

        @Fallback
        Object doGeneric(Object self, @SuppressWarnings("unused") Object idx) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__getitem__", "array.array", self);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forArray());
        }
    }

    @Builtin(name = SpecialMethodNames.__SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Specialization
        PNone getitem(VirtualFrame frame, PArray self, Object key, Object value,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self.getSequenceStorage(), key, value);
            return PNone.NONE;
        }

        @Fallback
        Object doGeneric(Object self, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__setitem__", "array.array", self);
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            // TODO correct error message depending on array's element type
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forArrayAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getitem(PArray self) {
            return factory().createArrayIterator(self);
        }
    }

    @Builtin(name = "itemsize", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ItemSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getItemSize(PArray self) {
            if (self.getSequenceStorage().getElementType() == SequenceStorage.ListStorageType.Int) {
                return factory().createInt(4);
            }
            return factory().createInt(2);
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isEmptyStorage(array)")
        public int lenEmpty(@SuppressWarnings("unused") PArray array) {
            return 0;
        }

        @Specialization(guards = "isIntStorage(array)")
        public int lenInt(PArray array) {
            IntSequenceStorage store = (IntSequenceStorage) array.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isByteStorage(array)")
        public int lenByte(PArray array) {
            ByteSequenceStorage store = (ByteSequenceStorage) array.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isLongStorage(array)")
        public int lenLong(PArray array) {
            LongSequenceStorage store = (LongSequenceStorage) array.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isDoubleStorage(array)")
        public int lenDouble(PArray array) {
            DoubleSequenceStorage store = (DoubleSequenceStorage) array.getSequenceStorage();
            return store.length();
        }

        @Specialization(guards = "isBasicStorage(array)")
        public int lenBasicStorage(PArray array) {
            BasicSequenceStorage store = (BasicSequenceStorage) array.getSequenceStorage();
            return store.length();
        }

        @Specialization
        public int len(PArray self) {
            return self.len();
        }
    }

    /**
     * Does not allow any generalization but compatible types.
     */
    @GenerateUncached
    public abstract static class ArrayNoGeneralizationNode extends SequenceStorageNodes.NoGeneralizationNode {

        public static final GenNodeSupplier SUPPLIER = new GenNodeSupplier() {

            public GeneralizationNode create() {
                return ArrayNoGeneralizationNodeGen.create();
            }

            public GeneralizationNode getUncached() {
                return ArrayNoGeneralizationNodeGen.getUncached();
            }
        };

        @Override
        protected String getErrorMessage() {
            return "signed short integer is greater than maximum";
        }
    }

    @Builtin(name = "append", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ArrayAppendNode extends PythonBinaryBuiltinNode {
        @Specialization
        PArray append(PArray array, Object arg,
                        @Cached SequenceStorageNodes.AppendNode appendNode) {
            appendNode.execute(array.getSequenceStorage(), arg, ArrayNoGeneralizationNode.SUPPLIER);
            return array;
        }
    }

}
