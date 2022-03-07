package com.lang.lox.scanner.token;

public final class Token {
    public final TokenType type;
    public final String lexeme;
    public final Object literal;
    public final int line;

    public Token(final TokenType tokenType,
                 final String lexeme,
                 final Object literal,
                 final int line
    ) {
       this.type = tokenType;
       this.lexeme = lexeme;
       this.literal = literal;
       this.line = line;
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ", lexeme='" + lexeme + '\'' +
                ", literal=" + literal +
                ", line=" + line +
                '}';
    }
}
