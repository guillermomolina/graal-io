/*
 * Copyright (c) 2022, Guillermo Adrián Molina. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

main := method(  
  obj1 = new();
  obj1["x"] = 42;
  obj1["x"] println
  
  obj2 = new();
  obj2["o"] = obj1;
  obj2["o"]["x"] println
  obj2["o"]["y"] = "why";
  obj1["y"] println
  
  mkobj()["z"] println
  
  obj3 = new();
  obj3["fn"] = mkobj;
  obj3["fn"]()["z"] println

  obj4 = new();
  write(obj4, "prop", 1);
  read(obj4, "prop");
  write(obj4, "prop", 2);
  read(obj4, "prop");
  write(obj4, "prop", "three");
  read(obj4, "prop");
  
  obj5 = new();
  i = 1;
  obj5.prop0 = 1;
  while (i < 10) {
    write(obj5, "prop" + i, read(obj5, "prop" + (i - 1)) * 2);
    i = i + 1;
  }
  obj5.prop2 println
  obj5.prop9 println

  obj6 = new();
  obj6["x"] println
}

function mkobj() {
  newobj = new();
  newobj["z"] = "zzz";
  return newobj;
}

function read(obj, name) {
  return obj[name];
}

function write(obj, name, value) {
  return obj[name] = value;
}
