package com.lang.lox.interpreter;

import com.lang.lox.interpreter.builtins.callables.LoxCallable;

import java.util.List;
import java.util.Map;

public final class LoxPrelude {
    public static void load(final Map<String, Object> globalEnvironment) {
        loadTimeFunctions(globalEnvironment);

        loadIOFunctions(globalEnvironment);

        loadArrayFunction(globalEnvironment);
    }

    private static void loadArrayFunction(Map<String, Object> globalEnvironment) {
        globalEnvironment.put("len", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                final var value = arguments.get(0);

                return ((value instanceof List<?>) ? ((double) ((List<?>) value).size()) : 0.0);
            }

            @Override
            public String toString() {
                return "<native fn>";
            }

            @Override
            public boolean hasDefaultParameters() {
                return false;
            }
        });

    }

    private static void loadTimeFunctions(final Map<String, Object> globalEnvironment) {
        globalEnvironment.put("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                return Long.valueOf(System.currentTimeMillis() / 1000L).doubleValue();
            }

            @Override
            public String toString() {
                return "<native fn>";
            }

            @Override
            public boolean hasDefaultParameters() {
                return false;
            }
        });
    }

    private static void loadIOFunctions(final Map<String, Object> globalEnvironment) {
        globalEnvironment.put("print", new LoxCallable() {
            @Override
            public int arity() {
                return MAX_ARGS;
            }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {

                if (arguments.isEmpty()) {
                    System.out.println();
                } else {
                    arguments.forEach(
                            argument -> System.out
                                    .print(interpreter.stringify(argument) + " "));
                }

                System.out.println();

                return null;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }

            @Override
            public boolean hasDefaultParameters() {
                return false;
            }
        });
    }

}
