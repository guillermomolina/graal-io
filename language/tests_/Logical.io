/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

main := method(
  t = 10 == 10; // true
  f = 10 != 10; // false
  left(f) && right(f) println
  left(f) && right(t) println
  left(t) && right(f) println
  left(t) && right(t) println
  "" println
  left(f) || right(f) println
  left(f) || right(t) println
  left(t) || right(f) println
  left(t) || right(t) println
}

function left(x) {
  "left" println
  return x;
}

function right(x) {
  "right" println
  return x;
}
