a := method(42)
b := method(a)
c := method(b)
d := method(c)
e := method(c)
f := method(c)
g := method(d + e + f)

i := 0
result := 0
while (i < 10000,
    i := i + 1
    result := result + g
) println
