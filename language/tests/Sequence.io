o1 := Object clone
o1 m := 1

o2 := Object clone
o2 m := method(o1)

o3 := Object clone
o3 m := method(o2)

o3 m m m println

