package com.lang.lox.interpreter.builtins.clss;

import com.lang.lox.utils.NameVisibility;

public final class Field<U> {
    final NameVisibility visibility;
    U value;

    public Field(final NameVisibility nameVisibility, final U nameValue) {
        visibility = nameVisibility;
        value = nameValue;
    }
}
