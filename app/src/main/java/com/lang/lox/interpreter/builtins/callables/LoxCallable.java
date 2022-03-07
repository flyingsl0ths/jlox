package com.lang.lox.interpreter.builtins.callables;

import com.lang.lox.interpreter.Interpreter;

import java.util.List;

public interface LoxCallable {
    static int MAX_ARGS = 255;

    int arity();

    Object call(Interpreter interpreter, List<Object> arguments);

    boolean hasDefaultParameters();
}