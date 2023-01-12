null := method()

foo := method("bar")

f := method(a, b,
  a + " < " + b + ": " + (a < b)
)

("s" + null) println  
("s" + "nil") println  
("s" + foo) println  
("s" + "foo") println
  
(null + "s") println  
("nil" + "s") println  
(foo + "s") println  
("foo" + "s") println

f(2, 4) println
f(2, "4") println
