recursion := method(n,
  local := 42
  
  if (n > 0) {
    recursion(n - 1)
  } else {
    local := "abc"
  }
  
  local println
)

recursion(3)
