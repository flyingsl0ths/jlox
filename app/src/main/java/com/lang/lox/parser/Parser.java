package com.lang.lox.parser;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import com.lang.lox.error.LoxErrorHandler;
import com.lang.lox.scanner.token.Token;
import com.lang.lox.scanner.token.TokenType;
import com.lang.lox.syntax.Expr;
import com.lang.lox.syntax.Stmt;
import com.lang.lox.utils.NameVisibility;
import com.lang.lox.utils.Pair;

import static com.lang.lox.interpreter.builtins.callables.LoxCallable.MAX_ARGS;
import static com.lang.lox.scanner.token.TokenType.*;

public final class Parser {
    private static class ParseError extends RuntimeException {
        public ParseError() {
        }
    }

    private interface BinaryExprProducer {
        Expr operand();

        Token operator();

        Expr create(final Expr leftOperand, final Token operator, final Expr rightOperand);
    }

    // used to issue warning about break statements
    private int mLoopDepth = 0;

    private int mCurrentToken = 0;
    private final LoxErrorHandler mErrorHandler;
    private final List<Token> mTokens;

    public Parser(final List<Token> tokens, final LoxErrorHandler errorHandler) {
        mTokens = tokens;
        mErrorHandler = errorHandler;
    }

    public List<Stmt> parse() {
        final var statements = new ArrayList<Stmt>();

        while (!isAtEnd()) {
            statements.add(declarations());
        }

        return statements;
    }

    private Stmt declarations() {
        try {

            if (match(FUN)) {
                return function("function", NameVisibility.NONE);
            }

            if (match(CLASS)) {
                return classDeclaration();
            }

            if (match(LET)) {
                return variableDeclaration(NameVisibility.NONE);
            }

            return statement(false);
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt.Class classDeclaration() {
        final var name = consume(IDENTIFIER, "Expected class name");

        final List<Stmt.Let> fields = new ArrayList<>();
        final List<Stmt.Let> classFields = new ArrayList<>();
        final List<Stmt.Function> methods = new ArrayList<>();
        final List<Stmt.Function> classMethods = new ArrayList<>();
        NameVisibility currentVisibility = NameVisibility.PRIVATE;
        Expr.Variable superclass = null;

        if (match(LESS)) {
            consume(IDENTIFIER, "Expected superclass name");
            superclass = new Expr.Variable(previous());
        }

        consume(LEFT_BRACE, "Expected '{' before class body");

        final Function<NameVisibility, Void> parseStaticMembers = visibility -> {
            if (match(LET)) {
                classFields.add(variableDeclaration(visibility));
            } else if (check(IDENTIFIER)) {
                classMethods.add(function("static method", visibility));
            }

            return null;
        };

        final Function<Token, NameVisibility> visibilityOf = member -> {
            switch (member.type) {
                case PUBLIC:
                    return NameVisibility.PUBLIC;

                case PRIVATE:
                    return NameVisibility.PRIVATE;

                case PROTECTED:
                    return NameVisibility.PROTECTED;
            }

            return null;
        };

        final Function<Token, NameVisibility> parseAccessModifier = modifierToken -> {
            final var visibility = visibilityOf.apply(modifierToken);

            if (visibility == null) {
                mErrorHandler.error(modifierToken, "Unknown access modifier");
            }

            consume(COLON, "Missing ':' after access modifier");

            return visibility;
        };

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            if (match(PUBLIC, PRIVATE, PROTECTED)) {
                currentVisibility = parseAccessModifier.apply(previous());
            }

            if (match(STATIC)) {
                parseStaticMembers.apply(currentVisibility);
            } else if (match(LET)) {
                fields.add(variableDeclaration(currentVisibility));
            } else {
                methods.add(function("method", currentVisibility));
            }
        }

        consume(RIGHT_BRACE, "Expected '}' after class body");

        return new Stmt.Class(name, superclass, fields, classFields, methods, classMethods);
    }

    private Stmt.Function function(final String kind, final NameVisibility visibility) {
        var name = consume(IDENTIFIER, "Expected " + kind + " name.");

        consume(LEFT_PAREN, "Expected '(' after " + kind + " name.");

        var parameters = new ArrayList<Pair<Token, Expr>>();

        var fun = new Stmt.Function(null, null, null, visibility);

        final Runnable parseFunctionParameters = () -> {
            parseParameters(parameters);

            if (!parameters.isEmpty() && parameters.get(0).second != null) {
                fun.hasDefaultParameters = true;
            }
        };

        if (!check(RIGHT_PAREN)) {
            parseFunctionParameters.run();
        }

        consume(RIGHT_PAREN, "Expected closing ')' after parameters.");

        consume(LEFT_BRACE, "Expected '{' before " + kind + " body.");

        fun.name = name;
        fun.params = parameters;
        fun.body = block();

        return fun;
    }

    private void parseParameters(
            final ArrayList<Pair<Token, Expr>> parameters) {
        var delimiter = new TokenType[]{COMMA};

        final Runnable buildParameterList = () -> {
            if (parameters.size() >= MAX_ARGS) {
                mErrorHandler.error(peek(), "Maximum parameter length exceeded.");
            }

            var identifier = consume(IDENTIFIER, "Expected parameter name.");

            Expr defaultValue;

            defaultValue = parseDefaultParameters();

            parameters.add(new Pair<>(identifier, defaultValue));
        };

        do {
            buildParameterList.run();
        } while (match(delimiter));
    }

    private Expr parseDefaultParameters() {
        if (!match(EQUAL)) {
            return null;
        }

        var assignmentToken = previous();

        if (match(NIL)) {
            mErrorHandler.error(assignmentToken, "Default parameter cannot be assigned to 'nil'");
        } else {
            return assignment();
        }

        return null;
    }

    private Stmt.Let variableDeclaration(NameVisibility visibility) {
        final var name = consume(IDENTIFIER, "Expected a variable name");

        Expr initializer = null;

        if (match(EQUAL)) {
            initializer = expression();

            if (initializer instanceof Expr.Lambda) {
                final var lambda = ((Expr.Lambda) initializer);
                lambda.name = name;
                lambda.assignedToVar = true;
            }
        }

        checkForMissingSemicolon();

        return new Stmt.Let(name, initializer, visibility);
    }

    private Stmt statement(boolean isParsingLambda) {
        if (match(FOR)) {
            return forStatement(isParsingLambda);
        }

        if (match(IF)) {
            return ifStatement(isParsingLambda);
        }

        if (match(RETURN)) {
            return returnStatement(isParsingLambda);
        }

        if (match(BREAK)) {
            if (isParsingLambda) {
                throw error(previous(), "\"break\" can only be used within a loop");
            }

            return breakStatement();
        }

        if (match(CONTINUE)) {
            if (isParsingLambda) {
                throw error(previous(), "\"continue\" can only be used within a loop");
            }

            return continueStatement();
        }

        if (match(ASSERT)) {
            return assertStatement(isParsingLambda);
        }

        if (match(WHILE)) {
            return whileStatement(isParsingLambda);
        }

        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }

        return expressionStatement(isParsingLambda);
    }

    private Stmt returnStatement(boolean isParsingLambda) {
        var keyword = previous();

        Expr value = null;

        if (!check(SEMICOLON)) {
            value = isParsingLambda ? assignment() : expression();
        }

        if (!isParsingLambda) {
            consume(SEMICOLON, "Expected ';' after return value");
        }

        return new Stmt.Return(keyword, value);
    }

    private Stmt breakStatement() {
        if (mLoopDepth == 0) {
            mErrorHandler.error(previous(), "\"break\" can only be used within a loop");
        }

        consume(SEMICOLON, "Expected ';' after \"break\".");
        return new Stmt.Break();
    }

    private Stmt continueStatement() {
        if (mLoopDepth == 0) {
            mErrorHandler.error(previous(), "\"continue\" can only be used within a loop");
        }

        consume(SEMICOLON, "Expected ';' after \"continue\".");
        return new Stmt.Continue();
    }

    private Stmt forStatement(boolean isParsingLambda) {
        consume(LEFT_PAREN, "Expected opening '(' after \"for\".");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(LET)) {
            initializer = variableDeclaration(NameVisibility.NONE);
        } else {
            initializer = expressionStatement(isParsingLambda);
        }

        final Supplier<Stmt> buildForLoop = () -> {
            var condition = !check(SEMICOLON) ? (isParsingLambda ? assignment() : expression()) : null;
            consume(SEMICOLON, "Expected ';' after loop condition.");

            final var increment = !check(RIGHT_PAREN) ? (isParsingLambda ? assignment() : expression())
                    : null;
            consume(RIGHT_PAREN, "Expected closing ')' after for clauses.");

            ++mLoopDepth;

            var body = statement(isParsingLambda);

            Stmt.Expression incrementStmt = null;
            if (increment != null) {
                if (body instanceof Stmt.Block) {
                    ((Stmt.Block) body).statements
                            .add(incrementStmt = new Stmt.Expression(increment));
                } else {
                    body = new Stmt.Block(Arrays.asList(body,
                            incrementStmt = new Stmt.Expression(increment)));
                }
            }

            if (condition == null) {
                condition = new Expr.Literal(true);
            }

            body = new Stmt.While(condition, body, Optional.ofNullable(incrementStmt));

            if (initializer != null) {
                body = new Stmt.Block(Arrays.asList(initializer, body));
            }

            return body;

        };

        try {
            return buildForLoop.get();
        } finally {
            --mLoopDepth;
        }
    }

    private Stmt whileStatement(final boolean isParsingLambda) {
        consume(LEFT_PAREN, "Expected '(' after \"while\"");
        final var condition = isParsingLambda ? assignment() : expression();
        consume(RIGHT_PAREN, "Expected ')' after condition");

        try {
            ++mLoopDepth;

            final var body = statement(isParsingLambda);

            return new Stmt.While(condition, body, null);
        } finally {
            --mLoopDepth;
        }
    }

    private Stmt ifStatement(final boolean isParsingLambda) {
        consume(LEFT_PAREN, "Expected '(' after \"if\"");
        final var condition = isParsingLambda ? assignment() : expression();
        consume(RIGHT_PAREN, "Expected ')' after \"if\" condition");

        final var thenBranch = statement(isParsingLambda);

        return new Stmt.If(condition, thenBranch, (match(ELSE) ? statement(isParsingLambda) : null));
    }

    private List<Stmt> block() {
        final var statements = new ArrayList<Stmt>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {

            statements.add(declarations());
        }

        consume(RIGHT_BRACE, "Expected '}' after block");
        return statements;
    }

    private Stmt expressionStatement(final boolean isParsingLambda) {
        final var expr = isParsingLambda ? assignment() : expression();

        if (!isParsingLambda) {
            consume(SEMICOLON, "';' expected after expression");
        }

        return new Stmt.Expression(expr);
    }

    private Stmt assertStatement(final boolean isParsingLambda) {
        final var hasParens = match(LEFT_PAREN);

        final var expr = assignment();
        final var exprToken = previous();

        final var hasMessage = match(COMMA) && match(STRING);

        final Supplier<Stmt> assertWithMessage = () -> {
            var message = previous();

            final var assertionMessage = new Token(message.type, "Assertion failed: " + message.lexeme,
                    null,
                    message.line);

            consume(RIGHT_PAREN, "Expected closing ')'");

            consume(SEMICOLON, "Expected ';' after \"assert\" statement");

            return new Stmt.Assert(expr, assertionMessage);
        };

        if (hasParens && hasMessage) {
            return assertWithMessage.get();
        }

        if (!isParsingLambda) {
            consume(SEMICOLON, "Expected ';' after \"assert\" statement");
        }

        final var defaultErrorMessage = new Token(ASSERT, "Assertion failed: " + exprToken.lexeme, null,
                (!isParsingLambda ? previous().line : peek().line));

        return new Stmt.Assert(expr, defaultErrorMessage);
    }

    private Expr expression() {
        return comma();
    }

    private Expr comma() {
        final var commaExpr = new Parser.BinaryExprProducer() {
            @Override
            public Expr operand() {
                return assignment();
            }

            @Override
            public Token operator() {
                return previous();
            }

            @Override
            public Expr create(Expr leftOperand, Token operator, Expr rightOperand) {
                return new Expr.Binary(leftOperand, operator, rightOperand);
            }
        };

        return buildBinaryExpr(new TokenType[]{COMMA}, commaExpr);
    }

    private Expr assignment() {
        final var expr = conditional();

        final BiFunction<Token, Expr, Expr.Assign> buildLetAssignmentExpr = (equalsToken, value) -> {
            final var variable = (Expr.Variable) expr;

            return new Expr.Assign(variable.name, value);
        };

        final BiFunction<Token, Expr, Expr> buildSetExpr = (equalsToken, value) -> {
            final var get = (Expr.Get) expr;

            return new Expr.Set(get.object, get.name, value);
        };

        final Supplier<Expr> initAssignment = () -> {
            final var equals = previous();

            final var value = assignment();

            if (expr instanceof Expr.Variable) {
                return buildLetAssignmentExpr.apply(equals, value);
            } else if (expr instanceof Expr.Get) {
                return buildSetExpr.apply(equals, value);
            }

            mErrorHandler.error(equals, "Invalid assignment target.");

            return null;
        };

        if (match(EQUAL)) {
            return initAssignment.get();
        }

        return expr;
    }

    private Expr conditional() {
        final var condition = or();

        if (match(CONDITIONAL)) {
            final var thenBranch = expression();

            consume(COLON, "Expected ':' after \"then\" branch of conditional expression");

            final var elseBranch = conditional();

            return new Expr.Conditional(condition, thenBranch, elseBranch);
        }

        return condition;
    }

    private Expr or() {
        var expr = and();

        while (match(OR)) {
            final var operator = previous();
            final var right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        var expr = equality();

        while (match(AND)) {
            final var operator = previous();
            final var right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        final var equalityExpr = new Parser.BinaryExprProducer() {
            @Override
            public Expr operand() {
                return comparison();
            }

            @Override
            public Token operator() {
                return previous();
            }

            @Override
            public Expr create(Expr leftOperand, Token operator, Expr rightOperand) {
                return new Expr.Binary(leftOperand, operator, rightOperand);
            }

        };

        return buildBinaryExpr(new TokenType[]{BANG_EQUAL, EQUAL_EQUAL}, equalityExpr);
    }

    private Expr comparison() {
        final var comparisonExpr = new BinaryExprProducer() {
            @Override
            public Expr operand() {
                return concatenation();
            }

            @Override
            public Token operator() {
                return previous();
            }

            @Override
            public Expr create(Expr leftOperand, Token operator, Expr rightOperand) {
                return new Expr.Binary(leftOperand, operator, rightOperand);
            }

        };

        return buildBinaryExpr(new TokenType[]{GREATER, LESS, GREATER_EQUAL, LESS_EQUAL}, comparisonExpr);
    }

    private Expr concatenation() {
        final var termExpr = new Parser.BinaryExprProducer() {
            @Override
            public Expr operand() {
                return insertion();
            }

            @Override
            public Token operator() {
                return previous();
            }

            @Override
            public Expr create(Expr leftOperand, Token operator, Expr rightOperand) {
                return new Expr.Binary(leftOperand, operator, rightOperand);
            }
        };

        return buildBinaryExpr(new TokenType[]{PLUS_PLUS}, termExpr);
    }

    private Expr insertion() {
        final var termExpr = new Parser.BinaryExprProducer() {
            @Override
            public Expr operand() {
                return term();
            }

            @Override
            public Token operator() {
                return previous();
            }

            @Override
            public Expr create(Expr leftOperand, Token operator, Expr rightOperand) {
                return new Expr.Binary(leftOperand, operator, rightOperand);
            }
        };

        return buildBinaryExpr(new TokenType[]{INSERTION}, termExpr);
    }

    private Expr term() {
        final var termExpr = new Parser.BinaryExprProducer() {
            @Override
            public Expr operand() {
                return factor();
            }

            @Override
            public Token operator() {
                return previous();
            }

            @Override
            public Expr create(Expr leftOperand, Token operator, Expr rightOperand) {
                return new Expr.Binary(leftOperand, operator, rightOperand);
            }
        };

        return buildBinaryExpr(new TokenType[]{PLUS, MINUS, PLUS_PLUS}, termExpr);
    }

    private Expr factor() {
        final var termExpr = new Parser.BinaryExprProducer() {
            @Override
            public Expr operand() {
                return unary();
            }

            @Override
            public Token operator() {
                return previous();
            }

            @Override
            public Expr create(Expr leftOperand, Token operator, Expr rightOperand) {
                return new Expr.Binary(leftOperand, operator, rightOperand);
            }
        };

        return buildBinaryExpr(new TokenType[]{SLASH, STAR, MOD, EXPONENT}, termExpr);

    }

    private Expr unary() {
        if (match(BANG, PLUS, MINUS)) {
            return new Expr.Unary(previous(), unary());
        }

        return prefix();
    }

    private Expr prefix() {
        final var currentTokenType = peek().type;
        final var isPrefixExpr = (currentTokenType == PLUS_PLUS
                || currentTokenType == MINUS_MINUS) && peekNext().type == IDENTIFIER;

        if (isPrefixExpr) {
            final var operator = advance();
            final var identifier = advance();
            return new Expr.Prefix(operator, new Expr.Variable(identifier));
        }

        return postfix();
    }

    private Expr postfix() {
        final var nextTokenType = peekNext().type;

        final var nextNextTokenType = peekNextNext().type;

        final var isNextNextTokenConcatenationOperand = nextNextTokenType == IDENTIFIER
                || nextNextTokenType == STRING
                || nextNextTokenType == LEFT_BRACKET || nextNextTokenType == LEFT_PAREN;

        final var isPostFixExpr = peek().type == IDENTIFIER &&
                (nextTokenType == PLUS_PLUS || nextTokenType == MINUS_MINUS)
                && !isNextNextTokenConcatenationOperand;

        if (isPostFixExpr) {
            final var identifier = advance();
            final var operator = advance();
            return new Expr.Postfix(new Expr.Variable(identifier), operator);
        }

        return call();
    }

    private Expr finishSubscript(Expr callee) {
        Expr index;

        final BooleanSupplier isNumberLiteral = () -> match(NUMBER);

        if (!check(RIGHT_BRACKET)) {
            index = isNumberLiteral.getAsBoolean() ? new Expr.Literal(previous().literal) : expression();
        } else {
            throw error(peek(), "Expected expression");
        }

        var bracket = consume(RIGHT_BRACKET, "Expected closing ']' after expression");

        return new Expr.Subscript(callee, bracket, index);
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(LEFT_BRACKET)) {
                expr = finishSubscript(expr);
            } else if (match(DOT)) {
                expr = finishDotExpr(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishDotExpr(final Expr expr) {
        final var name = consume(IDENTIFIER, "Expected property name after '.'.");
        return new Expr.Get(expr, name);
    }

    private Expr finishCall(Expr callee) {
        var arguments = new ArrayList<Expr>();

        final Runnable addArgs = () -> {
            if (arguments.size() >= MAX_ARGS) {
                mErrorHandler.error(peek(), "Maximum argument count exceeded");
            }

            var expr = assignment();

            arguments.add(expr);
        };

        final Runnable parseArgs = () -> {
            do {
                addArgs.run();
            } while (match(COMMA));
        };

        if (!check(RIGHT_PAREN)) {
            parseArgs.run();
        }

        var paren = consume(RIGHT_PAREN, "Expected closing ')' after arguments");

        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }

        if (match(TRUE)) {
            return new Expr.Literal(true);
        }

        if (match(NIL)) {
            return new Expr.Literal(null);
        }

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            return (isAtStartOfLambda()
                    ? parseLambda()
                    : parseGrouping());
        }

        if (match(LEFT_BRACKET)) {
            return array();
        }

        if (match(THIS)) {
            return new Expr.This(previous());
        }

        final Supplier<Expr.Super> parseSuperExpr = () -> {
            final var keyword = previous();

            if (match(DOT)) {
                final var member = consume(IDENTIFIER, "Expected superclass member name");
                return new Expr.Super(keyword, member);
            }

            return new Expr.Super(keyword, null);
        };

        if (match(SUPER)) {
            return parseSuperExpr.get();
        }

        if (match(IDENTIFIER)) {
            var identifier = previous();
            return (match(ARROW) ? parseSingleParamLambda(identifier) : new Expr.Variable(identifier));
        }

        if (handleUnaryOperatorError()) {
            return null;
        }

        if (handleBinaryOperatorError()) {
            return null;
        }

        throw error(peek(), "expected expression");
    }

    private boolean isAtStartOfLambda() {
        var currentToken = peek();

        var nextToken = peekNext();

        BooleanSupplier findArrowToken = () -> {
            var currentTokenIndex = mCurrentToken + 2;
            var currentTokenType = mTokens.get(currentTokenIndex).type;

            while (!isAtEnd()
                    && currentTokenType != SEMICOLON) {
                if (mTokens.get(currentTokenIndex).type == TokenType.ARROW) {
                    return true;
                }

                currentTokenType = mTokens.get(++currentTokenIndex).type;
            }

            return false;
        };

        return (currentToken.type == RIGHT_PAREN
                && nextToken.type == ARROW)
                || (currentToken.type == IDENTIFIER
                && findArrowToken.getAsBoolean());
    }

    private Expr.Lambda parseSingleParamLambda(final Token param) {
        return parseLambdaBody(param != null ? List.of(param) : new ArrayList<>());
    }

    private Expr.Lambda parseLambda() {
        final var parameters = new ArrayList<Token>();

        final var delimiter = new TokenType[]{COMMA};

        final Runnable buildParameterList = () -> {
            if (parameters.size() >= MAX_ARGS) {
                mErrorHandler.error(peek(), "Maximum parameter length exceeded.");
            }

            var identifier = consume(IDENTIFIER, "Expected parameter name.");

            parameters.add(identifier);
        };

        if (!check(RIGHT_PAREN)) {
            do {
                buildParameterList.run();
            } while (match(delimiter));
        }

        consume(RIGHT_PAREN, "Expected closing ')' after parameter list.");

        consume(ARROW, "Expected \"->\" after parameter list.");

        return parseLambdaBody(parameters);
    }

    private Expr.Lambda parseLambdaBody(final List<Token> parameters) {
        final var body = toStatementList(statement(true));

        final Supplier<Token> generateLambdaId = () -> {
            final var lineNumber = peek().line;
            return new Token(IDENTIFIER, "lambda", null, lineNumber);
        };

        return new Expr.Lambda(generateLambdaId.get(), parameters, body);
    }

    private List<Stmt> toStatementList(final Stmt statement) {
        Supplier<List<Stmt>> exprToBlock = () -> {
            final var expr = (Stmt.Expression) statement;

            final var returnStmt = new Stmt.Return(new Token(RETURN, "return", null, peek().line),
                    expr.expression);

            return List.of(returnStmt);
        };

        if (statement instanceof Stmt.Block) {
            return ((Stmt.Block) statement).statements;
        } else if (statement instanceof Stmt.Expression) {
            return exprToBlock.get();
        }

        return List.of(statement);
    }

    private Expr parseGrouping() {
        final var expr = expression();

        consume(RIGHT_PAREN, "Expected ')' after expression.");

        return new Expr.Grouping(expr);
    }

    private Expr array() {
        final List<Expr> exprList = new ArrayList<>();

        if (match(RIGHT_BRACKET)) {
            return new Expr.Array(exprList);
        }

        do {
            var expr = assignment();
            exprList.add(expr);
        } while (match(COMMA));

        consume(RIGHT_BRACKET, "Missing right bracket in array expression");

        return new Expr.Array(exprList);
    }

    private boolean handleUnaryOperatorError() {
        if (match(BANG_EQUAL, EQUAL)) {
            return handleEqualityError();
        }

        return false;
    }

    private boolean handleEqualityError() {
        mErrorHandler.error(previous(), "Missing left-hand operand in equality expression.");
        equality();
        return true;
    }

    private boolean handleBinaryOperatorError() {
        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            mErrorHandler.error(previous(), "Missing left-hand operand in comparison expression.");
            comparison();
            return true;
        }

        if (match(EXPONENT, SLASH, MOD, STAR)) {
            mErrorHandler.error(previous(), "Missing left-hand operand in arithmetic expression.");
            factor();
            return true;
        }

        if (match(PLUS_PLUS)) {
            return handlePlusPlusOperatorError();
        }

        return false;
    }

    private boolean handlePlusPlusOperatorError() {
        final var operator = previous();
        final var currentToken = peek();

        if (currentToken.type == IDENTIFIER || currentToken.type == STRING
                || currentToken.type == LEFT_BRACKET) {
            mErrorHandler.error(operator, "Missing left-hand operand in concatenation expression.");
            concatenation();
            return true;
        }

        mErrorHandler.error(operator,
                String.format(Locale.getDefault(), "'%s' is not an identifier", currentToken.lexeme));
        prefix();
        return true;
    }

    private Token consume(final TokenType tokenType, final String message) {
        if (check(tokenType)) {
            return advance();
        }

        throw error(peek(), message);
    }

    private void checkForMissingSemicolon() {
        if (check(TokenType.SEMICOLON)) {
            advance();
            return;
        }

        throw error(previous(), "';' expected after variable declaration");
    }

    private ParseError error(final Token token, final String message) {
        mErrorHandler.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advanceInputPointer();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            }

            switch (peek().type) {
                case CLASS:
                case FUN:
                case LET:
                case FOR:
                case IF:
                case WHILE:
                case ASSERT:
                case RETURN:
                    return;
            }

            advanceInputPointer();
        }
    }

    private boolean match(final TokenType... types) {
        for (final var type : types) {
            if (check(type)) {
                advanceInputPointer();
                return true;
            }
        }

        return false;
    }

    private boolean check(final TokenType type) {
        if (isAtEnd()) {
            return false;
        }

        return peek().type == type;
    }

    private boolean isAtEnd() {
        return (peek().type == EOF);
    }

    private Token peek() {
        return mTokens.get(mCurrentToken);
    }

    private Token peekNext() {
        return mTokens.get(mCurrentToken + 1);
    }

    private Token peekNextNext() {
        final var nextNext = mCurrentToken + 2;
        final var totalTokens = mTokens.size();
        return (nextNext > totalTokens ? mTokens.get(totalTokens - 1) : mTokens.get(nextNext));
    }

    private void advanceInputPointer() {
        if (!isAtEnd()) {
            ++mCurrentToken;
        }
    }

    private Token advance() {
        if (!isAtEnd()) {
            ++mCurrentToken;
        }

        return previous();
    }

    private Token previous() {
        return mTokens.get(mCurrentToken - 1);
    }

    private Expr buildBinaryExpr(final TokenType[] operators, final BinaryExprProducer exprProducer) {
        var expr = exprProducer.operand();

        while (match(operators)) {
            final var operator = exprProducer.operator();
            final var rightOperand = exprProducer.operand();
            expr = exprProducer.create(expr, operator, rightOperand);
        }

        return expr;
    }
}
