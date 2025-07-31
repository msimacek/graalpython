/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.attributes;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Reads attribute directly from the underlying {@link DynamicObject} regardless of whether the
 * object has dict, also bypasses any other additional logic in {@link ReadAttributeFromObjectNode}.
 */
@GenerateUncached
@GenerateInline(false) // footprint reduction 44 -> 25
public abstract class ReadAttributeFromPythonObjectNode extends PNodeWithContext {
    @NeverDefault
    public static ReadAttributeFromPythonObjectNode create() {
        return ReadAttributeFromPythonObjectNodeGen.create();
    }

    public static ReadAttributeFromPythonObjectNode getUncached() {
        return ReadAttributeFromPythonObjectNodeGen.getUncached();
    }

    public static Object executeUncached(PythonObject object, TruffleString key, Object defaultValue) {
        return getUncached().execute(object, key, defaultValue);
    }

    public abstract Object execute(PythonObject object, TruffleString key, Object defaultValue);

    public final Object execute(PythonObject object, TruffleString key) {
        return execute(object, key, PNone.NO_VALUE);
    }

    // used only by DynamicObjectStorage, which will be removed during the transition from
    // DynamicObject to ObjectHashMap
    public abstract Object execute(DynamicObject object, TruffleString key, Object defaultValue);

    protected static Object getAttribute(DynamicObject object, TruffleString key, Object defaultValue) {
        return DynamicObjectLibrary.getUncached().getOrDefault(object, key, defaultValue);
    }

    @Idempotent
    protected static boolean isLongLivedObject(DynamicObject object) {
        return object instanceof PythonModule || object instanceof PythonManagedClass;
    }

    @Idempotent
    protected static boolean isPrimitive(Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof Boolean || value instanceof Double;
    }

    @NonIdempotent
    protected static boolean locationIsAssumedFinal(Location loc) {
        return loc != null && loc.isAssumedFinal();
    }

    protected static Location getLocationOrNull(Property prop) {
        return prop == null ? null : prop.getLocation();
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "1", //
                    guards = {
                                    "isSingleContext()",
                                    "dynamicObject == cachedObject",
                                    "isLongLivedObject(cachedObject)",
                                    "key == cachedKey",
                                    "dynamicObject.getShape() == cachedShape",
                                    "locationIsAssumedFinal(loc)",
                                    "!isPrimitive(value)"
                    }, //
                    assumptions = {"cachedShape.getValidAssumption()", "loc.getFinalAssumption()"})
    protected static Object readFinalAttr(DynamicObject dynamicObject, TruffleString key, Object defaultValue,
                    @Cached("key") TruffleString cachedKey,
                    @Cached(value = "dynamicObject", weak = true) DynamicObject cachedObject,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("getLocationOrNull(cachedShape.getProperty(cachedKey))") Location loc,
                    @Cached("dynamicObject.getShape().getPropertyAssumption(key)") Assumption propertyAssumption,
                    @Cached(value = "getAttribute(dynamicObject, key, defaultValue)", weak = true) Object value) {
        return value;
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "1", //
                    guards = {
                                    "isSingleContext()",
                                    "dynamicObject == cachedObject",
                                    "isLongLivedObject(cachedObject)",
                                    "key == cachedKey",
                                    "dynamicObject.getShape() == cachedShape",
                                    "locationIsAssumedFinal(loc)",
                                    "isPrimitive(value)"
                    }, //
                    assumptions = {"cachedShape.getValidAssumption()", "loc.getFinalAssumption()"})
    protected static Object readFinalPrimitiveAttr(DynamicObject dynamicObject, TruffleString key, Object defaultValue,
                    @Cached("key") TruffleString cachedKey,
                    @Cached(value = "dynamicObject", weak = true) DynamicObject cachedObject,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("getLocationOrNull(cachedShape.getProperty(cachedKey))") Location loc,
                    @Cached("dynamicObject.getShape().getPropertyAssumption(key)") Assumption propertyAssumption,
                    @Cached(value = "getAttribute(dynamicObject, key, defaultValue)") Object value) {
        return value;
    }

    @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", replaces = {"readFinalAttr", "readFinalPrimitiveAttr"})
    protected static Object readDirect(DynamicObject dynamicObject, TruffleString key, Object defaultValue,
                    @CachedLibrary("dynamicObject") DynamicObjectLibrary dylib) {
        return dylib.getOrDefault(dynamicObject, key, defaultValue);
    }
}
