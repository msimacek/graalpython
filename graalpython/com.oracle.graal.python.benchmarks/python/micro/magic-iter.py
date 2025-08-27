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

class CustomIterator:
    # Even though this is worse for our generated code, it is increasingly
    # common, because the typing PEP explicitly has an example like this:
    # https://peps.python.org/pep-0526/#class-and-instance-variable-annotations
    #
    # While that is a historical document, it is a) still widely referenced and
    # b) at the time of writing this comment latest spec still has a similar
    # example where a variable is initialized with a default on the class level
    # and the __init__ method only conditionally overrides the default *and
    # otherwise keeps it on the class* making self.FOO accesses polymorphic:
    # https://typing.python.org/en/latest/spec/class-compat.html#classvar
    pos = 0

    def __init__(self, obj):
        self.__obj = obj

    def __iter__(self):
        return self

    def __next__(self):
        res = self.pos
        self.pos += 1
        return self.__obj[res]


class CustomIterable:
    def __init__(self, scale):
        self.__iter = CustomIterator(self)
        self.scale = scale + 1

    def __iter__(self):
        return self.__iter

    def __getitem__(self, i):
        return i * self.scale


def count(num):
    idxObj = CustomIterable(num % 11)
    for t in range(num):
        it = iter(idxObj)
        val0 = next(it)
        val1 = next(it)
        val2 = next(it)

    return (val0, val1, val2)


def measure(num):
    result = count(num)
    return result


def __benchmark__(num=1000000):
    return measure(num)

