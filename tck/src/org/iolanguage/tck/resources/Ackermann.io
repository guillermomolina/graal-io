/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function ack(m,n) {
  if (m == 0) {
    return n + 1;
  }
  if (n == 0) {
      n = 1;
  } else {
      n = ack(m, n - 1);
  }
  return ack(m - 1, n);
}

function execute() {
    ack(2,3);
}

main := method(
  return execute;
}
