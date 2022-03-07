package com.lang.lox.interpreter.builtins.clss;

import com.lang.lox.scanner.token.Token;

public final class LookupContext {
    public boolean memberAccessWithinSubClass;
    public boolean memberAccessWithinSuperClass;
    public final Token memberName;

    public LookupContext(final boolean memberAccessOccursWithinCurrentClass,
                         final boolean memberAccessOccursInSuperClass, final Token name) {
        memberAccessWithinSubClass = memberAccessOccursWithinCurrentClass;
        memberAccessWithinSuperClass = memberAccessOccursInSuperClass;
        memberName = name;
    }
}
