package com.lang.lox.interpreter.builtins.clss;

import java.util.Map;

import com.lang.lox.interpreter.builtins.callables.LoxFunction;

public final class LoxMetaClass {
	private final String mName;
	private final MemberAccessor<Object> mFields;
	private final MemberAccessor<LoxFunction> mMethods;

	public LoxMetaClass(String name, Map<String, Field<Object>> fields,
			Map<String, Field<LoxFunction>> methods) {
		mName = name;
		mFields = new MemberAccessor<>(mName, fields);
		mMethods = new MemberAccessor<>(mName, methods);
	}

	public MemberAccessor<Object> getFields() {
		return mFields;
	}

	public MemberAccessor<LoxFunction> getMethods() {
		return mMethods;
	}

	@Override
	public String toString() {
		return "<" + mName + " class>";
	}
}
