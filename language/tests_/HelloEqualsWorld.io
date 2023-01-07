doIt := method(a,
  "Initial stack trace:" println
  stacktrace println
  
  hello = 123
  "After 123 assignment:" println
  stacktrace println
  
  helloEqualsWorld()
  "After hello assignment:" println
  stacktrace println
  
//  readln()
)

i = 0
while (i < 10,
  doIt(i)
  i := i + 1
)
