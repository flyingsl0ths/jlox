package com.lang.lox.interpreter;

import java.util.ArrayList;
import java.util.List;

public final class Environment {
    private final Environment enclosing;

    private final List<Object> values = new ArrayList<>();

    public Environment(final Environment enclosingEnvironment) {
        enclosing = enclosingEnvironment;
    }

    public void define(final Object value) {
        values.add(value);
    }

    public Object getAt(final int distance, final int slot) {
        var ancestor = ancestor(distance);

        return ancestor != null ? ancestor.values.get(slot) : null;
    }

    public Object getThisObject() {
        return getAt(1, 0);
    }

    public void assignAt(final int distance, final int slot, final Object value) {
        ancestor(distance).values.set(slot, value);
    }

    private Environment ancestor(int distance) {
        var environment = this;

        for (var i = 0; i < distance; ++i) {
            environment = environment.enclosing;
        }

        return environment;
    }

    @Override
    public String toString() {
        String result = values.toString();
        if (enclosing != null) {
            result += " -> " + enclosing;
        }

        return result;
    }
}
