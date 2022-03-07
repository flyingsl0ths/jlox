package com.lang.lox.interpreter;

import com.lang.lox.error.LoxErrorHandler;
import com.lang.lox.scanner.LoxScanner;
import com.lang.lox.utils.ExitCodes;
import com.lang.lox.parser.Parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class LoxInterpreter {

    private final LoxErrorHandler mErrorHandler = new LoxErrorHandler();

    private final CodeInterpreter mCodeInterpreter = new CodeInterpreter();

    public void runViaPrompt() {
        try {
            runPrompt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void runViaSourceFile(final String filePath) {
        try {
            runFile(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void run(final String code) {
        final var scanner = new LoxScanner(code, mErrorHandler);

        final var tokens = scanner.scanTokens();

        final var parser = new Parser(tokens, mErrorHandler);

        final var syntaxTree = parser.parse();

        if (mErrorHandler.hasError()) {
            System.exit(ExitCodes.EX_DATAERR.code());
        }

        if (mErrorHandler.hasRuntimeError()) {
            System.exit(ExitCodes.EX_SOFTWARE.code());
        }

        mCodeInterpreter.resolveVariableScopes(syntaxTree, mErrorHandler);

        if (mErrorHandler.hasError()) {
            System.exit(ExitCodes.EX_DATAERR.code());
        }

        mCodeInterpreter.interpret(syntaxTree, mErrorHandler);
    }

    private void runFile(final String filePath) throws IOException {
        final byte[] sourceBytes = Files.readAllBytes(Paths.get(filePath));

        mCodeInterpreter.printExpressionStatements(false);

        run(new String(sourceBytes, Charset.defaultCharset()));

        if (mErrorHandler.hasError()) {
            System.exit(ExitCodes.EX_DATAERR.code());
        }
    }

    private String readFromPrompt(final BufferedReader reader) throws IOException {
        System.out.print("> ");

        return reader.readLine();
    }

    private String runAndPrompt(final String line, BufferedReader reader) throws IOException {
        run(line);

        mErrorHandler.resetErrorStatus();

        mErrorHandler.resetRuntimeErrorStatus();

        return readFromPrompt(reader);
    }

    private void runPrompt() throws IOException {
        final InputStreamReader input = new InputStreamReader(System.in);

        final BufferedReader reader = new BufferedReader(input);

        mCodeInterpreter.printExpressionStatements(true);

        String line = readFromPrompt(reader);

        do {
            line = runAndPrompt(line, reader);
        } while (line != null);

    }
}
