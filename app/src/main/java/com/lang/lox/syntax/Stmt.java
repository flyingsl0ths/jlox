package com.lang.lox.syntax;

import com.lang.lox.scanner.token.Token;
import com.lang.lox.utils.NameVisibility;
import com.lang.lox.utils.Pair;

import java.util.List;
import java.util.Optional;

public abstract class Stmt {
    public interface Visitor<R> {
        R visitExpressionStmt(Expression stmt);

        R visitIfStmt(If stmt);

        R visitFunctionStmt(Function stmt);

        R visitBlockStmt(Block stmt);

        R visitClassStmt(Class stmt);

        R visitBreakStmt(Break stmt);

        R visitContinueStmt(Continue stmt);

        R visitReturnStmt(Return stmt);

        R visitAssertStmt(Assert stmt);

        R visitLetStmt(Let stmt);

        R visitWhileStmt(While stmt);
    }

    public abstract <R> R accept(Visitor<R> visitor);

    public static final class Expression extends Stmt {
        public Expr expression;

        public Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    public static final class If extends Stmt {
        public Expr condition;
        public Stmt thenBranch;
        public Stmt elseBranch;

        public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }
    }

    public static final class Function extends Stmt {
        public NameVisibility visibility;
        public Token name;
        public List<Pair<Token, Expr>> params;
        public List<Stmt> body;
        public boolean hasDefaultParameters = false;

        public Function(Token name, List<Pair<Token, Expr>> params, List<Stmt> body,
                        NameVisibility visibility) {
            this.name = name;
            this.params = params;
            this.body = body;
            this.visibility = visibility;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStmt(this);
        }
    }

    public static final class Block extends Stmt {
        public List<Stmt> statements;

        public Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }
    }

    public static class Class extends Stmt {
        public Token name;
        public Expr.Variable superclass;
        public List<Stmt.Let> classFields;
        public List<Stmt.Let> fields;
        public List<Stmt.Function> methods;
        public List<Stmt.Function> classMethods;

        public Class(Token name,
                     Expr.Variable superclass,
                     List<Stmt.Let> fields,
                     List<Stmt.Let> classFields,
                     List<Stmt.Function> methods, List<Stmt.Function> classMethods) {
            this.name = name;
            this.superclass = superclass;
            this.fields = fields;
            this.classFields = classFields;
            this.methods = methods;
            this.classMethods = classMethods;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitClassStmt(this);
        }
    }

    public static final class Break extends Stmt {

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBreakStmt(this);
        }
    }

    public static final class Continue extends Stmt {

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitContinueStmt(this);
        }
    }

    public static final class Return extends Stmt {
        public Token keyword;
        public Expr value;

        public Return(Token keyword, Expr value) {
            this.keyword = keyword;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStmt(this);
        }
    }

    public static final class Assert extends Stmt {
        public Expr expression;
        public Token message;

        public Assert(Expr expression, Token message) {
            this.expression = expression;
            this.message = message;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssertStmt(this);
        }
    }

    public static final class Let extends Stmt {
        public NameVisibility visibility;
        public Token name;
        public Expr initializer;

        public Let(Token name, Expr initializer, NameVisibility visibility) {
            this.name = name;
            this.initializer = initializer;
            this.visibility = visibility;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLetStmt(this);
        }
    }

    public static final class While extends Stmt {
        public Expr condition;
        public Stmt body;
        public Optional<Expression> incrementer;

        public While(Expr condition, Stmt body, Optional<Expression> incrementer) {
            this.condition = condition;
            this.body = body;
            this.incrementer = incrementer;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }
    }
}
