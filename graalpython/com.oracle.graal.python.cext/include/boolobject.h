/* Copyright (c) 2018, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2017 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
/* Boolean object interface */

#ifndef Py_BOOLOBJECT_H
#define Py_BOOLOBJECT_H
#ifdef __cplusplus
extern "C" {
#endif


// PyBool_Type is declared by object.h

#define PyBool_Check(x) Py_IS_TYPE((x), &PyBool_Type)

/* Py_False and Py_True are the only two bools in existence. */

/* Don't use these directly */
PyAPI_DATA(struct _longobject*) _Py_FalseStructReference;
PyAPI_DATA(struct _longobject*) _Py_TrueStructReference;
#define _Py_TrueStruct (*_Py_TrueStructReference)
#define _Py_FalseStruct (*_Py_FalseStructReference)

/* Use these macros */
#define Py_False ((PyObject *) _Py_FalseStructReference)
#define Py_True ((PyObject *) _Py_TrueStructReference)

// Test if an object is the True singleton, the same as "x is True" in Python.
PyAPI_FUNC(int) Py_IsTrue(PyObject *x);
#define Py_IsTrue(x) Py_Is((x), Py_True)

// Test if an object is the False singleton, the same as "x is False" in Python.
PyAPI_FUNC(int) Py_IsFalse(PyObject *x);
#define Py_IsFalse(x) Py_Is((x), Py_False)

/* Macros for returning Py_True or Py_False, respectively */
#define Py_RETURN_TRUE return Py_True
#define Py_RETURN_FALSE return Py_False

/* Function to return a bool from a C long */
PyAPI_FUNC(PyObject *) PyBool_FromLong(long);

#ifdef __cplusplus
}
#endif
#endif /* !Py_BOOLOBJECT_H */
