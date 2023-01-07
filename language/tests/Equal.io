e := method(a, b, getSlot("a") == getSlot("b"))

e(4, 4) println  
e(3, "aaa") println  
e(4, 4) println  
e("a", "a") println  
e(1==2, 1==2) println  
e(1==2, 1) println  
e(getSlot("e"), getSlot("e")) println  

nil