recursion := method(n,
  local := 42
  
  if (n > 0,
    recursion(n - 1),
    local := "abc"
  )
  
  local println
)

recursion(3)
