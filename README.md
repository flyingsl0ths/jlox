# JLox

My implementation of Bob Nystrom's Lox language from https://craftinginterpreters.com/

## Running

`./gradlew run --args="<filepath>"` or `./gradlew run` (launches repl)

## Differences from the original

- Variable declarations via `let` and not `var`
- Arrays
- `<<<` array insertion operator
- `[1,2,3][0] == 1` array access
- Super class initialization `super(...);`
- Visibility modifiers (based of C++'s)
  - `private`
  - `public`
  - `protected`
- Instances of classes are forbidden to add additional members during runtime for example:
  `foo.foo = "Hello World";` unless `foo` is a `public` member of the class
- Unary `++`, `--`
- Lambdas (based off Javascript's) `() => print("foo")`
- `continue` & `break` statements
- `%` `**` operators
- `*` can be used on collections similar to python
  `"Hello" * 2 == "HelloHello"`, `[1,2,3] * 2 == [1,2,3,1,2,3]`
- `++` is used to concatenate collections such as
  - Arrays
  - Strings
- `print` is a function
- Default parameter functions
- ternary operator
