loop := method(n,
  obj := Object clone
  obj i := 0
  obj sum := 0
  while (obj i <= n,
    obj sum := obj sum + obj i
    obj i := obj i + 1  
  )
  obj sum
)

i := 0
while(i < 20,
  loop(10000)
  i := i + 1
)
loop(10000) println  
