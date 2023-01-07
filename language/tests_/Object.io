/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

main := method(  
  obj1 = new();
  obj1.x = 42;
  obj1.x println
  
  obj2 = new();
  obj2.o = obj1;
  obj2.o.x println
  obj2.o.y = "why";
  obj1.y println
  
  mkobj().z println
  
  obj3 = new();
  obj3.fn = mkobj;
  obj3.fn().z println

  obj4 = new();
  write(obj4, 1);
  read(obj4);
  write(obj4, 2);
  read(obj4);
  write(obj4, "three");
  read(obj4);

  obj5 = new();
  obj5.x println
}

function mkobj() {
  newobj = new();
  newobj.z = "zzz";
  return newobj;
}

function read(obj) {
  return obj.prop;
}

function write(obj, value) {
  return obj.prop = value;
}
