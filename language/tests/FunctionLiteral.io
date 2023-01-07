add := method(a, b, a + b)

sub := method(a, b, a - b)

foo := method(f,
  f(40, 2) println
)

foo(getSlot("add"))
foo(getSlot("sub"))
nil