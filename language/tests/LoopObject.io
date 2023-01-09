loop := method(n,
  obj := Object clone
  obj i := 0
  while (obj i < n,
    obj i := obj i + 1  
  )
  obj i
)

i := 0
while(i < 20,
  loop(1000)
  i := i + 1
)
loop(1000) println  

