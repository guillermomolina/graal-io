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

m1 := method(
    x := 9
    m2 := method(
        m3 := method(x)
        m3
    )
    m2
)
m1 println

m1 := method(
    m2 := method(
        m3 := method(x)
        m3
    )
    m2
)
m1 println


x := 5
getSlot("x") println
m1 := method(
    y := 6
    getSlot("x") println
    getSlot("y") println
    m2 := method(
        z := 7
        getSlot("x") println
        getSlot("y") println
        getSlot("z") println      
    )
    m2
)
m1

A := Object clone
A x := 10
B := A clone
b := B clone
b getSlot("x") println
