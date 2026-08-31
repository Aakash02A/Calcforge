package com.calcforge.engine;

import com.calcforge.engine.ast.BinaryExpr;
import com.calcforge.engine.ast.Expr;
import com.calcforge.engine.ast.FactorialExpr;
import com.calcforge.engine.ast.FunctionCallExpr;
import com.calcforge.engine.ast.NumberExpr;
import com.calcforge.engine.ast.PercentExpr;
import com.calcforge.engine.ast.UnaryExpr;
import com.calcforge.engine.ast.VariableExpr;

/**
 * Renders an AST back to a human-readable, unambiguous string. Nested binary/unary
 * sub-expressions are always parenthesized, trading a few "extra" parentheses for
 * zero ambiguity - appropriate for a product whose whole premise is a transparent
 * calculation trail.
 */
public final class ExprFormatter {

    private ExprFormatter() {
    }

    public static String format(Expr expr) {
        if (expr instanceof NumberExpr e) {
            return NumberFormatter.plain(e.getValue());
        }
        if (expr instanceof VariableExpr e) {
            return e.getName();
        }
        if (expr instanceof UnaryExpr e) {
            return e.getOperator() + wrapIfNeeded(e.getOperand());
        }
        if (expr instanceof PercentExpr e) {
            return wrapIfNeeded(e.getOperand()) + "%";
        }
        if (expr instanceof FactorialExpr e) {
            return wrapIfNeeded(e.getOperand()) + "!";
        }
        if (expr instanceof FunctionCallExpr e) {
            StringBuilder sb = new StringBuilder(e.getName()).append('(');
            for (int i = 0; i < e.getArgs().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(format(e.getArgs().get(i)));
            }
            return sb.append(')').toString();
        }
        if (expr instanceof BinaryExpr e) {
            return wrapIfNeeded(e.getLeft()) + " " + e.getOperator() + " " + wrapIfNeeded(e.getRight());
        }
        throw new IllegalStateException("Unknown expression node: " + expr.getClass());
    }

    private static String wrapIfNeeded(Expr e) {
        String s = format(e);
        if (e instanceof BinaryExpr || e instanceof UnaryExpr) {
            return "(" + s + ")";
        }
        return s;
    }
}
