package com.lang.lox.interpreter;

import com.lang.lox.error.LoxErrorHandler;
import com.lang.lox.resolver.Resolver;
import com.lang.lox.syntax.Stmt;

import java.util.List;

public final class CodeInterpreter {
    final Interpreter mInterpreter = new Interpreter();

    public void resolveVariableScopes(final List<Stmt> syntaxTree, final LoxErrorHandler errorHandler) {
        var resolver = new Resolver(mInterpreter, errorHandler);
        resolver.resolve(syntaxTree);
    }

    public void interpret(final List<Stmt> statements, final LoxErrorHandler loxErrorHandler) {
        mInterpreter.interpret(statements, loxErrorHandler);
    }

    public void printExpressionStatements(final boolean printExpressionStatements) {
        mInterpreter.printExpressionStatements(printExpressionStatements);
    }
}
