add := method(a, b, a + b)

loop := method(n,
  i := 0  
  sum := 0  
  while (i <= n,
    sum := add(sum, i)  
    i := add(i, 1)
  )
  sum  
)

i := 0;
while (i < 20,
  loop(10000);
  i := i + 1
)
loop(10000) println  
