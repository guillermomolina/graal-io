slotNames println
a := 1
slotNames println

m := method(
    slotNames println
    a := 1
    slotNames println
)
m

o := Object clone
o slotNames println
o a := 1
o slotNames println

o m := method(
    slotNames println
    a := 1
    slotNames println
)
o m

o do(
    slotNames println
    a := 1
    slotNames println

    m := method(
        slotNames println
        a := 1
        slotNames println
    )
    m
)