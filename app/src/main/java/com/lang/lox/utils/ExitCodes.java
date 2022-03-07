package com.lang.lox.utils;

public enum ExitCodes {
    EX_USAGE(64),
    EX_DATAERR(65),
    EX_SOFTWARE(70);

    final int mCode;

    ExitCodes(final int code) {
        mCode = code;
    }

    public int code() {
        return mCode;
    }
}
