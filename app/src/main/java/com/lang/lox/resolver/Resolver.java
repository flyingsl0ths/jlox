package com.lang.lox.resolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import com.lang.lox.error.LoxErrorHandler;
import com.lang.lox.interpreter.Interpreter;
import com.lang.lox.scanner.token.Token;
import com.lang.lox.scanner.token.TokenType;
import com.lang.lox.syntax.*;
import com.lang.lox.syntax.Expr.Array;
import com.lang.lox.syntax.Expr.Assign;
import com.lang.lox.syntax.Expr.Binary;
import com.lang.lox.syntax.Expr.Call;
import com.lang.lox.syntax.Expr.Conditional;
import com.lang.lox.syntax.Expr.Get;
import com.lang.lox.syntax.Expr.Grouping;
import com.lang.lox.syntax.Expr.Lambda;
import com.lang.lox.syntax.Expr.Literal;
import com.lang.lox.syntax.Expr.Logical;
import com.lang.lox.syntax.Expr.Postfix;
import com.lang.lox.syntax.Expr.Prefix;
import com.lang.lox.syntax.Expr.Set;
import com.lang.lox.syntax.Expr.Subscript;
import com.lang.lox.syntax.Expr.Super;
import com.lang.lox.syntax.Expr.This;
import com.lang.lox.syntax.Expr.Unary;
import com.lang.lox.syntax.Stmt.Assert;
import com.lang.lox.syntax.Stmt.Block;
import com.lang.lox.syntax.Stmt.Break;
import com.lang.lox.syntax.Stmt.Class;
import com.lang.lox.syntax.Stmt.Continue;
import com.lang.lox.syntax.Stmt.Expression;
import com.lang.lox.syntax.Stmt.Function;
import com.lang.lox.syntax.Stmt.If;
import com.lang.lox.syntax.Stmt.Let;
import com.lang.lox.syntax.Stmt.Return;
import com.lang.lox.syntax.Stmt.While;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
	private enum ClassType {
		NONE,
		CLASS,
		SUBCLASS
	}

	private enum FunctionType {
		FUNCTION,
		LAMBDA,
		META_METHOD,
		METHOD,
		PROPERTY,
		INITIALIZER,
		NONE
	}

	private enum VariableState {
		DECLARED,
		DEFINED,
		READ
	}

	private final static class Variable {
		public final Token name;
		public VariableState state;
		public final int slot;

		private Variable(final Token name, final VariableState state, final int slot) {
			this.name = name;
			this.state = state;
			this.slot = slot;
		}
	}

	private final Stack<Map<String, Variable>> mScopes = new Stack<>();
	private final Interpreter mInterpreter;
	private final LoxErrorHandler mErrorHandler;
	private FunctionType mCurrentFunctionType = FunctionType.NONE;
	private ClassType mCurrentClassType = ClassType.NONE;
	private Optional<Map<String, Variable>> mCurrentClassScope = Optional.empty();

	public Resolver(final Interpreter interpreter, final LoxErrorHandler errorHandler) {
		mInterpreter = interpreter;
		mErrorHandler = errorHandler;
	}

	@Override
	public Void visitExpressionStmt(Expression stmt) {
		resolve(stmt.expression);
		return null;
	}

	@Override
	public Void visitIfStmt(If stmt) {
		resolve(stmt.condition);
		resolve(stmt.thenBranch);

		final var elseBranch = stmt.elseBranch;

		if (elseBranch != null) {
			resolve(elseBranch);
		}

		return null;
	}

	@Override
	public Void visitFunctionStmt(Function stmt) {
		var functionName = stmt.name;
		declare(functionName);
		define(functionName);
		resolveFunction(stmt, FunctionType.FUNCTION);
		return null;
	}

	private void resolveFunction(final Function function, final FunctionType type) {
		final var enclosingFunctionType = mCurrentFunctionType;
		mCurrentFunctionType = type;

		beginScope();

		final var params = function.params;
		if (!params.isEmpty()) {
			params.forEach(param -> {
				var token = param.first;
				declare(token);
				define(token);
			});
		}

		resolve(function.body);

		checkForSuperConstructorCall(function);

		endScope();
		mCurrentFunctionType = enclosingFunctionType;
	}

	private void checkForSuperConstructorCall(final Function function) {
		if (mCurrentClassType != ClassType.SUBCLASS) {
			return;
		}

		final var isConstructor = function.name.lexeme.equals("init");

		final var missingCallToSuperConstructorErrorMessage = "\"super\" must be the first statement in subclass constructor";

		if (isConstructor && function.body.isEmpty()) {
			mErrorHandler.error(function.name,
					missingCallToSuperConstructorErrorMessage);
		}

		final var firstStmt = function.body.get(0);

		if (!(firstStmt instanceof Stmt.Expression)) {
			return;
		}

		final var expr = ((Stmt.Expression) firstStmt).expression;

		final var isNotCallToSuperConstructor = !(expr instanceof Expr.Call)
				&& !(((Expr.Call) expr).callee instanceof Expr.Super);

		if (isConstructor && isNotCallToSuperConstructor) {
			mErrorHandler.error(function.name,
					missingCallToSuperConstructorErrorMessage);
		}
	}

	@Override
	public Void visitBlockStmt(Block stmt) {
		beginScope();

		resolve(stmt.statements);

		endScope();

		return null;
	}

	@Override
	public Void visitClassStmt(Class stmt) {
		final var enclosingClassType = mCurrentClassType;
		mCurrentClassType = ClassType.CLASS;

		final var className = stmt.name;
		declare(className);
		define(className);

		if (stmt.superclass != null) {
			if (stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
				mErrorHandler.error(stmt.superclass.name, "A class cannot inherit from itself.");
			}
			mCurrentClassType = ClassType.SUBCLASS;

			resolve(stmt.superclass);
		}

		beginScope();
		final var scope = mScopes.peek();

		mCurrentClassScope = Optional.of(scope);

		final var thisKeyword = "this";
		scope.put(thisKeyword,
				new Variable(new Token(TokenType.THIS, thisKeyword, null, className.line),
						VariableState.READ,
						scope.size()));

		defineMetaClass(thisKeyword, stmt);

		final var fields = stmt.fields;
		if (!fields.isEmpty()) {
			fields.forEach(field -> {
				if (field.initializer != null) {
					resolve(field.initializer);
				}
			});
		}

		final var methods = stmt.methods;
		final BiFunction<Stmt.Function, FunctionType, Void> resolveMethod = (method, declType) -> {
			declType = method.name.lexeme.equals("init") ? FunctionType.INITIALIZER : FunctionType.METHOD;
			resolveFunction(method, declType);
			return null;
		};

		if (!methods.isEmpty()) {
			methods.forEach(method -> resolveMethod.apply(method, FunctionType.METHOD));
		}

		endScope();

		mCurrentClassType = enclosingClassType;

		return null;
	}

	private void defineMetaClass(final String thisKeyword, final Stmt.Class clss) {
		final var classFelds = clss.classFields;
		if (!classFelds.isEmpty()) {
			classFelds.forEach(field -> {
				if (field.initializer != null) {
					resolve(field.initializer);
				}
			});
		}

		final var classMethods = clss.classMethods;
		if (!classMethods.isEmpty()) {
			classMethods.forEach(classMethod -> resolveFunction(classMethod, FunctionType.META_METHOD));
		}
	}

	private void beginScope() {
		mScopes.push(new HashMap<>());
	}

	public void resolve(final List<Stmt> statements) {
		statements.forEach(stmt -> resolve(stmt));
	}

	private void resolve(final Stmt stmt) {
		stmt.accept(this);
	}

	private void resolve(final Expr expr) {
		expr.accept(this);
	}

	private void endScope() {
		mScopes.pop();
	}

	@Override
	public Void visitBreakStmt(Break stmt) {
		return null;
	}

	@Override
	public Void visitContinueStmt(Continue stmt) {
		return null;
	}

	@Override
	public Void visitReturnStmt(Return stmt) {
		if (mCurrentFunctionType == FunctionType.NONE) {
			mErrorHandler.error(stmt.keyword, "Cannot return from top level code.");
		} else if (stmt.value instanceof Expr.Super && mCurrentClassType == ClassType.CLASS
				&& mCurrentFunctionType == FunctionType.METHOD) {
			mErrorHandler.error(stmt.keyword, "Cannot return \"super\" from method");
		}

		final var value = stmt.value;

		if (value != null) {
			if (mCurrentFunctionType == FunctionType.INITIALIZER) {
				mErrorHandler.error(stmt.keyword, "Cannot return a value from a constructor");
			}

			resolve(value);
		}

		return null;
	}

	@Override
	public Void visitAssertStmt(Assert stmt) {
		final var expr = stmt.expression;

		if (expr != null) {
			resolve(expr);
		}

		return null;
	}

	@Override
	public Void visitLetStmt(Let stmt) {
		declare(stmt.name);

		if (stmt.initializer != null) {
			resolve(stmt.initializer);
		}

		define(stmt.name);

		return null;
	}

	private void declare(final Token name) {
		if (mScopes.isEmpty()) {
			return;
		}

		var scope = mScopes.peek();

		if (scope.containsKey(name.lexeme)) {
			mErrorHandler.error(name, "Redifinition of " + name.lexeme);
		}

		scope.put(name.lexeme, new Variable(name, VariableState.DECLARED, scope.size()));
	}

	private void define(final Token name) {
		if (mScopes.isEmpty()) {
			return;
		}

		mScopes.peek().get(name.lexeme).state = VariableState.DEFINED;
	}

	@Override
	public Void visitWhileStmt(While stmt) {
		resolve(stmt.condition);
		resolve(stmt.body);
		return null;
	}

	@Override
	public Void visitAssignExpr(Assign expr) {
		resolve(expr.value);
		resolveLocal(expr, expr.name, true);
		return null;
	}

	@Override
	public Void visitConditionalExpr(Conditional expr) {
		resolve(expr.condition);
		resolve(expr.thenBranch);

		final var elseBranch = expr.elseBranch;
		if (elseBranch != null) {
			resolve(elseBranch);
		}

		return null;
	}

	@Override
	public Void visitBinaryExpr(Binary expr) {
		resolve(expr.left);
		resolve(expr.right);
		return null;
	}

	@Override
	public Void visitSubscriptExpr(Subscript expr) {
		resolve(expr.callee);

		resolve(expr.index);

		return null;
	}

	@Override
	public Void visitCallExpr(Call expr) {
		resolve(expr.callee);

		final var args = expr.arguments;

		if (!args.isEmpty()) {
			expr.arguments.forEach(this::resolve);
		}

		return null;
	}

	@Override
	public Void visitGetExpr(Get expr) {
		resolve(expr.object);

		if (expr.name.lexeme.equals("init")) {
			mErrorHandler.error(expr.name,
					mCurrentClassType != ClassType.NONE
							? "Attempty to call constructor within constructor results in a stack overflow"
							: "Attempt to call constructor on an already initialized instance.");
		}

		return null;
	}

	@Override
	public Void visitSetExpr(Set expr) {
		if (expr.name.lexeme.equals("init")) {
			mErrorHandler.error(expr.name, "Cannot redefine constructor "
					+ (mCurrentClassType == ClassType.CLASS ? "within constructor"
							: "after initialization"));
		} else if (expr.name.lexeme.equals("super") && mCurrentClassType == ClassType.CLASS
				&& mCurrentFunctionType == FunctionType.METHOD) {
			mErrorHandler.error(expr.name, "Cannot reassign \"super\" class");
		}

		resolve(expr.value);
		resolve(expr.object);
		return null;
	}

	private void checkClassFieldUsage(final String fieldName) {
		mCurrentClassScope.ifPresent(scope -> {
			if (scope.containsKey(fieldName)) {
				scope.get(fieldName).state = VariableState.READ;
			}

		});
	}

	@Override
	public Void visitThisExpr(This expr) {
		if (mCurrentClassType == ClassType.NONE) {
			mErrorHandler.error(expr.keyword, "Cannot use \"this\" outside of a class");
		}

		resolveLocal(expr, expr.keyword, true);
		return null;
	}

	@Override
	public Void visitSuperExpr(Super expr) {
		if (mCurrentClassType == ClassType.NONE) {
			mErrorHandler.error(expr.keyword, "Cannot use \"super\" outside of a class");
		} else if (mCurrentClassType != ClassType.SUBCLASS) {
			mErrorHandler.error(expr.keyword, "Cannot use \"super\" in a class with no superclass");

		}

		return null;
	}

	@Override
	public Void visitGroupingExpr(Grouping expr) {
		resolve(expr.expression);
		return null;
	}

	@Override
	public Void visitUnaryExpr(Unary expr) {
		resolve(expr.right);
		return null;
	}

	@Override
	public Void visitLogicalExpr(Logical expr) {
		resolve(expr.left);
		resolve(expr.right);
		return null;
	}

	@Override
	public Void visitLiteralExpr(Literal expr) {
		return null;
	}

	@Override
	public Void visitArrayExpr(Array expr) {
		final var values = expr.values;

		if (!values.isEmpty()) {
			values.forEach(this::resolve);
		}

		return null;
	}

	@Override
	public Void visitVariableExpr(Expr.Variable expr) {
		final BooleanSupplier isDeclared = () -> {
			final var scope = mScopes.peek();
			final var lexeme = expr.name.lexeme;
			return scope.containsKey(lexeme) && scope.get(lexeme).state == VariableState.DECLARED;
		};

		if (!mScopes.isEmpty() &&
				isDeclared.getAsBoolean()) {
			mErrorHandler.error(
					expr.name, "Can't read local variable in it's own initializer");
		}

		resolveLocal(expr, expr.name, true);

		return null;
	}

	private void resolveLocal(final Expr expr, final Token name, final boolean isRead) {
		final var lexeme = name.lexeme;
		for (var i = mScopes.size() - 1; i >= 0; --i) {
			final var binding = mScopes.get(i).get(lexeme);
			if (binding != null) {
				mInterpreter.resolve(expr, mScopes.size() - 1 - i, binding.slot);

				if (binding.state != VariableState.READ && isRead) {
					binding.state = VariableState.READ;
				}

				break;
			}

		}
	}

	@Override
	public Void visitLambdaExpr(Lambda expr) {
		resolveLambda(expr, FunctionType.LAMBDA);
		return null;
	}

	private void resolveLambda(final Lambda lambda, final FunctionType type) {
		final var enclosingFunctionType = mCurrentFunctionType;
		mCurrentFunctionType = type;

		beginScope();

		final var params = lambda.params;

		if (!params.isEmpty()) {
			params.forEach(param -> {
				declare(param);
				define(param);
			});
		}

		resolve(lambda.body);

		endScope();
		mCurrentFunctionType = enclosingFunctionType;
	}

	@Override
	public Void visitPostfixExpr(Postfix expr) {
		resolve(expr.left);
		return null;
	}

	@Override
	public Void visitPrefixExpr(Prefix expr) {
		resolve(expr.right);
		return null;
	}
}
