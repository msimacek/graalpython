/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class PythonObject extends PythonAbstractObject {
    /**
     * Indicates that the object doesn't allow {@code __dict__}, but may have slots
     */
    public static final byte HAS_SLOTS_BUT_NO_DICT_FLAG = 0b1;
    /**
     * Indicates that the shape has some properties that may contain {@link PNone#NO_VALUE} and
     * therefore the shape itself is not enough to resolve any lookups.
     */
    public static final byte HAS_NO_VALUE_PROPERTIES = 0b10;
    /**
     * Indicates that the object has a dict in the form of an actual dictionary
     */
    public static final byte HAS_MATERIALIZED_DICT = 0b100;
    /**
     * Indicates that the object is a static base in the CPython's tp_new_wrapper sense.
     *
     * @see com.oracle.graal.python.nodes.function.builtins.WrapTpNew
     */
    public static final byte IS_STATIC_BASE = 0b1000;

    private Object pythonClass;

    @SuppressWarnings("this-escape") // escapes in the assertion
    public PythonObject(Object pythonClass, Shape instanceShape) {
        super(instanceShape);
        assert pythonClass != null;
        assert !PGuards.isPythonClass(getShape().getDynamicType()) || IsSameTypeNode.executeUncached(getShape().getDynamicType(), pythonClass) : getShape().getDynamicType() + " vs " + pythonClass;
        this.pythonClass = pythonClass;
    }

    public void setDict(Node inliningTarget, HiddenAttr.WriteNode writeNode, PDict dict) {
        writeNode.execute(inliningTarget, this, HiddenAttr.DICT, dict);
    }

    @NeverDefault
    public Object getPythonClass() {
        return pythonClass;
    }

    public void setPythonClass(Object pythonClass) {
        assert getShape().getDynamicType() == PNone.NO_VALUE;
        this.pythonClass = pythonClass;
    }

    @TruffleBoundary
    public final Object getAttribute(TruffleString key) {
        return DynamicObjectLibrary.getUncached().getOrDefault(this, key, PNone.NO_VALUE);
    }

    @TruffleBoundary
    public void setAttribute(TruffleString name, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        DynamicObjectLibrary.getUncached().put(this, name, assertNoJavaString(value));
    }

    @TruffleBoundary
    public List<TruffleString> getAttributeNames() {
        ArrayList<TruffleString> keyList = new ArrayList<>();
        for (Object o : getShape().getKeyList()) {
            if (o instanceof TruffleString && DynamicObjectLibrary.getUncached().getOrDefault(this, o, PNone.NO_VALUE) != PNone.NO_VALUE) {
                keyList.add((TruffleString) o);
            }
        }
        return keyList;
    }

    @Override
    public int compareTo(Object o) {
        return this == o ? 0 : 1;
    }

    /**
     * Important: toString can be called from arbitrary locations, so it cannot do anything that may
     * execute Python code or rely on a context being available.
     *
     * The Python-level string representation functionality always needs to be implemented as
     * __repr__/__str__ builtins (which in turn can call toString if this already implements the
     * correct behavior).
     */
    @Override
    public String toString() {
        String className = "unknown";
        if (pythonClass instanceof PythonManagedClass managedClass) {
            className = managedClass.getQualName().toJavaStringUncached();
        } else if (pythonClass instanceof PythonBuiltinClassType pbct) {
            className = pbct.getName().toJavaStringUncached();
        } else if (PGuards.isNativeClass(pythonClass)) {
            className = "native";
        }
        return "<" + className + " object at 0x" + Integer.toHexString(hashCode()) + ">";
    }

    /* needed for some guards in exported messages of subclasses */
    public static int getCallSiteInlineCacheMaxDepth() {
        return PythonOptions.getCallSiteInlineCacheMaxDepth();
    }
}
