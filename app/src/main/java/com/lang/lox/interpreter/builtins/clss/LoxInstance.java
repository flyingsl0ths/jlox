package com.lang.lox.interpreter.builtins.clss;

import java.util.Map;

import com.lang.lox.error.LoxRuntimeError;
import com.lang.lox.scanner.token.Token;

public class LoxInstance {
	private final LoxClass mClass;
	private LoxInstance mSuperClassInstance;
	private final MemberAccessor<Object> mFields;

	public LoxInstance(final LoxClass loxClass, final LoxInstance superClassInstance,
			final Map<String, Field<Object>> fields) {
		mClass = loxClass;
		mSuperClassInstance = superClassInstance;
		mFields = new MemberAccessor<>(mClass.getName().lexeme, fields);
	}

	public LoxClass getSuperClass() {
		return mClass.getSuperClass();
	}

	public LoxInstance getSuperClassInstance() {
		return mSuperClassInstance;
	}

	public void setSuperClassInstance(final LoxInstance superClassInstance) {
		mSuperClassInstance = superClassInstance;
	}

	public Object get(final Token name, final boolean accessOccursWithinClass) {
		return getMember(new LookupContext(accessOccursWithinClass, false, name));
	}

	private Object getMember(final LookupContext context) {
		final var field = mFields.getValue(context);

		if (field != null) {
			return field;
		}

		final var method = mClass.findMethod(context);

		if (method != null) {
			return method.bind(this);
		}

		if (mSuperClassInstance != null) {
			context.memberAccessWithinSuperClass = true;
			return mSuperClassInstance.getMember(context);
		}

		throw new LoxRuntimeError(context.memberName,
				"Undefined property \"" + context.memberName.lexeme + "\".");
	}

	public Object getInSuperClass(final Token name) {
		return mSuperClassInstance != null ? mSuperClassInstance.getMember(new LookupContext(true, true, name)) : null;
	}

	public void set(final Token name, final Object value, final boolean accessOccursWithinClass) {
		setMemberValue(value, new LookupContext(accessOccursWithinClass, false, name));
	}

	private void setMemberValue(final Object value, final LookupContext context) {
		final var updatedMemeberValue = mFields.setValue(value, context);

		if (!updatedMemeberValue && mSuperClassInstance != null) {
			context.memberAccessWithinSuperClass = true;
			mSuperClassInstance.setMemberValue(value, context);
		}
	}

	@Override
	public String toString() {
		return "<" + mClass.getName() + " instance>";
	}
}
