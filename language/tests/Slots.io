x := 5
getSlot("x") println
m1 := method(
    y := 6
    getSlot("y") println
    getSlot("x") println
    slotNames println
    m2 := method(
        z := 7
        getSlot("z") println      
        getSlot("y") println
        getSlot("x") println
        slotNames println
    )
    m2
)
m1

A := Object clone
A x := 10
B := A clone
b := B clone
b x println
b getSlot("x") println
b getSlot("y") println
b slotNames println
