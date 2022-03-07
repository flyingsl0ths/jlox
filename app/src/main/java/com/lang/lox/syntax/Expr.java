package com.lang.lox.syntax;

import com.lang.lox.scanner.token.Token;

import java.util.List;

public abstract class Expr {
	public interface Visitor<R> {
		R visitAssignExpr(Assign expr);

		R visitConditionalExpr(Conditional expr);

		R visitBinaryExpr(Binary expr);

		R visitSubscriptExpr(Subscript expr);

		R visitCallExpr(Call expr);

		R visitGetExpr(Get expr);

		R visitSetExpr(Set expr);

		R visitThisExpr(This expr);

		R visitSuperExpr(Super expr);

		R visitGroupingExpr(Grouping expr);

		R visitUnaryExpr(Unary expr);

		R visitLogicalExpr(Logical expr);

		R visitLiteralExpr(Literal expr);

		R visitArrayExpr(Array expr);

		R visitVariableExpr(Variable expr);

		R visitLambdaExpr(Lambda expr);

		R visitPostfixExpr(Postfix expr);

		R visitPrefixExpr(Prefix expr);
	}

	public abstract <R> R accept(Visitor<R> visitor);

	public static final class Assign extends Expr {
		public Token name;
		public Expr value;

		public Assign(Token name, Expr value) {
			this.name = name;
			this.value = value;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitAssignExpr(this);
		}
	}

	public static final class Conditional extends Expr {
		public Expr condition;
		public Expr thenBranch;
		public Expr elseBranch;

		public Conditional(Expr condition, Expr thenBranch, Expr elseBranch) {
			this.condition = condition;
			this.thenBranch = thenBranch;
			this.elseBranch = elseBranch;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitConditionalExpr(this);
		}
	}

	public static final class Binary extends Expr {
		public Expr left;
		public Token operator;
		public Expr right;

		public Binary(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitBinaryExpr(this);
		}
	}

	public static final class Subscript extends Expr {
		public Expr callee;
		public Expr index;
		public Token bracket;

		public Subscript(Expr callee, Token bracket, Expr index) {
			this.callee = callee;
			this.index = index;
			this.bracket = bracket;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitSubscriptExpr(this);
		}
	}

	public static final class Call extends Expr {
		public Expr callee;
		public Token paren;
		public List<Expr> arguments;

		public Call(Expr callee, Token paren, List<Expr> arguments) {
			this.callee = callee;
			this.paren = paren;
			this.arguments = arguments;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitCallExpr(this);
		}
	}

	public static final class Get extends Expr {
		public Expr object;
		public Token name;

		public Get(Expr object, Token name) {
			this.object = object;
			this.name = name;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitGetExpr(this);
		}
	}

	public static class Set extends Expr {
		public Expr object;
		public Token name;
		public Expr value;

		public Set(Expr object, Token name, Expr value) {
			this.object = object;
			this.name = name;
			this.value = value;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitSetExpr(this);
		}
	}

	public static final class This extends Expr {
		public final Token keyword;

		public This(Token keyword) {
			this.keyword = keyword;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitThisExpr(this);
		}

	}

	public static class Super extends Expr {
		public Token keyword;
		public Token memberName = null;

		public Super(Token keyword, Token memberName) {
			this.keyword = keyword;
			this.memberName = memberName;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitSuperExpr(this);
		}

	}

	public static final class Grouping extends Expr {
		public Expr expression;

		public Grouping(Expr expression) {
			this.expression = expression;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitGroupingExpr(this);
		}
	}

	public static final class Unary extends Expr {
		public Token operator;
		public Expr right;

		public Unary(Token operator, Expr right) {
			this.operator = operator;
			this.right = right;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitUnaryExpr(this);
		}
	}

	public static final class Logical extends Expr {
		public Expr left;
		public Token operator;
		public Expr right;

		public Logical(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitLogicalExpr(this);
		}
	}

	public static final class Literal extends Expr {
		public Object value;

		public Literal(Object value) {
			this.value = value;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitLiteralExpr(this);
		}
	}

	public static final class Array extends Expr {
		public List<Expr> values;

		public Array(List<Expr> values) {
			this.values = values;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitArrayExpr(this);
		}
	}

	public static final class Variable extends Expr {
		public Token name;

		public Variable(Token name) {
			this.name = name;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitVariableExpr(this);
		}
	}

	public static final class Lambda extends Expr {
		public Token name;
		public List<Token> params;
		public List<Stmt> body;
		public boolean assignedToVar = false;

		public Lambda(Token name, List<Token> params, List<Stmt> body) {
			this.name = name;
			this.params = params;
			this.body = body;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitLambdaExpr(this);
		}
	}

	public static final class Postfix extends Expr {
		public Expr.Variable left;
		public Token operator;

		public Postfix(Expr.Variable left, Token operator) {
			this.left = left;
			this.operator = operator;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitPostfixExpr(this);
		}
	}

	public static final class Prefix extends Expr {
		public Token operator;
		public Expr.Variable right;

		public Prefix(Token operator, Expr.Variable right) {
			this.operator = operator;
			this.right = right;
		}

		@Override
		public <R> R accept(Visitor<R> visitor) {
			return visitor.visitPrefixExpr(this);
		}
	}
}
