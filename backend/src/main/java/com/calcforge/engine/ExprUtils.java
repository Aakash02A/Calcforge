package com.calcforge.engine;

import com.calcforge.engine.ast.BinaryExpr;
import com.calcforge.engine.ast.Expr;
import com.calcforge.engine.ast.FactorialExpr;
import com.calcforge.engine.ast.FunctionCallExpr;
import com.calcforge.engine.ast.NumberExpr;
import com.calcforge.engine.ast.PercentExpr;
import com.calcforge.engine.ast.UnaryExpr;
import com.calcforge.engine.ast.VariableExpr;

import java.util.LinkedHashSet;
import java.util.Set;

/** Small static-analysis helpers over the AST (used to build the "Assumptions" trail stage). */
public final class ExprUtils {

    private ExprUtils() {
    }

    /** Returns the distinct variable/constant names referenced anywhere in the expression, in first-seen order. */
    public static Set<String> collectVariableNames(Expr expr) {
        Set<String> names = new LinkedHashSet<>();
        collect(expr, names);
        return names;
    }

    private static void collect(Expr expr, Set<String> out) {
        if (expr instanceof NumberExpr) {
            return;
        }
        if (expr instanceof VariableExpr e) {
            out.add(e.getName());
            return;
        }
        if (expr instanceof UnaryExpr e) {
            collect(e.getOperand(), out);
            return;
        }
        if (expr instanceof PercentExpr e) {
            collect(e.getOperand(), out);
            return;
        }
        if (expr instanceof FactorialExpr e) {
            collect(e.getOperand(), out);
            return;
        }
        if (expr instanceof FunctionCallExpr e) {
            if (!MathFunctions.isKnownFunction(e.getName())) {
                out.add(e.getName());
            }
            e.getArgs().forEach(a -> collect(a, out));
            return;
        }
        if (expr instanceof BinaryExpr e) {
            collect(e.getLeft(), out);
            collect(e.getRight(), out);
        }
    }
}
