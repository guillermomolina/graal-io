// http://soft.vub.ac.be/~tvcutsem/talks/presentations/IO-tvcutsem-26-11-04.pdf
x := 5
b := block(v, v + x)
m := method(v, v + x)
/*Test := Object clone do(
    x := 1
    accept := method(f, f(2))
)*/
Test := Object clone
Test x:= 1
Test accept := method(f, f(2))

b(2) println
Test accept(getSlot("b")) println
m(2) println
Test accept(getSlot("m")) println