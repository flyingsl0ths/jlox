package com.lang.lox.error;

import com.lang.lox.scanner.token.Token;

public class LoxRuntimeError extends RuntimeException {
	public Token token;

	public LoxRuntimeError(final Token token, final String message) {
		super(message);
		this.token = token;
	}
}
