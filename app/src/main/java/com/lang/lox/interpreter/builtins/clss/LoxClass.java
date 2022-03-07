package com.lang.lox.interpreter.builtins.clss;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lang.lox.error.LoxRuntimeError;
import com.lang.lox.interpreter.Environment;
import com.lang.lox.interpreter.Interpreter;
import com.lang.lox.interpreter.builtins.callables.LoxCallable;
import com.lang.lox.interpreter.builtins.callables.LoxFunction;
import com.lang.lox.scanner.token.Token;
import com.lang.lox.syntax.Stmt;

public final class LoxClass implements LoxCallable {
	private final Token mName;
	private final LoxMetaClass mMetaClass;
	private final LoxClass mSuperClass;
	private final Environment mEnvironment;
	private final List<Stmt.Let> mFields;
	private final MemberAccessor<LoxFunction> mMethods;

	public LoxClass(final LoxMetaClass metaClass, final LoxClass superClass, final Token name,
			Environment environment,
			final List<Stmt.Let> fields,
			final Map<String, Field<LoxFunction>> methods) {
		mMetaClass = metaClass;
		mSuperClass = superClass;
		mName = name;
		mEnvironment = environment;
		mFields = fields;
		mMethods = new MemberAccessor<>(mName.lexeme, methods);
	}

	public Token getName() {
		return mName;
	}

	public Object get(final Token name, final boolean accessOccursWithinClass) {
		return getMember(new LookupContext(accessOccursWithinClass, false, name));
	}

	private Object getMember(final LookupContext context) {
		final var field = mMetaClass.getFields().getValue(context);

		if (field != null) {
			return field;
		}

		final var method = mMetaClass.getMethods().getValue(context);

		if (method != null) {
			return method.bind(this);
		}

		if (mSuperClass != null) {
			context.memberAccessWithinSuperClass = true;
			return mSuperClass.getMember(context);
		}

		throw new LoxRuntimeError(context.memberName,
				"Undefined property \"" + context.memberName.lexeme + "\".");
	}

	public void set(final Token name, final Object value, final boolean accessOccursWithinClass) {
		setMemberValue(value, new LookupContext(accessOccursWithinClass, false, name));
	}

	private void setMemberValue(final Object value,
			final LookupContext context) {
		final var updatedValue = mMetaClass.getFields().setValue(value, context);

		if (!updatedValue && mSuperClass != null) {
			context.memberAccessWithinSuperClass = true;
			mSuperClass.setMemberValue(value, context);
		}
	}

	public Environment getEnvironment() {
		return mEnvironment;
	}

	public LoxClass getSuperClass() {
		return mSuperClass;
	}

	@Override
	public int arity() {
		final var initializer = findConstructor();

		return (initializer != null ? initializer.arity() : 0);
	}

	@Override
	public Object call(Interpreter interpreter, List<Object> arguments) {
		final var instance = new LoxInstance(this, null, initFields(interpreter, mFields));

		final var constructor = findConstructor();

		if (constructor != null) {
			constructor.bind(instance).call(interpreter, arguments);
		}

		return instance;
	}

	LoxFunction findConstructor() {
		final var constructorName = new Token(mName.type, "init", null, mName.line);

		final var initializer = mMethods.findValue(new LookupContext(true, false, constructorName));

		return (initializer != null ? initializer.value : null);
	}

	public LoxFunction findMethod(final LookupContext context) {
		final var method = mMethods.findValue(context);

		if (method != null) {
			return method.value;
		}

		if (mSuperClass != null) {
			context.memberAccessWithinSuperClass = true;
			return mSuperClass.findMethod(context);
		}

		return null;
	}

	private Map<String, Field<Object>> initFields(final Interpreter interpreter,
			final List<Stmt.Let> variableDeclarations) {
		final var fields = new HashMap<String, Field<Object>>();

		if (variableDeclarations.isEmpty()) {
			return fields;
		}

		variableDeclarations.forEach(variableDeclaration -> {
			final var hasInit = variableDeclaration.initializer != null;
			final var value = hasInit ? interpreter.evaluateVarStmtIn(mEnvironment,
					variableDeclaration) : null;

			fields.put(variableDeclaration.name.lexeme,
					new Field<>(variableDeclaration.visibility,
							value));
		});

		return fields;
	}

	@Override
	public boolean hasDefaultParameters() {
		return false;
	}

	@Override
	public String toString() {
		return "<class " + mName + ">";
	}
}
