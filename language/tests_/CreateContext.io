context = java("org.graalvm.polyglot.Context") create()
context.eval("io", "createObject := method(clone)")
context.eval("io", "getPrimitive := method(return 42)")
innerBindings := context getBindings("io")
innerBindings createObject() println
innerBindings getPrimitive() println
context close()

// this is expected fail as
innerBindings getPrimitive()
 