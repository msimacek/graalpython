# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import struct

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionOutVars, unhandled_error_compare


int_bits = struct.calcsize('i') * 8
max_int = 2 ** (int_bits - 1) - 1
min_int = -2 ** (int_bits - 1)
long_bits = struct.calcsize('l') * 8
max_long = 2 ** (long_bits - 1) - 1
min_long = -2 ** (long_bits - 1)
max_ulong = 2 ** long_bits
ssize_t_bits = struct.calcsize('n') * 8
max_ssize_t = 2 ** (ssize_t_bits - 1) - 1
min_ssize_t = -2 ** (ssize_t_bits - 1)


def _reference_as_index(n):
    if not isinstance(n, int):
        if hasattr(n, '__index__'):
            n = n.__index__()
            assert type(n) is int
        else:
            raise TypeError(f"{type(n)} object cannot be interpreted as an integer")
    return n


def _reference_as_long(args):
    n = _reference_as_index(args[0])
    if n > max_long or n < min_long:
        raise OverflowError("Python int too large to convert to C long")
    return n


def _reference_as_int(args):
    n = _reference_as_index(args[0])
    if n > max_int or n < min_int:
        raise OverflowError("Python int too large to convert to C int")
    return n


def _reference_as_unsigned_long(args):
    n = args[0]
    if not isinstance(n, int):
        raise TypeError("an integer is required")
    if n < 0:
        raise OverflowError("can't convert negative value to unsigned int")
    if n > max_ulong:
        raise OverflowError("Python int too large to convert to C unsigned long")
    return n


def _reference_as_long_and_overflow(args):
    n = _reference_as_index(args[0])
    if n > max_long:
        return -1, 1
    elif n < min_long:
        return -1, -1
    return n, 0


def _reference_as_ssize_t(args):
    n = args[0]
    if not isinstance(n, int):
        raise TypeError("an integer is required")
    if n > max_ssize_t or n < min_ssize_t:
        raise OverflowError("Python int too large to convert to C ssize_t")
    return n


def _reference_fromvoidptr(args):
    n = args[0]
    if n < 0:
        return ((~abs(n)) & 0xffffffffffffffff) + 1
    return n


def _reference_fromlong(args):
    n = args[0]
    return n


def _reference_sign(args):
    n = args[0]
    if n==0:
        return 0
    elif n < 0:
        return -1
    else:
        return 1

def _reference_is_compact(args):
    n = args[0]
    # the range is impl. specific, but let's assume it's at least int32
    return 1 if -2147483648 <= n <= 2147483647 else 0


class DummyNonInt():
    pass


class DummyIndexable:

    def __index__(self):
        return 0xBEEF


def _int_examples():
    return [
        (0,),
        (1,),
        (-1,),
        (-2,),
        (True,),
        (False,),
        (0x7fffffff,),
        (0xffffffff,),
        (-0xffffffff,),
        (0x7fffffffffffffffffffffffffffffff,),
        (0xffffffffffffffffffffffffffffffff,),
        (-0xffffffffffffffffffffffffffffffff,),
        (0xffffffffffffffffffffffffffffffffff,),
        (-0xffffffffffffffffffffffffffffffffff,),
        (0.3,),
        (DummyNonInt(),),
        (DummyIndexable(),),
    ]


class TestPyLong(CPyExtTestCase):

    test_PyLong_AsLong = CPyExtFunction(
        _reference_as_long,
        _int_examples,
        resultspec="l",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test__PyLong_AsInt = CPyExtFunction(
        _reference_as_int,
        _int_examples,
        resultspec="l",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsLongAndOverflow = CPyExtFunctionOutVars(
        _reference_as_long_and_overflow,
        _int_examples,
        resultspec="li",
        argspec='O',
        arguments=["PyObject* obj"],
        resulttype="long",
        resultvars=["int overflow"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsUnsignedLong = CPyExtFunction(
        _reference_as_unsigned_long,
        _int_examples,
        resultspec="k",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsSsize_t = CPyExtFunction(
        _reference_as_ssize_t,
        _int_examples,
        resultspec="n",
        argspec='O',
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromSsize_t = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,),
            (-1,),
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["Py_ssize_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromSize_t = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0,),
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["size_t n"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromDouble = CPyExtFunction(
        lambda args: int(args[0]),
        lambda: (
            (0.0,),
            (-1.0,),
            (-11.123456789123456789,),
        ),
        resultspec="O",
        argspec='d',
        arguments=["double d"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromVoidPtr = CPyExtFunction(
        _reference_fromvoidptr,
        lambda: (
            (0,),
            (-1,),
            (-2,),
            (1,),
            (0xffffffff,),
        ),
        resultspec="O",
        argspec='n',
        arguments=["void* ptr"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromVoidPtrAllocated = CPyExtFunction(
        lambda args: int,
        lambda: ((None,),),
        code="""PyObject* PyLong_FromVoidPtrAllocated(PyObject* none) {
            void* dummyPtr = malloc(sizeof(size_t));
            return (PyObject*)Py_TYPE(PyLong_FromVoidPtr(dummyPtr));
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* none"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_AsVoidPtrAllocated = CPyExtFunction(
        lambda args: True,
        lambda: ((None,),),
        code="""PyObject* PyLong_AsVoidPtrAllocated(PyObject* none) {
            void* dummyPtr = malloc(sizeof(size_t));
            PyObject* obj = PyLong_FromVoidPtr(dummyPtr);
            void* unwrappedPtr = PyLong_AsVoidPtr(obj);
            PyObject* result = unwrappedPtr == dummyPtr ? Py_True : Py_False;
            free(dummyPtr);
            return result;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* none"],
        cmpfunc=unhandled_error_compare
    )

    # We get a pattern like this in Cython generated code
    test_PyLong_FromAndToVoidPtrAllocated = CPyExtFunction(
        lambda args: True,
        lambda: ((None,),),
        code="""PyObject* PyLong_FromAndToVoidPtrAllocated(PyObject* none) {
            void* unwrappedPtr;
            PyObject* result;
            void* dummyPtr = malloc(sizeof(size_t));
            PyObject* obj = PyLong_FromVoidPtr(dummyPtr);
            int r = PyObject_RichCompareBool(obj, Py_False, Py_LT);
            if (r < 0) {
                return Py_None;
            }
            unsigned long long l = PyLong_AsUnsignedLongLong(obj);
            unwrappedPtr = (void*)l;
            result = unwrappedPtr == dummyPtr ? Py_True : Py_False;
            free(dummyPtr);
            return result;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* none"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_Check = CPyExtFunction(
        lambda args: isinstance(args[0], int),
        lambda: (
            (0,),
            (-1,),
            (0xffffffff,),
            (0xfffffffffffffffffffffff,),
            ("hello",),
            (DummyNonInt(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_CheckExact = CPyExtFunction(
        lambda args: type(args[0]) is int,
        lambda: (
            (0,),
            (-1,),
            (0xffffffff,),
            (0xfffffffffffffffffffffff,),
            ("hello",),
            (DummyNonInt(),),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromString = CPyExtFunction(
        lambda args: int(args[0], args[1]),
        lambda: (
            ("00", 0),
            ("03", 0),
            ("  12 ", 10),
            ("  12abg13 ", 22),
            ("12", 0),
            ("12321321", 0),
            ("0x132f1", 0),
            ("0x132132ff213213213231", 0),
            ("13123441234123423412341234123412341234124312341234213213213213213231", 0),
        ),
        code='''PyObject* wrap_PyLong_FromString(const char* str, int base) {
            char* pend;
            return PyLong_FromString(str, &pend, base);
        }''',
        callfunction="wrap_PyLong_FromString",
        resultspec="O",
        argspec="si",
        arguments=["char* string", "int base"],
    )

    test_PyLong_AsByteArray = CPyExtFunction(
        lambda args: args[4],
        lambda: (
            (0, 8, False, True, b'\x00\x00\x00\x00\x00\x00\x00\x00'),
            (4294967299, 8, False, True, b'\x00\x00\x00\x01\x00\x00\x00\x03'),
            (2147483647, 4, False, True, b'\x7f\xff\xff\xff'),
            (-2147483648, 4, False, True, b'\x80\x00\x00\x00'),
            (2147483647, 5, False, True, b'\x00\x7f\xff\xff\xff'),
            (-2147483648, 5, False, True, b'\xff\x80\x00\x00\x00'),
            (9223372036854775807, 8, False, True, b'\x7f\xff\xff\xff\xff\xff\xff\xff'),
            (-9223372036854775808, 8, False, True, b'\x80\x00\x00\x00\x00\x00\x00\x00'),
            (9223372036854775807, 9, False, True, b'\x00\x7f\xff\xff\xff\xff\xff\xff\xff'),
            (-9223372036854775808, 9, False, True, b'\xff\x80\x00\x00\x00\x00\x00\x00\x00'),
            (12, 8, False, True, b'\x00\x00\x00\x00\x00\x00\x00\x0c'),
            (1234, 8, False, True, b'\x00\x00\x00\x00\x00\x00\x04\xd2'),
            (0xdeadbeefdead, 8, False, True, b'\x00\x00\xde\xad\xbe\xef\xde\xad'),
            (0xdeadbeefdead, 8, True, True, b'\xad\xde\xef\xbe\xad\xde\x00\x00'),
            (0xdeadbeefdead, 7, True, True, b'\xad\xde\xef\xbe\xad\xde\x00'),
            (0xdeadbeefdeadbeefbeefdeadcafebabe, 17, False, True, b'\x00\xde\xad\xbe\xef\xde\xad\xbe\xef\xbe\xef\xde\xad\xca\xfe\xba\xbe'),
        ),
        code='''PyObject* wrap_PyLong_AsByteArray(PyObject* object, Py_ssize_t n, int little_endian, int is_signed, PyObject* unused) {
            unsigned char* buf = (unsigned char *) malloc(n * sizeof(unsigned char) + 1);
            memset(buf, 0x33, n + 1);
            PyObject* result;
            Py_INCREF(object);
            if (_PyLong_AsByteArray((PyLongObject*) object, buf, n, little_endian, is_signed)) {
                Py_DECREF(object);
                return NULL;
            }
            Py_DECREF(object);
            if (buf[n] != 0x33) {
                PyErr_SetString(PyExc_SystemError, "Sentinel value corrupted.");
                return NULL;
            }
            result = PyBytes_FromStringAndSize((const char *) buf, n);
            free(buf);
            return result;
        }''',
        callfunction="wrap_PyLong_AsByteArray",
        resultspec="O",
        argspec="OniiO",
        arguments=["PyObject* object", "Py_ssize_t n", "int little_endian", "int is_signed", "PyObject* unused"],
    )

    test_PyUnstable_Long_IsCompact = CPyExtFunction(
        _reference_is_compact,
        # for CPython the range is different, so we test only obvious values
        lambda: ((0,),  (-1,), (1,), (9223372036854775807 * 10,)),
        resultspec="i",
        argspec='O',
        arguments=["PyLongObject* o"],
    )

    test_PyUnstable_Long_CompactValue = CPyExtFunction(
        lambda x: x[0],
        # for CPython the range is different, so we test only obvious values
        lambda: ((0,),  (-1,), (1,)),
        resultspec="l",
        argspec='O',
        arguments=["PyLongObject* o"],
    )

    test__PyLong_Sign = CPyExtFunction(
        _reference_sign,
        lambda: (
            (0,),
            (-1,),
            (0xffffffff,),
            (0xfffffffffffffffffffffff,),
            (True,),
            (False,),
        ),
        resultspec="i",
        argspec='O',
        arguments=["PyObject* o"],
        cmpfunc=unhandled_error_compare
    )

    test_PyLong_FromUnicodeObject = CPyExtFunction(
        lambda args: int(args[0], args[1]),
        lambda: (
            ("10", 10),
            ("-12341451234123512345", 8),
            ("aa", 16),
            ("asdf", 10),
            ("", 10),
            ("0", 0),
        ),
        resultspec="O",
        argspec="Oi",
        arguments=["PyObject* n", "int base"],
        cmpfunc=unhandled_error_compare,
    )

    test__PyLong_FromByteArray = CPyExtFunction(
        lambda args: int.from_bytes(args[0], 'little' if args[1] else 'big', signed=args[2]),
        lambda: (
            (b'', 1, 1),
            (b'\x00\x0009', 0, 1),
            (b'90\x00\x00\x00\x00\x00\x00', 1, 1),
            (b'\xff\xff\xcf\xc7', 0, 1),
            (b'\xff\xff\xff\xff\xff\xff\xff\xab', 0, 0),
            (b'\xab\xff\xff\xff\xff\xff\xff\xff', 1, 0),
            (b'\xff\xff\xff\xff\xff\xff\xff\xff', 0, 1),
            (b'\xff\xff\xff\xff\xff\xff\xff\xff', 1, 1),
        ),
        resultspec="O",
        argspec="y#ii",
        arguments=["const char* bytes", "Py_ssize_t size", "int little_endian", "int is_signed"],
        cmpfunc=unhandled_error_compare,
    )

    test__PyLong_NumBits = CPyExtFunction(
        lambda args: args[0].bit_length(),
        lambda: (
            (1,),
            (1230948701328090743,),
            (-1230948701328090743,),
        ),
        resultspec="n",
        argspec="O",
        arguments=["PyObject* obj"],
        cmpfunc=unhandled_error_compare,
    )
