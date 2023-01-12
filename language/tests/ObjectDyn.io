mkobj := method(
  newobj := Object clone
  newobj z := "zzz"
  newobj
)

read := method(obj, name, obj getSlot(name))

write := method(obj, name, value, obj setSlot(name, value))

obj1 := Object clone
obj1 x := 42
obj1 x println

obj2 := Object clone
obj2 o := obj1
obj2 o x println
obj2 o y := "why"
obj1 y println

mkobj() z println

obj3 := Object clone
obj3 fn := getSlot("mkobj")
obj3 fn z println

obj4 := Object clone
write(obj4, "prop", 1)
read(obj4, "prop")
write(obj4, "prop", 2)
read(obj4, "prop")
write(obj4, "prop", "three")
read(obj4, "prop")

obj5 := Object clone
i := 1
obj5 prop0 := 1
while (i < 10,
  write(obj5, "prop" + i, read(obj5, "prop" + (i - 1)) * 2)
  i := i + 1
)
obj5 prop2 println
obj5 prop9 println

obj6 := Object clone
obj6 x println
