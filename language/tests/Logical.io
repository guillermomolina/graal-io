

left := method(x,
  "left" println
   x
)

right := method(x,
  "right" println
  x
)

t := 10 == 10 // true
f := 10 != 10 // false
(left(f) && right(f)) println
(left(f) && right(t)) println
(left(t) && right(f)) println
(left(t) && right(t)) println
"" println
(left(f) || right(f)) println
(left(f) || right(t)) println
(left(t) || right(f)) println
(left(t) || right(t)) println
