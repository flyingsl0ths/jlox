package com.lang.lox;

import com.lang.lox.interpreter.LoxInterpreter;
import com.lang.lox.utils.ExitCodes;

public final class Lox {
    public static void main(String[] args) {
        final var interpreter = new LoxInterpreter();

        if (args.length > 1) {
            displayIncorrectUsageMessageAndExit();
        } else if (args.length == 1) {
            interpreter.runViaSourceFile(args[0]);
        } else {
            interpreter.runViaPrompt();
        }
    }

    private static void displayIncorrectUsageMessageAndExit() {
        System.out.println("Usage: jlox <script>");

        System.exit(ExitCodes.EX_USAGE.code());
    }

}
