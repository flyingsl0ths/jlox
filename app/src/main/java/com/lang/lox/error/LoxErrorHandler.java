package com.lang.lox.error;

import com.lang.lox.scanner.token.Token;
import com.lang.lox.scanner.token.TokenType;

public final class LoxErrorHandler {
	private boolean mHadError = false;

	private boolean mHadRuntimeError = false;

	public boolean hasError() {
		return mHadError;
	}

	public boolean hasRuntimeError() {
		return mHadRuntimeError;
	}

	public void resetErrorStatus() {
		mHadError = false;
	}

	public void resetRuntimeErrorStatus() {
		mHadRuntimeError = false;
	}

	public void error(final int line, final String message) {
		report(line, "", message);
	}

	public void error(final Token token, final String message) {
		report(token.line, ((token.type != TokenType.EOF) ? ("at \"" + token.lexeme + "\"") : "at end"),
				message);
	}

	public void detailedError(final long line, final long column, final String message) {
		System.err.printf("[Error]: In line %d, column %d: %s%n", line, column, message);

		mHadError = true;
	}

	public void report(final int line, final String where, final String message) {
		System.err.printf("[line %d] Error %s: %s%n", line, where, message);

		mHadError = true;
	}

	public void runtimeError(final LoxRuntimeError error) {
		if (error.token != null) {
			System.err.printf("%s\n[line %s]", error.getMessage(), error.token.line);
		} else {
			System.err.printf("%s", error.getMessage());
		}

		mHadRuntimeError = true;
	}

	public void warn(final Token token, final String message) {
		final var where = ((token.type != TokenType.EOF) ? ("\"" + token.lexeme + "\"") : "at end");

		System.err.printf("[line %d] %s: %s%n", token.line,
				message, where);
	}
}
