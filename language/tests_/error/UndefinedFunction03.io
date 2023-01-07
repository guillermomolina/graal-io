/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function invoke(f) {
  f("hello");
}

function f1() {
  "f1" println
}

function f2() {
  "f2" println
}

function f3() {
  "f3" println
}

function f4() {
  "f4" println
}

function f5() {
  "f5" println
}

main := method(
  invoke(f1);
  invoke(f2);
  invoke(f3);
  invoke(f4);
  invoke(f5);
  invoke(foo);  
)
