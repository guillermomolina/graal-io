/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function fib(num) {
  if (num < 1) {return 0;}
  n1 = 0;
  n2 = 1;
  i = 1;
  while (i < num) {
    next = n2 + n1;
    n1 = n2;
    n2 = next;
    i = i + 1;
  }
  return n2;
}

function execute() {
    fib(5);
}

main := method(
  return execute;
}
