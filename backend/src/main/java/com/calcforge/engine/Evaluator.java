package com.calcforge.engine;

import com.calcforge.engine.ExpressionException.ErrorCode;
import com.calcforge.engine.ast.BinaryExpr;
import com.calcforge.engine.ast.Expr;
import com.calcforge.engine.ast.FactorialExpr;
import com.calcforge.engine.ast.FunctionCallExpr;
import com.calcforge.engine.ast.NumberExpr;
import com.calcforge.engine.ast.PercentExpr;
import com.calcforge.engine.ast.UnaryExpr;
import com.calcforge.engine.ast.VariableExpr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Walks a parsed {@link Expr} tree bottom-up, computing a {@link BigDecimal} result and
 * appending one {@link TrailStep} (stage {@link TrailStage#COMPUTATION}) to the
 * {@link EvaluationContext} for every real operation performed (not for bare literals or
 * variable references, which aren't "operations"). Steps are recorded in evaluation order
 * (innermost/leftmost first), which is both the true order of computation and, for the
 * vast majority of everyday expressions, the order a person would reduce them by hand.
 */
public final class Evaluator {

    private Evaluator() {
    }

    public static BigDecimal evaluate(Expr expr, EvaluationContext ctx) {
        return eval(expr, ctx);
    }

    private static BigDecimal eval(Expr expr, EvaluationContext ctx) {
        if (expr instanceof NumberExpr e) {
            return e.getValue();
        }
        if (expr instanceof VariableExpr e) {
            return resolveVariable(e.getName(), ctx);
        }
        if (expr instanceof UnaryExpr e) {
            BigDecimal operand = eval(e.getOperand(), ctx);
            BigDecimal result = "-".equals(e.getOperator()) ? operand.negate() : operand;
            recordStep(ctx, "-".equals(e.getOperator()) ? "Negate" : "Unary plus",
                    e.getOperator() + "(" + NumberFormatter.plain(operand) + ")", result);
            return result;
        }
        if (expr instanceof PercentExpr e) {
            BigDecimal operand = eval(e.getOperand(), ctx);
            BigDecimal result = operand.divide(BigDecimal.valueOf(100), ctx.getMathContext());
            recordStep(ctx, "Percent (\u00f7 100)", NumberFormatter.plain(operand) + "%", result);
            return result;
        }
        if (expr instanceof FactorialExpr e) {
            BigDecimal operand = eval(e.getOperand(), ctx);
            BigDecimal result = MathFunctions.factorial(operand);
            recordStep(ctx, "Factorial", NumberFormatter.plain(operand) + "!", result);
            return result;
        }
        if (expr instanceof FunctionCallExpr e) {
            List<BigDecimal> args = new ArrayList<>();
            for (Expr a : e.getArgs()) {
                args.add(eval(a, ctx));
            }
            BigDecimal result = MathFunctions.apply(e.getName(), args, ctx);
            String rendered = e.getName() + "(" +
                    args.stream().map(NumberFormatter::plain).collect(Collectors.joining(", ")) + ")";
            recordStep(ctx, "Apply " + e.getName() + "()", rendered, result);
            return result;
        }
        if (expr instanceof BinaryExpr e) {
            BigDecimal left = eval(e.getLeft(), ctx);
            BigDecimal right = eval(e.getRight(), ctx);
            BigDecimal result = applyBinary(e.getOperator(), left, right, ctx);
            String rendered = NumberFormatter.plain(left) + " " + e.getOperator() + " " + NumberFormatter.plain(right);
            recordStep(ctx, describeOperator(e.getOperator()), rendered, result);
            return result;
        }
        throw new IllegalStateException("Unhandled AST node: " + expr.getClass());
    }

    private static BigDecimal applyBinary(String op, BigDecimal left, BigDecimal right, EvaluationContext ctx) {
        return switch (op) {
            case "+" -> left.add(right, ctx.getMathContext());
            case "-" -> left.subtract(right, ctx.getMathContext());
            case "*" -> left.multiply(right, ctx.getMathContext());
            case "/" -> {
                if (right.signum() == 0) {
                    throw new ExpressionException(ErrorCode.DIVISION_BY_ZERO,
                            "Division by zero (" + NumberFormatter.plain(left) + " / 0)");
                }
                yield left.divide(right, ctx.getMathContext());
            }
            case "^" -> MathFunctions.power(left, right, ctx.getMathContext());
            default -> throw new IllegalStateException("Unknown operator: " + op);
        };
    }

    private static String describeOperator(String op) {
        return switch (op) {
            case "+" -> "Add";
            case "-" -> "Subtract";
            case "*" -> "Multiply";
            case "/" -> "Divide";
            case "^" -> "Raise to power";
            default -> op;
        };
    }

    private static BigDecimal resolveVariable(String name, EvaluationContext ctx) {
        BigDecimal constant = MathConstants.resolve(name, ctx.getMathContext());
        if (constant != null) {
            return constant;
        }
        BigDecimal value = ctx.lookupVariable(name);
        if (value == null) {
            throw new ExpressionException(ErrorCode.UNKNOWN_VARIABLE,
                    "Unknown variable '" + name + "' - define it first or check for a typo");
        }
        return value;
    }

    private static void recordStep(EvaluationContext ctx, String title, String expression, BigDecimal result) {
        ctx.addTrailStep(TrailStep.builder()
                .stage(TrailStage.COMPUTATION)
                .title("Step " + ctx.nextComputationStepNumber() + ": " + title)
                .expression(expression)
                .value(NumberFormatter.plain(result))
                .build());
    }
}
