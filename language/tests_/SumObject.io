/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

function loop(n) {
  obj = new();
  obj.i = 0;  
  obj.sum = 0;  
  while (obj.i <= n) {  
    obj.sum = obj.sum + obj.i;  
    obj.i = obj.i + 1;  
  )
  return obj.sum;  
)

main := method(
  i = 0;
  while (i < 20) {
    loop(10000);
    i = i + 1;
  }
  loop(10000) println  
)
