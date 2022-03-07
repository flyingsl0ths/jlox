package com.lang.lox.scanner;

import com.lang.lox.error.LoxErrorHandler;
import com.lang.lox.scanner.token.Token;
import com.lang.lox.scanner.token.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class LoxScanner {
    private static final class Progress {
        private int currentIndex = 0;
        private int startIndex = 0;
        private int sourceLine = 1;
    }

    private static final Map<String, TokenType> KEYWORDS;

    static {
        KEYWORDS = new HashMap<>();
        KEYWORDS.put("break", TokenType.BREAK);
        KEYWORDS.put("and", TokenType.AND);
        KEYWORDS.put("static", TokenType.STATIC);
        KEYWORDS.put("public", TokenType.PUBLIC);
        KEYWORDS.put("private", TokenType.PRIVATE);
        KEYWORDS.put("protected", TokenType.PROTECTED);
        KEYWORDS.put("class", TokenType.CLASS);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("fun", TokenType.FUN);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("nil", TokenType.NIL);
        KEYWORDS.put("or", TokenType.OR);
        KEYWORDS.put("assert", TokenType.ASSERT);
        KEYWORDS.put("continue", TokenType.CONTINUE);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("super", TokenType.SUPER);
        KEYWORDS.put("this", TokenType.THIS);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("let", TokenType.LET);
        KEYWORDS.put("while", TokenType.WHILE);
    }

    private boolean mSyntaxError = false;

    private final Progress mProgress = new Progress();

    private final String mSource;

    private final List<Token> mTokens = new ArrayList<>();

    private final LoxErrorHandler mErrorHandler;

    public LoxScanner(final String source, final LoxErrorHandler errorHandler) {
        mSource = source;
        mErrorHandler = errorHandler;
    }

    public List<Token> scanTokens() {
        while (!mSyntaxError && !isAtEnd()) {
            scanToken();
        }

        mTokens.add(new Token(TokenType.EOF, "", null, mProgress.sourceLine));

        return mTokens;
    }

    private void addToken(TokenType type, final Object literal) {
        final String text = mSource.substring(mProgress.startIndex, mProgress.currentIndex);

        mTokens.add(new Token(type, text, literal, mProgress.sourceLine));
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private boolean isAtEnd() {
        return mProgress.currentIndex >= mSource.length();
    }

    private void scanToken() {
        mProgress.startIndex = mProgress.currentIndex;

        final char scannedCharacter = advance();

        lex(scannedCharacter);
    }

    private char advance() {
        return mSource.charAt(mProgress.currentIndex++);
    }

    private void lex(final char scannedCharacter) {
        if (scannedCharacter == '(') {
            addToken(TokenType.LEFT_PAREN);

        } else if (scannedCharacter == ')') {
            addToken(TokenType.RIGHT_PAREN);

        } else if (scannedCharacter == '{') {
            addToken(TokenType.LEFT_BRACE);

        } else if (scannedCharacter == '}') {
            addToken(TokenType.RIGHT_BRACE);

        } else if (scannedCharacter == '[') {
            addToken(TokenType.LEFT_BRACKET);

        } else if (scannedCharacter == ']') {
            addToken(TokenType.RIGHT_BRACKET);

        } else if (scannedCharacter == ',') {
            addToken(TokenType.COMMA);

        } else if (scannedCharacter == '.') {
            addToken(matches('.') ? TokenType.ELLIPSE : TokenType.DOT);

        } else if (scannedCharacter == '-') {
            resolveMinusToken();

        } else if (scannedCharacter == '+') {
            addToken(matches('+') ? TokenType.PLUS_PLUS : TokenType.PLUS);

        } else if (scannedCharacter == ';') {
            addToken(TokenType.SEMICOLON);

        } else if (scannedCharacter == '*') {
            addToken(matches('*') ? TokenType.EXPONENT : TokenType.STAR);

        } else if (scannedCharacter == '%') {
            addToken(TokenType.MOD);

        } else if (scannedCharacter == '?') {
            addToken(TokenType.CONDITIONAL);

        } else if (scannedCharacter == ':') {
            addToken(TokenType.COLON);

        } else if (scannedCharacter == '|') {
            addToken(TokenType.BAR);

        } else if (isSpaceCharacter(scannedCharacter)) {
            ignoreSpaceCharacters();

        } else if (scannedCharacter == '\n') {
            ++mProgress.sourceLine;

        } else if (scannedCharacter == '!') {
            addToken(matches('=') ? TokenType.BANG_EQUAL : TokenType.BANG);

        } else if (scannedCharacter == '=') {
            resolveEqualsToken();

        } else if (scannedCharacter == '<') {
            resolveLessThanToken();

        } else if (scannedCharacter == '>') {
            addToken(matches('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);

        } else if (scannedCharacter == '/') {
            inspectSlash();

        } else if (scannedCharacter == '"') {
            string();

        } else if (isDigit(scannedCharacter)) {
            number();

        } else if (isAlpha(scannedCharacter)) {
            identifier();
        } else {
            mErrorHandler.error(mProgress.sourceLine,
                    String.format("Unexpected character: %c", scannedCharacter));
        }
    }

    private void resolveEqualsToken() {
        if (matches('>')) {
            addToken(TokenType.ARROW);
        } else if (matches('=')) {
            addToken(TokenType.EQUAL_EQUAL);
        } else {
            addToken(TokenType.EQUAL);
        }
    }

    private void resolveLessThanToken() {
        if (peek() == '<' && peekNext() == '<') {
            mProgress.currentIndex += 2;
            addToken(TokenType.INSERTION);
        } else {
            addToken(matches('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
        }
    }

    private void resolveMinusToken() {
        TokenType token;

        if (matches('-')) {
            token = TokenType.MINUS_MINUS;
        } else {
            token = TokenType.MINUS;
        }

        addToken(token);
    }

    private boolean isSpaceCharacter(final char character) {
        return character == ' ' || character == '\r' || character == '\t';
    }

    private void ignoreSpaceCharacters() {
        final char nextCharacter = peek();

        if (!isSpaceCharacter(nextCharacter)) {
            return;
        }

        final char[] nextChar = {nextCharacter};

        final Runnable skip = () -> {
            ++mProgress.currentIndex;

            nextChar[0] = peek();
        };

        while (isSpaceCharacter(nextChar[0]) && !isAtEnd()) {
            skip.run();
        }
    }

    private void inspectSlash() {
        final Runnable ignoreUntilNewLine = () -> {
            while (peek() != '\n' && !isAtEnd()) {
                ++mProgress.currentIndex;
            }
        };

        if (matches('*')) {
            ignoreMultiLineComment();
        } else if (matches('/')) {
            ignoreUntilNewLine.run();
        } else {
            addToken(TokenType.SLASH);
        }
    }

    private void ignoreMultiLineComment() {
        boolean foundEndOfComment = peek() == '*' && peekNext() == '/';

        final Runnable incrementInputPointer = () -> {
            if (peek() == '\n') {
                ++mProgress.sourceLine;
            }

            ++mProgress.currentIndex;
        };

        while (!foundEndOfComment && !isAtEnd()) {
            incrementInputPointer.run();
            foundEndOfComment = peek() == '*' && peekNext() == '/';

            if (isAtEnd()) {
                break;
            }
        }

        if (foundEndOfComment) {
            if (mSource.charAt(mProgress.currentIndex + 2) == '*') {
                mErrorHandler.detailedError(mProgress.sourceLine, mProgress.currentIndex,
                        "Joint multiline comment");
                mSyntaxError = true;
            } else {
                mProgress.currentIndex += 2;
            }

        } else {
            mErrorHandler.detailedError(mProgress.sourceLine, mProgress.currentIndex,
                    "Unterminated comment");
            mSyntaxError = true;
        }
    }

    private char peek() {
        return (isAtEnd() ? '\0' : mSource.charAt(mProgress.currentIndex));
    }

    private boolean matches(char expected) {
        if (isAtEnd()) {
            return false;
        }

        if (mSource.charAt(mProgress.currentIndex) != expected) {
            return false;
        }

        ++mProgress.currentIndex;

        return true;
    }

    private void string() {
        final Runnable computeStringLiteralLength = () -> {
            if (peek() == '\n') {
                ++mProgress.sourceLine;
            }

            ++mProgress.currentIndex;
        };

        while (peek() != '"' && !isAtEnd()) {
            computeStringLiteralLength.run();
        }

        if (isAtEnd()) {
            mErrorHandler.error(mProgress.sourceLine, "Unterminated string");
            return;
        }

        ++mProgress.currentIndex;

        // Trims the starting/ending '"'
        addToken(TokenType.STRING, mSource.substring(mProgress.startIndex + 1, mProgress.currentIndex - 1));
    }

    private boolean isDigit(final char scannedCharacter) {
        return (scannedCharacter >= '0' && scannedCharacter <= '9');
    }

    private void number() {
        while (isDigit(peek())) {
            ++mProgress.currentIndex;
        }

        final Runnable addFloatingPointNumberToken = () -> {
            ++mProgress.currentIndex;

            while (isDigit(peek())) {
                ++mProgress.currentIndex;
            }
        };

        final char nextCharacter = peek();

        if (nextCharacter == '.' && isDigit(peekNext())) {
            addFloatingPointNumberToken.run();
        }

        addToken(TokenType.NUMBER,
                Double.parseDouble(
                        mSource.substring(mProgress.startIndex, mProgress.currentIndex)));

    }

    private char peekNext() {
        return (mProgress.currentIndex + 1 >= mSource.length() ? '\0'
                : mSource.charAt(mProgress.currentIndex + 1));
    }

    private boolean isAlpha(final char character) {
        return (character >= 'a' && character <= 'z' ||
                character >= 'A' && character <= 'Z' ||
                character == '_');
    }

    private void identifier() {
        final Predicate<Character> inspectForSyntaxError = (currentCharacter) -> {
            if (currentCharacter == '\n') {
                mErrorHandler.detailedError(mProgress.sourceLine, mProgress.currentIndex,
                        "bad syntax multiple expressions after identifier");
                return (mSyntaxError = true);
            }

            return false;
        };

        while (isAlphaNumeric(peek())
                || isAllowableIdentifierSuffix(peek())) {
            ++mProgress.currentIndex;
            inspectForSyntaxError.test(peek());
        }

        final String lexeme = mSource.substring(mProgress.startIndex, mProgress.currentIndex);

        final TokenType tokenType = isKeyWord(lexeme);

        mTokens.add(
                new Token(
                        tokenType != null ? tokenType : TokenType.IDENTIFIER,
                        lexeme,
                        null,
                        mProgress.sourceLine));
    }

    private boolean isAlphaNumeric(final char character) {
        return isAlpha(character) || isDigit(character);
    }

    private boolean isAllowableIdentifierSuffix(final char character) {
        return character == '\'';
    }

    private TokenType isKeyWord(final String lexeme) {
        return KEYWORDS.get(lexeme);
    }
}
