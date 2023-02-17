"0123" foreach(v, v println)

a := Sequence clone setItemType("float32")
a atPut(0, 21.34)
a atPut(1, 2.56)
a atPut(3, 7.35)
a foreach(v, v println)

l := list("mi", 3.56, true)
l foreach(v, v println)
