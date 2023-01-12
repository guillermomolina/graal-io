mkobj := method(
  newobj := Object clone
  newobj z := "zzz"
  newobj
)

read := method(obj, obj prop)

write := method(obj, value,
  obj prop := value
)

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
obj3 fn := mkobj
obj3 fn() z println

obj4 := Object clone
write(obj4, 1)
read(obj4)
write(obj4, 2)
read(obj4)
write(obj4, "three")
read(obj4)

obj5 := Object clone
obj5 x println
