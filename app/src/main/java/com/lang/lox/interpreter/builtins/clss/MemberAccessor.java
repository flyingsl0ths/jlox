package com.lang.lox.interpreter.builtins.clss;

import java.util.Map;

import com.lang.lox.error.LoxRuntimeError;
import com.lang.lox.utils.NameVisibility;

public final class MemberAccessor<T> {
    public static <T> Field<T> field(final NameVisibility nameVisibility, final T value) {
        return new Field<>(nameVisibility, value);
    }

    final String mEnclosingClassName;
    final Map<String, Field<T>> mFields;

    public MemberAccessor(final String enclosingClassName, final Map<String, Field<T>> values) {
        mFields = values;
        mEnclosingClassName = enclosingClassName;
    }

    public T getValue(final LookupContext context) {
        final var fieldName = context.memberName.lexeme;

        if (!mFields.containsKey(fieldName)) {
            return null;

        }

        final var value = mFields.get(fieldName);

        checkAccess(value, context);

        return value.value;
    }

    public boolean setValue(final T value, final LookupContext context) {
        final var fieldName = context.memberName.lexeme;

        if (!mFields.containsKey(fieldName)) {
            return false;
        }

        final var field = mFields.get(fieldName);

        checkAccess(field,
                context);

        field.value = value;

        return true;
    }

    Field<T> findValue(final LookupContext context) {
        if (mFields.isEmpty()) {
            return null;
        }

        final var field = mFields.get(context.memberName.lexeme);
        if (field != null) {
            checkAccess(field, context);
            return field;
        }

        return null;
    }

    private void checkAccess(final Field<T> field,
                             final LookupContext context) {
        final var fieldVisibility = field.visibility;
        final var illegalAccessDetected =
                (context.memberAccessWithinSuperClass && fieldVisibility == NameVisibility.PROTECTED) ||
                        (!context.memberAccessWithinSubClass && fieldVisibility == NameVisibility.PRIVATE);

        if (illegalAccessDetected) {
            throw new LoxRuntimeError(context.memberName,
                    "\"" + context.memberName.lexeme + "\" is a "
                            + field.visibility.toString().toLowerCase()
                            + " member of "
                            + mEnclosingClassName + ".");
        }
    }
}
