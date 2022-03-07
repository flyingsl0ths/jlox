package com.lang.lox.interpreter.builtins;

public final class LoxReturn extends RuntimeException {
    public final Object value;

    public LoxReturn(final Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}
