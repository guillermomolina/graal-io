/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function test(n) {
  a = 1;
  if (a > 0) {
    b = 10;
    b println
  } else {
    b = 20;
    a = 0;
    c = 1;
    if (b > 0) {
      a = -1;
      b = -1;
      c = -1;
      d = -1;
      print(d);
    }
  }
  b println
  a println
}
main := method(
  test(\"n_n\");
}
