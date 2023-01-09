isInstance := method(type, value, type == getSlot("value") proto)

null := method(nil)

printTypes := method(type,
  isInstance(type, 42) println
  isInstance(type, 42000000000000000000000000000000000000000) println
  isInstance(type, "42") println
  isInstance(type, true) println
  isInstance(type, Object clone) println
  isInstance(type, getSlot("null")) println
  isInstance(type, null) println
  "" println
)

number := 42 proto
string := "42" proto
boolean := true proto
object := Object clone proto
f := getSlot("null") proto
nilProto := null proto

printTypes(number)
printTypes(string)
printTypes(boolean)
printTypes(object)
printTypes(f)
printTypes(nilProto)
