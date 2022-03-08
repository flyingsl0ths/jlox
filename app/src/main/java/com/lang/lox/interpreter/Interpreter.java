package com.lang.lox.interpreter;

import com.lang.lox.error.LoxErrorHandler;
import com.lang.lox.error.LoxRuntimeError;
import com.lang.lox.interpreter.builtins.clss.Field;
import com.lang.lox.interpreter.builtins.clss.LoxClass;
import com.lang.lox.interpreter.builtins.clss.LoxInstance;
import com.lang.lox.interpreter.builtins.clss.LoxMetaClass;
import com.lang.lox.interpreter.builtins.clss.MemberAccessor;
import com.lang.lox.interpreter.builtins.LoxReturn;
import com.lang.lox.interpreter.builtins.callables.LoxCallable;
import com.lang.lox.interpreter.builtins.callables.LoxFunction;
import com.lang.lox.interpreter.builtins.callables.LoxLambda;
import com.lang.lox.scanner.token.Token;
import com.lang.lox.scanner.token.TokenType;
import com.lang.lox.syntax.Expr;
import com.lang.lox.syntax.Stmt;
import com.lang.lox.syntax.Expr.Get;
import com.lang.lox.syntax.Expr.Set;
import com.lang.lox.syntax.Expr.Super;
import com.lang.lox.syntax.Expr.This;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class Interpreter implements Stmt.Visitor<Void>, Expr.Visitor<Object> {
	private boolean mPrintExpressionStatements = false;

	private Environment mEnvironment;

	private final Map<String, Object> mGlobals = new HashMap<>();
	private final Map<Expr, Integer> mLocals = new HashMap<>();
	private final Map<Expr, Integer> mSlots = new HashMap<>();

	private boolean mIsWithinClass = false;
	private boolean mWasPriorExprSuperConstructor = false;

	private static class BreakException extends RuntimeException {
	}

	private static class ContinueException extends RuntimeException {
	}

	Interpreter() {
		LoxPrelude.load(mGlobals);
	}

	void printExpressionStatements(final boolean printExpressionStatements) {
		mPrintExpressionStatements = printExpressionStatements;
	}

	void interpret(final List<Stmt> statements, final LoxErrorHandler loxErrorHandler) {
		try {
			statements.forEach(this::execute);
		} catch (LoxRuntimeError error) {
			loxErrorHandler.runtimeError(error);
		}
	}

	public void execute(final Stmt stmt) {
		stmt.accept(this);
	}

	@Override
	public Void visitExpressionStmt(Stmt.Expression stmt) {
		final var value = evaluate(stmt.expression);

		if (mPrintExpressionStatements) {
			System.out.println(stringify(value));
		}

		return null;
	}

	@Override
	public Void visitIfStmt(Stmt.If stmt) {
		final Runnable evaluateElseBranch = () -> {
			final var elseBranch = stmt.elseBranch;

			if (elseBranch != null) {
				execute(stmt.elseBranch);
			}
		};

		if (isTruthy(evaluate(stmt.condition))) {
			execute(stmt.thenBranch);
		} else {
			evaluateElseBranch.run();
		}

		return null;
	}

	@Override
	public Void visitFunctionStmt(Stmt.Function stmt) {
		var function = new LoxFunction(stmt, mEnvironment, false, stmt.visibility);
		define(stmt.name, function);
		return null;
	}

	@Override
	public Void visitBreakStmt(Stmt.Break stmt) {
		throw new BreakException();
	}

	@Override
	public Void visitContinueStmt(Stmt.Continue stmt) {
		throw new ContinueException();
	}

	@Override
	public Void visitAssertStmt(Stmt.Assert stmt) {
		final var isTrue = isTruthy(evaluate(stmt.expression));

		if (isTrue) {
			throw new LoxRuntimeError(stmt.message, stmt.message.lexeme);
		}

		return null;
	}

	@Override
	public Void visitBlockStmt(Stmt.Block stmt) {
		executeBlock(stmt.statements, new Environment(mEnvironment));
		return null;
	}

	public void executeBlock(final List<Stmt> statements, final Environment environment) {
		final var previous = mEnvironment;

		final Runnable executeStatements = () -> {
			mEnvironment = environment;

			statements.forEach(this::execute);
		};

		try {
			executeStatements.run();
		} finally {
			mEnvironment = previous;
		}
	}

	@Override
	public Void visitClassStmt(Stmt.Class stmt) {
		final var classEnvironment = new Environment(mEnvironment);
		final var classFields = stmt.classFields;
		final var metaClassFields = new HashMap<String, Field<Object>>();

		LoxClass superClass = null;

		if (stmt.superclass != null) {
			final var value = evaluate(stmt.superclass);

			if (!(value instanceof LoxClass)) {
				throw new LoxRuntimeError(stmt.superclass.name, "Superclass must be a class.");
			}

			superClass = (LoxClass) value;
		}

		if (!classFields.isEmpty()) {
			classFields.forEach(field -> {
				final var value = MemberAccessor.field(field.visibility,
						evaluateVarStmtIn(classEnvironment, field));
				metaClassFields.put(field.name.lexeme, value);
			});
		}

		final var classMethods = stmt.classMethods;
		final var metaMethods = new HashMap<String, Field<LoxFunction>>();
		if (!classMethods.isEmpty()) {

			classMethods.forEach(method -> {
				final var function = new LoxFunction(method, mEnvironment, false, method.visibility);

				metaMethods.put(method.name.lexeme,
						MemberAccessor.field(method.visibility, function));
			});
		}

		final var instanceMethods = stmt.methods;
		final var methods = new HashMap<String, Field<LoxFunction>>();
		final var constructorName = "init";
		if (!instanceMethods.isEmpty()) {
			instanceMethods.forEach(method -> {
				final var function = new LoxFunction(method, mEnvironment,
						method.name.lexeme.equals(constructorName),
						method.visibility);

				methods.put(method.name.lexeme,
						MemberAccessor.field(method.visibility, function));
			});
		}

		final var metaClass = new LoxMetaClass(
				stmt.name.lexeme, metaClassFields, metaMethods);

		final var loxClass = new LoxClass(metaClass, superClass, stmt.name, classEnvironment,
				stmt.fields, methods);

		define(stmt.name, loxClass);
		/*
		 * This code is mirrored in the interpreter. When we evaluate a subclass
		 * definition, we create a new environment.
		 */

		return null;
	}

	@Override
	public Void visitLetStmt(Stmt.Let stmt) {
		Object value = null;

		final var initializer = stmt.initializer;

		if (initializer != null) {
			value = evaluate(initializer);
		}

		define(stmt.name, value);

		return null;
	}

	public Object evaluateVarStmtIn(final Environment environment, final Stmt.Let stmt) {
		final var previous = mEnvironment;

		final Supplier<Object> executeStatements = () -> {
			mEnvironment = environment;

			Object value;

			final var initializer = stmt.initializer;

			value = evaluate(initializer);

			return value;
		};

		try {
			return executeStatements.get();
		} finally {
			mEnvironment = previous;
		}
	}

	private void define(final Token name, final Object value) {
		final var inGlobalScope = mEnvironment == null;
		if (inGlobalScope) {
			mGlobals.put(name.lexeme, value);
		} else {
			mEnvironment.define(value);
		}
	}

	private void update(final Expr.Variable variable, final Object value) {
		final var inGlobalScope = mEnvironment == null;
		if (inGlobalScope) {
			mGlobals.put(variable.name.lexeme, value);
		} else {
			mEnvironment.assignAt(mLocals.get(variable), mSlots.get(variable), value);
		}
	}

	@Override
	public Void visitWhileStmt(Stmt.While stmt) {
		try {
			while (isTruthy(evaluate(stmt.condition))) {
				try {
					execute(stmt.body);
				} catch (ContinueException ignored) {
					stmt.incrementer.ifPresent(this::execute);
				}
			}
		} catch (BreakException ignored) {
		}

		return null;
	}

	public String stringify(Object value) {
		if (value == null) {
			return "nil";
		}

		if (value instanceof String) {
			return ("\"" + value + "\"");
		} else if (value instanceof Double) {
			return Double.toString((double) value).replaceAll("\\.0", "");
		} else if (value instanceof List<?>) {
			return stringifyList((List<?>) value);
		}

		return value.toString();
	}

	private String stringifyList(List<?> values) {
		if (values.isEmpty()) {
			return "[]";
		}

		final var buffer = new StringBuilder("[");

		values.stream().limit(values.size() - 1)
				.forEach(value -> buffer.append(Interpreter.this.stringify(value)).append(", "));

		final var lastValue = stringify(values.get(values.size() - 1));

		buffer.append(lastValue).append("]");

		return buffer.toString();
	}

	@Override
	public Object visitArrayExpr(Expr.Array expr) {
		final var values = expr.values;

		final List<Object> result = new ArrayList<>();

		if (!values.isEmpty()) {
			values.forEach(value -> result.add(evaluate(value)));
		}

		return result;
	}

	@Override
	public Object visitVariableExpr(Expr.Variable expr) {
		return lookupVariable(expr.name, expr);
	}

	private Object lookupVariable(final Token name, final Expr expr) {
		final var scope = mLocals.get(expr);

		if (scope != null) {
			return mEnvironment.getAt(scope, mSlots.get(expr));
		} else if (mGlobals.containsKey(name.lexeme)) {
			return mGlobals.get(name.lexeme);
		} else {
			throw new LoxRuntimeError(name,
					"Undefined variable '" + name.lexeme + "'.");
		}
	}

	@Override
	public Object visitAssignExpr(Expr.Assign expr) {
		final var value = evaluate(expr.value);
		final var distance = mLocals.get(expr);

		if (distance != null) {
			mEnvironment.assignAt(distance, mSlots.get(expr), value);
		} else if (mGlobals.containsKey(expr.name.lexeme)) {
			mGlobals.put(expr.name.lexeme, value);
		} else {
			throw new LoxRuntimeError(expr.name,
					"Undefined variable '" + expr.name.lexeme + "'.");
		}

		return value;
	}

	@Override
	public Object visitConditionalExpr(Expr.Conditional expr) {
		return isTruthy(evaluate(expr.condition)) ? evaluate(expr.thenBranch) : evaluate(expr.elseBranch);
	}

	public Object evaluate(final Expr expression) {
		return expression.accept(this);
	}

	private boolean isTruthy(final Object object) {
		if (object == null)
			return false;

		if (object instanceof Double) {
			return isTruthyNumber((double) object);
		} else if (object instanceof Boolean) {
			return (boolean) object;
		} else if (object instanceof String) {
			return isTruthyString((String) object);
		} else if (object instanceof List) {
			return isTruthyList((List<?>) object);
		} else
			return object instanceof LoxCallable;
	}

	private boolean isTruthyNumber(final double value) {
		return value > 0;
	}

	private boolean isTruthyString(final String value) {
		return !value.equals("");
	}

	private boolean isTruthyList(final List<?> value) {
		return !value.isEmpty();
	}

	@Override
	public Object visitBinaryExpr(Expr.Binary expr) {
		final var left = evaluate(expr.left);
		final var right = evaluate(expr.right);

		return evaluate(expr, left, right);
	}

	private Object evaluate(final Expr.Binary expr, final Object left, final Object right) {
		switch (expr.operator.type) {
			case COMMA:
				return right;
			case BANG_EQUAL:
				return !isEqual(left, right);
			case EQUAL_EQUAL:
				return isEqual(left, right);
			case GREATER:
				checkNumberOperands(expr.operator, left, right);
				return ((double) left) > ((double) right);
			case GREATER_EQUAL:
				checkNumberOperands(expr.operator, left, right);
				return ((double) left) >= ((double) right);
			case LESS:
				checkNumberOperands(expr.operator, left, right);
				return ((double) left) < ((double) right);
			case LESS_EQUAL:
				checkNumberOperands(expr.operator, left, right);
				return ((double) left) <= ((double) right);
			case PLUS:
				return addition(expr.operator, left, right);
			case PLUS_PLUS:
				return concatenate(expr.operator, left, right);
			case INSERTION:
				return insertInto(expr.operator, left, right);
			case MINUS:
				checkNumberOperands(expr.operator, left, right);
				return ((double) left) - ((double) right);
			case SLASH:
				return divide(expr.operator, left, right,
						(leftOperand, rightOperand) -> leftOperand / rightOperand);
			case STAR:
				return multiply(expr.operator, left, right);
			case MOD:
				return divide(expr.operator, left, right,
						(leftOperand, rightOperand) -> leftOperand % rightOperand);
			case EXPONENT:
				checkNumberOperands(expr.operator, left, right);
				return Math.pow(((double) left), ((double) right));
			default:
				return null;
		}

	}

	@SuppressWarnings("unchecked")
	private Object insertInto(final Token operator, final Object left, final Object right) {

		if (left instanceof List) {
			((List<Object>) left).add(right);
			return left;
		}

		throw new LoxRuntimeError(operator, "Unsupported operands in insertion expression");
	}

	private Object divide(final Token operator, final Object left, final Object right,
			final BiFunction<Double, Double, Double> divisionExpr) {
		checkNumberOperands(operator, left, right);

		final var leftOperand = ((double) left);

		final var rightOperand = ((double) right);

		if (rightOperand == 0) {
			throw new LoxRuntimeError(operator, "Division by zero is not allowed");
		}

		return divisionExpr.apply(leftOperand, rightOperand);
	}

	private Object addition(final Token operator, final Object left, final Object right) {
		if (left instanceof Double && right instanceof Double) {
			return (double) left + (double) right;
		}

		if (left instanceof String && right instanceof Double) {
			return (String) left + (double) right;
		}

		if (left instanceof String && right instanceof Boolean) {
			return (String) left + (boolean) right;
		}

		throw new LoxRuntimeError(operator, "Unsupported operands in addition expression");
	}

	private boolean isEqual(final Object left, final Object right) {
		if (left == null && right == null) {
			return true;
		}

		if (left == null) {
			return false;
		}

		return left.equals(right);
	}

	private void checkNumberOperands(final Token operator,
			final Object left, final Object right) {
		if (left instanceof Double && right instanceof Double)
			return;

		throw new LoxRuntimeError(operator, "Operands must be numbers.");
	}

	private Object concatenate(final Token operator, final Object left, final Object right) {
		if (left instanceof String && right instanceof String) {
			return concatenateStrings((String) left, (String) right);
		}

		if (left instanceof List && right instanceof List) {
			return concatenateLists(
					(List<?>) left, (List<?>) right);
		}

		throw new LoxRuntimeError(operator, "Unsupported operands in concatenation expression");
	}

	private String concatenateStrings(String left, String right) {
		if (left.isEmpty()) {
			return right;
		} else if (right.isEmpty()) {
			return left;
		}

		return (left + right);
	}

	private List<?> concatenateLists(final List<?> left, final List<?> right) {
		final List<Object> newList = new ArrayList<>();

		if (!left.isEmpty()) {
			newList.addAll(left);
		}

		if (!right.isEmpty()) {
			newList.addAll(right);
		}

		return newList;
	}

	@SuppressWarnings("unchecked")
	private Object multiply(Token operator, Object left, Object right) {
		if (left instanceof Double && right instanceof Double) {
			return ((double) left) * ((double) right);
		}

		if (left instanceof String && right instanceof Double) {
			return multiplyString((String) left, (double) right);
		}

		if (left instanceof List && right instanceof Double) {
			return multiplyList((List<Object>) left, (double) right);
		}

		throw new LoxRuntimeError(operator, "Unsupported operands in multiplication expression");
	}

	private Object multiplyString(final String left, final double times) {
		if (times == 0) {
			return "";
		} else if (times == 1) {
			return left;
		}

		final var builder = new StringBuilder();

		final double repeat = Math.round(times);

		for (int i = 0; i < repeat; ++i) {
			builder.append(left);
		}

		return builder.toString();
	}

	private Object multiplyList(final List<Object> list, final double times) {
		final List<Object> result = new ArrayList<>();

		if (times == 0) {
			return result;
		} else if (times == 1) {
			return list;
		} else if (list.isEmpty()) {
			return result;
		}

		for (int i = 0; i < times; ++i) {
			result.addAll(list);
		}

		return result;
	}

	@Override
	public Object visitGroupingExpr(Expr.Grouping expr) {
		return evaluate(expr.expression);
	}

	@Override
	public Object visitLiteralExpr(Expr.Literal expr) {
		return expr.value;
	}

	@Override
	public Object visitUnaryExpr(Expr.Unary expr) {
		final var right = evaluate(expr.right);

		switch (expr.operator.type) {
			case BANG:
				return !isTruthy(right);
			case MINUS:
				checkNumberOperand(expr.operator, right);
				return -((double) right);
			case PLUS:
				checkNumberOperand(expr.operator, right);
				return right;
			default:
				return null;
		}
	}

	private void checkNumberOperand(Token operator, Object operand) {
		if (operand instanceof Double) {
			return;
		}

		throw new LoxRuntimeError(operator, "Operand must be a number.");
	}

	@Override
	public Object visitLogicalExpr(Expr.Logical expr) {
		final var leftOperand = evaluate(expr.left);

		final var operatorToken = expr.operator.type;
		if (operatorToken == TokenType.OR) {
			if (isTruthy(leftOperand)) {
				return leftOperand;
			}
		} else if (operatorToken == TokenType.AND) {
			if (!isTruthy(leftOperand)) {
				return leftOperand;
			}
		}

		return evaluate(expr.right);
	}

	@Override
	public Object visitPostfixExpr(Expr.Postfix expr) {
		final var varValue = visitVariableExpr(expr.left);

		switch (expr.operator.type) {
			case PLUS_PLUS:
				checkNumberValue(expr.operator, varValue);
				return postfixIncrementVar(expr.left, (double) varValue);
			case MINUS_MINUS:
				checkNumberValue(expr.operator, varValue);
				return postfixDecrementVar(expr.left, (double) varValue);
			default:
				throw new LoxRuntimeError(expr.operator, "Unknown operator");
		}
	}

	private void checkNumberValue(Token operator, Object operand) {
		if (operand instanceof Double) {
			return;
		}

		throw new LoxRuntimeError(operator, "Value must be a number.");
	}

	private double postfixIncrementVar(final Expr.Variable variable, double right) {
		update(variable, right + 1);
		return right;
	}

	private double postfixDecrementVar(final Expr.Variable variable, double right) {
		update(variable, right - 1);
		return right;
	}

	@Override
	public Object visitPrefixExpr(Expr.Prefix expr) {
		final var varValue = visitVariableExpr(expr.right);

		switch (expr.operator.type) {
			case PLUS_PLUS:
				checkNumberValue(expr.operator, varValue);
				return prefixIncrementVar(expr.right, (double) varValue);
			case MINUS_MINUS:
				checkNumberValue(expr.operator, varValue);
				return prefixDecrementVar(expr.right, (double) varValue);
			default:
				throw new LoxRuntimeError(expr.operator, "Unknown operator");
		}
	}

	private double prefixIncrementVar(final Expr.Variable variable, double right) {
		update(variable, ++right);
		return right;
	}

	private double prefixDecrementVar(final Expr.Variable variable, double right) {
		update(variable, --right);
		return right;
	}

	@Override
	public Object visitCallExpr(Expr.Call expr) {
		var callee = evaluate(expr.callee);

		var arguments = new ArrayList<>();

		final var args = expr.arguments;
		if (!args.isEmpty()) {
			args.forEach(argument -> arguments.add(evaluate(argument)));
		}

		if (!(callee instanceof LoxCallable)) {
			throw new LoxRuntimeError(expr.paren, "Object is not callable");
		}

		var function = (LoxCallable) callee;

		final var arity = function.arity();
		if (!function.hasDefaultParameters() && arity != LoxCallable.MAX_ARGS &&
				arguments.size() != arity) {
			throw new LoxRuntimeError(expr.paren, "Expected " +
					arity + " argument(s) but got " +
					arguments.size() + ".");
		}

		final var result = evaluateFunctionCallResult(mWasPriorExprSuperConstructor, function, arguments);

		mWasPriorExprSuperConstructor = false;

		return result;
	}

	private Object evaluateFunctionCallResult(final boolean wasPriorExprSuperClassConstructor,
			final LoxCallable function, final List<Object> arguments) {

		final var result = function.call(this, arguments);

		if (wasPriorExprSuperClassConstructor && function instanceof LoxClass) {
			((LoxInstance) mEnvironment.getThisObject()).setSuperClassInstance((LoxInstance) result);
			return null;
		}

		return result;
	}

	@Override
	public Object visitGetExpr(Get expr) {
		final var object = evaluate(expr.object);
		final var isWithinClass = mIsWithinClass;
		mIsWithinClass = false;

		if (object instanceof LoxInstance) {
			return ((LoxInstance) object).get(expr.name, isWithinClass);
		} else if (object instanceof LoxClass) {
			return ((LoxClass) object).get(expr.name, isWithinClass);
		}

		throw new LoxRuntimeError(expr.name, "Is not a class or instance of a class");
	}

	@Override
	public Object visitSetExpr(Set expr) {
		final var object = evaluate(expr.object);
		final var isWithinClass = mIsWithinClass;
		mIsWithinClass = false;

		if (object instanceof LoxInstance) {
			final var value = evaluate(expr.value);
			final var instance = ((LoxInstance) object);

			instance.set(expr.name, value, isWithinClass);
			return value;
		} else if (object instanceof LoxClass) {
			final var value = evaluate(expr.value);
			final var instance = ((LoxClass) object);

			instance.set(expr.name, value, isWithinClass);
			return value;
		}

		throw new LoxRuntimeError(expr.name, "Is not a class or an instance of a class");
	}

	@Override
	public Object visitThisExpr(This expr) {
		mIsWithinClass = true;
		return lookupVariable(expr.keyword, expr);
	}

	@Override
	public Object visitSuperExpr(Super expr) {
		final LoxInstance obj = (LoxInstance) mEnvironment.getThisObject();

		if (expr.memberName != null) {
			return obj.getInSuperClass(expr.memberName);
		}

		mWasPriorExprSuperConstructor = true;

		final var superClassInstance = obj.getSuperClassInstance();

		return superClassInstance != null ? superClassInstance : obj.getSuperClass();
	}

	@Override
	public Void visitReturnStmt(Stmt.Return stmt) {
		Object value = null;

		if (stmt.value != null) {
			value = evaluate(stmt.value);
		}

		throw new LoxReturn(value);
	}

	public Object visitLambdaExpr(Expr.Lambda expr) {
		return new LoxLambda(expr, mEnvironment, false);
	}

	@Override
	public Object visitSubscriptExpr(Expr.Subscript expr) {
		var callee = evaluate(expr.callee);

		if (!(callee instanceof List<?>)) {
			throw new LoxRuntimeError(expr.bracket, "Object is not subscript-able");
		}

		var indexValue = toNumeric(evaluate(expr.index));

		var array = (List<?>) callee;

		checkArraySize(array.size(), indexValue, expr.bracket);

		return array.get(indexValue);
	}

	private void checkArraySize(final int arraySize, final int indexValue, final Token source) {
		if (arraySize == 0 || indexValue > arraySize || indexValue < 0) {
			throw new LoxRuntimeError(source, "Array index out of bounds");
		}
	}

	private int toNumeric(Object value) {
		if (value instanceof Boolean) {
			return (((boolean) value) ? 1 : 0);
		} else if (value instanceof String) {
			return (isTruthyString((String) value) ? 1 : 0);
		} else if (value instanceof List<?>) {
			return (isTruthyList((List<?>) value) ? 1 : 0);
		} else if (value instanceof LoxCallable) {
			return 1;
		} else if (value == null) {
			return 0;
		}

		return ((int) Math.floor((double) value));
	}

	public void resolve(final Expr expr, final int depth, final int slot) {
		mLocals.put(expr, depth);
		mSlots.put(expr, slot);
	}
}
