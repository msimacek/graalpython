# Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#!/usr/bin/env python3
import argparse
import ctypes
import ctypes.util
import math
import platform
import struct
import sys


def bits64(value):
    return struct.unpack(">Q", struct.pack(">d", float(value)))[0]


def bits_binary(value):
    return format(bits64(value), "064b")


def bits_hex(value):
    return f"0x{bits64(value):016x}"


def ulp_ordered_int(value):
    raw = struct.unpack(">q", struct.pack(">d", float(value)))[0]
    if raw < 0:
        raw = ~(raw + 2**63)
    return raw


def ulp_distance(left, right):
    return abs(ulp_ordered_int(left) - ulp_ordered_int(right))


def load_libm():
    candidates = [
        ctypes.util.find_library("m"),
        ctypes.util.find_library("System"),
        "/usr/lib/libm.dylib",
        "/usr/lib/libSystem.B.dylib",
        "libm.so.6",
        "libm.so",
    ]
    seen = set()
    for candidate in candidates:
        if not candidate or candidate in seen:
            continue
        seen.add(candidate)
        try:
            libm = ctypes.CDLL(candidate)
            libm.sin.argtypes = [ctypes.c_double]
            libm.sin.restype = ctypes.c_double
            return candidate, libm
        except OSError:
            continue
    raise RuntimeError(f"could not load libm from candidates: {candidates}")


def print_value(label, value):
    print(f"{label}:")
    print(f"  decimal: {value!r}")
    print(f"  hex bits: {bits_hex(value)}")
    print(f"  bin bits: {bits_binary(value)}")


def print_comparison(label, left_name, left, right_name, right):
    print(f"{label}:")
    print(f"  {left_name}: {left!r} ({bits_hex(left)})")
    print(f"  {right_name}: {right!r} ({bits_hex(right)})")
    print(f"  exact_equal: {left == right}")
    print(f"  abs_diff: {abs(left - right)!r}")
    print(f"  ulp_distance: {ulp_distance(left, right)}")


def exact_flags(libm_sin):
    return [math.sin(float(k)) == libm_sin(float(k)) for k in range(10)]


def tolerant_flags(libm_sin):
    flags = []
    for k in range(10):
        python_value = math.sin(float(k))
        libm_value = libm_sin(float(k))
        abs_tol = max(math.ulp(python_value), math.ulp(libm_value))
        flags.append(math.isclose(python_value, libm_value, rel_tol=0.0, abs_tol=abs_tol))
    return flags


def ulp_distances(libm_sin):
    return [ulp_distance(math.sin(float(k)), libm_sin(float(k))) for k in range(10)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", required=True, choices=["cpython", "graalpy-native", "graalpy-jvm"])
    parser.add_argument("--expect-graalpy-exact-failure", action="store_true")
    parser.add_argument("--require-permissive-success", action="store_true")
    parser.add_argument("--check-java", action="store_true")
    args = parser.parse_args()

    libm_name, libm = load_libm()
    libm_sin = lambda value: libm.sin(float(value))

    print(f"runtime: {args.runtime}")
    print(f"python_executable: {sys.executable}")
    print(f"python_version: {sys.version.splitlines()[0]}")
    print(f"platform: {platform.platform()}")
    print(f"libm: {libm_name}")
    print()

    value = 4.0
    python_sin = math.sin(value)
    libc_sin = libm_sin(value)

    print_value("python math.sin(4.0)", python_sin)
    print_value("libm sin(4.0)", libc_sin)
    print_comparison("python math.sin vs libm sin at 4.0", "math.sin", python_sin, "libm.sin", libc_sin)
    print()

    exact = exact_flags(libm_sin)
    tolerant = tolerant_flags(libm_sin)
    distances = ulp_distances(libm_sin)

    print("cython_equivalent_exact_expression:")
    print("  [math.sin(k) == libm_sin(k) for k in range(10)]")
    print(f"  result: {exact}")
    print(f"  ulp_distances: {distances}")
    print()

    print("proposed_more_permissive_expression:")
    print("  [math.isclose(math.sin(k), libm_sin(k), rel_tol=0.0, abs_tol=max(math.ulp(math.sin(k)), math.ulp(libm_sin(k)))) for k in range(10)]")
    print(f"  result: {tolerant}")
    print()

    if args.check_java:
        from java.lang import Math as JavaMath
        from java.lang import StrictMath

        java_math_sin = float(JavaMath.sin(value))
        java_strict_math_sin = float(StrictMath.sin(value))
        print_value("java.lang.Math.sin(4.0)", java_math_sin)
        print_value("java.lang.StrictMath.sin(4.0)", java_strict_math_sin)
        print_comparison("java.lang.Math vs libm sin at 4.0", "JavaMath.sin", java_math_sin, "libm.sin", libc_sin)
        print_comparison("java.lang.StrictMath vs libm sin at 4.0", "StrictMath.sin", java_strict_math_sin, "libm.sin", libc_sin)
        print_comparison("python math.sin vs java.lang.Math at 4.0", "math.sin", python_sin, "JavaMath.sin", java_math_sin)
        print_comparison("java.lang.Math vs java.lang.StrictMath at 4.0", "JavaMath.sin", java_math_sin, "StrictMath.sin", java_strict_math_sin)
        print()

    if args.expect_graalpy_exact_failure and all(exact):
        raise SystemExit("expected the exact Cython-style comparison to fail on GraalPy, but it passed")

    if args.require_permissive_success and not all(tolerant):
        raise SystemExit("expected the more permissive comparison to pass for all values, but it did not")


if __name__ == "__main__":
    main()
