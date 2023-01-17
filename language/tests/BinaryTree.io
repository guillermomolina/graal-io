Node := Object clone()
Node with := method(a, b, 
    self left := a;
    self right := b;
    self
)

make_tree := method(depth,
    if (depth <= 0, 
        return Node clone with(nil, nil)
    )
    depth := depth - 1
    Node clone with(make_tree(depth), make_tree(depth))
)

check_tree := method(node,
    if (node left isNil, return 1)
    1 + check_tree(node left) + check_tree(node right)
)

min_depth := 4
max_depth := 15
stretch_depth := max_depth + 1

"stretch tree of depth " print 
stretch_depth print
" check: " print
check_tree(make_tree(stretch_depth)) println