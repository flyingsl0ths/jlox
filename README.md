# JLox

My implementation of Bob Nystrom's Lox language from https://craftinginterpreters.com/

Differences from the original

- Variable declarations via `let` and not `var`
- Arrays
- `<<<` array insertion operator
- `[1,2,3][0] == 1` array access
- Super class initialization `super(...);`
- Visibility modifiers (based of C++'s)
    - `private`
    - `public`
    - `protected`
- Unary `++`, `--`
- Lambdas (based off Javascript's) `() => print("foo")`
- `continue` & `break` statements
- `++` is used to concatenate collections such as
    - Arrays
    - Strings
- `print` is a function
- Default parameter functions
- ternary operator