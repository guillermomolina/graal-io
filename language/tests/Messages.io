o1 := Object clone
o1 m := 1

o2 := Object clone
o2 m := method(o1)

o3 := Object clone
o3 m := method(o2)

o3 m m m println

m1 := method("out")
m := method(
    m1 := method("in")
    m1 println
    ?m1 println
    ?m2 println
    a := ?m2
    a println
)
m

