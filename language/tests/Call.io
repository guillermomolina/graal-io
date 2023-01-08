ret := method(a, a) 
dub := method(a, a * 2) 
inc := method(a, a + 1) 
dec := method(a, a - 1) 
call := method(f, v, f(v))
 
ret(42) println
dub(21) println
inc(41) println
dec(43) println
call(getSlot("ret"), 42) println
call(getSlot("dub"), 21) println
call(getSlot("inc"), 41) println
call(getSlot("dec"), 43) println
nil
