foo = method(test(40, 2) println)

defineFunction("test := method(a, b,  return a + b )")
foo();

defineFunction("test := method(a, b,  return a - b )")
foo();
