m := method(o,
    o type println
    o proto type println
    o proto proto type println
)

m("")
m(0)
m(0.1)
m(true)
m(nil)
m(list())
m(Sequence clone)

getSlot("m") type println
getSlot("m") proto type println
getSlot("m") proto proto type println
