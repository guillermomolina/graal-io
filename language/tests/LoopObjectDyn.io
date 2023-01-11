loop := method(n, obj, name, 
  obj setSlot(name, 0)
  while (obj getSlot(name) < n, 
    obj setSlot(name, obj getSlot(name) + 1)
  )
  obj getSlot(name)
)

i := 0
while(i < 20,
  loop(1000, Object clone, "prop")
  i := i + 1
)
loop(1000, Object clone, "prop") println  

