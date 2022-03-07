package com.lang.lox.interpreter.builtins.callables;

import java.util.List;

import com.lang.lox.error.LoxRuntimeError;
import com.lang.lox.interpreter.Environment;
import com.lang.lox.interpreter.Interpreter;
import com.lang.lox.interpreter.builtins.LoxReturn;
import com.lang.lox.syntax.Stmt;
import com.lang.lox.utils.NameVisibility;

public class LoxFunction implements LoxCallable {
	private final Stmt.Function mDeclaration;
	private final Environment mClosure;
	private int mDefinedCount = 0;
	private final boolean mIsInitializer;
	private final NameVisibility mVisibility;

	public LoxFunction(final Stmt.Function declaration, final Environment closure, final boolean isInitializer,
			final NameVisibility visibility) {
		mDeclaration = declaration;
		mClosure = closure;
		mIsInitializer = isInitializer;
		mVisibility = visibility;
	}

	public LoxFunction bind(final Object value) {
		final var environment = new Environment(mClosure);
		environment.define(value);
		return new LoxFunction(mDeclaration, environment, mIsInitializer, mVisibility);
	}

	@Override
	public int arity() {
		return mDeclaration.params.size();
	}

	@Override
	public Object call(Interpreter interpreter, List<Object> arguments) {
		var environment = new Environment(mClosure);

		defineParameters(environment, arguments, interpreter);

		try {
			interpreter.executeBlock(mDeclaration.body, environment);
		} catch (LoxReturn returnValue) {
			final var value = returnValue.value;
			return (mIsInitializer && value == null ? mClosure.getAt(0, 0) : value);
		}

		return null;
	}

	private void defineParameters(final Environment environment, final List<Object> arguments,
			final Interpreter interpreter) {
		final Runnable defineAllDefaults = () -> {
			mDeclaration.params.forEach(valuePair -> {
				var value = interpreter.evaluate(valuePair.second);

				if (value == null) {
					throw new LoxRuntimeError(valuePair.first, "Evaluated default value is 'nil'");
				}

				environment.define(value);
			});

			mDefinedCount = mDeclaration.params.size();
		};

		final Runnable defineAllGiven = () -> {
			arguments.forEach(argument -> {
				environment.define(argument);
				++mDefinedCount;
			});
		};

		final Runnable defineRemaining = () -> {
			mDeclaration.params.stream().skip(mDefinedCount).forEach(valuePair -> {
				var value = interpreter.evaluate(valuePair.second);

				if (value == null) {
					throw new LoxRuntimeError(valuePair.first, "Evaluated default value is 'nil'");
				}

				environment.define(value);
			});
		};

		if (arguments.isEmpty()) {
			defineAllDefaults.run();
		} else {
			defineAllGiven.run();
		}

		if (mDefinedCount != mDeclaration.params.size()) {
			defineRemaining.run();
		}

		mDefinedCount = 0;
	}

	@Override
	public String toString() {
		return "<fn " + mDeclaration.name.lexeme + ">";
	}

	@Override
	public boolean hasDefaultParameters() {
		return mDeclaration.hasDefaultParameters;
	}
}
