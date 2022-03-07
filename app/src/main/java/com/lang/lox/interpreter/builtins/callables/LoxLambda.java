package com.lang.lox.interpreter.builtins.callables;

import java.util.List;

import com.lang.lox.interpreter.Environment;
import com.lang.lox.interpreter.Interpreter;
import com.lang.lox.interpreter.builtins.LoxReturn;
import com.lang.lox.syntax.Expr;

public class LoxLambda implements LoxCallable {
    private final Expr.Lambda mDeclaration;
    private final Environment mClosure;
    private final boolean mIsProperty;

    public LoxLambda(final Expr.Lambda declaration, final Environment closure, final boolean isProperty) {
        mDeclaration = declaration;
        mClosure = closure;
        mIsProperty = isProperty;
    }

    public boolean isProperty() {
        return mIsProperty;
    }

    public LoxLambda bind(final Environment enclosingEnvironment) {
        return new LoxLambda(mDeclaration, enclosingEnvironment, mIsProperty);
    }

    @Override
    public int arity() {
        return mDeclaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        var environment = new Environment(mClosure);

        defineParameters(environment, arguments);

        try {
            interpreter.executeBlock(mDeclaration.body, environment);
        } catch (LoxReturn returnValue) {
            return returnValue.value;
        }

        return null;
    }

    private void defineParameters(Environment environment, List<Object> arguments) {
        var totalParams = mDeclaration.params.size();

        for (var i = 0; i < totalParams; ++i) {
            environment.define(arguments.get(i));
        }
    }

    @Override
    public boolean hasDefaultParameters() {
        return false;
    }

    @Override
    public String toString() {
        return !mDeclaration.assignedToVar ? "<lambda>" : "<lambda " + mDeclaration.name.lexeme + ">";
    }

}
