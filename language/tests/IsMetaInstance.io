printTypes := method(type,
  42 hasProto(type) println
  42000000000000000000000000000000000000000 hasProto(type) println
  "42" hasProto(type) println
  true hasProto(type) println
  Object clone hasProto(type) println
  method() hasProto(type) println
  nil hasProto(type) println
  list() hasProto(type) println
  "" println
)

number := 42 proto
string := "42" proto
boolean := true proto
object := Object clone proto
function := method() proto
nilProto := nil proto
array := list() proto

printTypes(number)
printTypes(string)
printTypes(boolean)
printTypes(object)
printTypes(getSlot("function"))
printTypes(nilProto)
printTypes(array)
