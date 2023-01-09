m3 := method(
    b := 5
    m1 := method(
        m2 := method(b := 10)
        m2
    )
    m1
    b println
)
m3
