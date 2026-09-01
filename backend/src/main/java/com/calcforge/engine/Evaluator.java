package com.calcforge.engine;

import com.calcforge.engine.ExpressionException.ErrorCode;
import com.calcforge.engine.ast.BinaryExpr;
import com.calcforge.engine.ast.Expr;
import com.calcforge.engine.ast.FactorialExpr;
import com.calcforge.engine.ast.FunctionCallExpr;
import com.calcforge.engine.ast.NumberExpr;
import com.calcforge.engine.ast.PercentExpr;
import com.calcforge.engine.ast.PhysicalValueExpr;
import com.calcforge.engine.ast.UnaryExpr;
import com.calcforge.engine.ast.VariableExpr;
import com.calcforge.engine.unit.PhysicalValue;
import com.calcforge.engine.unit.UnitDimension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Evaluator {

    private Evaluator() {
    }

    public static BigDecimal evaluate(Expr expr, EvaluationContext ctx) {
        return evalPhysical(expr, ctx).getValue();
    }

    public static PhysicalValue evaluatePhysical(Expr expr, EvaluationContext ctx) {
        return evalPhysical(expr, ctx);
    }

    private static PhysicalValue evalPhysical(Expr expr, EvaluationContext ctx) {
        if (expr instanceof PhysicalValueExpr e) {
            return e.getPhysicalValue();
        }
        if (expr instanceof NumberExpr e) {
            return PhysicalValue.dimensionless(e.getValue());
        }
        if (expr instanceof VariableExpr e) {
            return PhysicalValue.dimensionless(resolveVariable(e.getName(), ctx));
        }
        if (expr instanceof UnaryExpr e) {
            PhysicalValue operand = evalPhysical(e.getOperand(), ctx);
            BigDecimal val = "-".equals(e.getOperator()) ? operand.getValue().negate() : operand.getValue();
            PhysicalValue result = new PhysicalValue(val, operand.getDimension());
            recordStep(ctx, "-".equals(e.getOperator()) ? "Negate" : "Unary plus",
                    e.getOperator() + "(" + formatPhysical(operand) + ")", result);
            return result;
        }
        if (expr instanceof PercentExpr e) {
            PhysicalValue operand = evalPhysical(e.getOperand(), ctx);
            BigDecimal val = operand.getValue().divide(BigDecimal.valueOf(100), ctx.getMathContext());
            PhysicalValue result = new PhysicalValue(val, operand.getDimension());
            recordStep(ctx, "Percent (\u00f7 100)", formatPhysical(operand) + "%", result);
            return result;
        }
        if (expr instanceof FactorialExpr e) {
            PhysicalValue operand = evalPhysical(e.getOperand(), ctx);
            if (!operand.getDimension().isDimensionless()) {
                throw new ExpressionException(ErrorCode.INVALID_OPERAND, "Factorial cannot be applied to unit dimensioned values");
            }
            BigDecimal val = MathFunctions.factorial(operand.getValue());
            PhysicalValue result = PhysicalValue.dimensionless(val);
            recordStep(ctx, "Factorial", NumberFormatter.plain(operand.getValue()) + "!", result);
            return result;
        }
        if (expr instanceof FunctionCallExpr e) {
            List<PhysicalValue> args = new ArrayList<>();
            for (Expr a : e.getArgs()) {
                args.add(evalPhysical(a, ctx));
            }
            List<BigDecimal> rawArgs = args.stream().map(PhysicalValue::getValue).toList();
            if (MathFunctions.isKnownFunction(e.getName())) {
                BigDecimal val = MathFunctions.apply(e.getName(), rawArgs, ctx);
                PhysicalValue result = PhysicalValue.dimensionless(val);
                String rendered = e.getName() + "(" +
                        args.stream().map(Evaluator::formatPhysical).collect(Collectors.joining(", ")) + ")";
                recordStep(ctx, "Apply " + e.getName() + "()", rendered, result);
                return result;
            }
            if (args.size() == 1) {
                String name = e.getName();
                boolean isVar = ctx.lookupVariable(name) != null;
                boolean isConst = MathConstants.resolve(name, ctx.getMathContext()) != null;
                if (isVar || isConst) {
                    BigDecimal varValue = resolveVariable(name, ctx);
                    BigDecimal val = varValue.multiply(args.get(0).getValue(), ctx.getMathContext());
                    PhysicalValue result = new PhysicalValue(val, args.get(0).getDimension());
                    recordStep(ctx, "Multiply (implicit)", name + "(" + formatPhysical(args.get(0)) + ")", result);
                    return result;
                }
            }
            throw new ExpressionException(ErrorCode.UNKNOWN_FUNCTION, "Unknown function '" + e.getName() + "'");
        }
        if (expr instanceof BinaryExpr e) {
            PhysicalValue left = evalPhysical(e.getLeft(), ctx);
            PhysicalValue right = evalPhysical(e.getRight(), ctx);
            PhysicalValue result = applyBinaryPhysical(e.getOperator(), left, right, ctx);
            String rendered = formatPhysical(left) + " " + e.getOperator() + " " + formatPhysical(right);
            recordStep(ctx, describeOperator(e.getOperator()), rendered, result);
            return result;
        }
        throw new IllegalStateException("Unhandled AST node: " + expr.getClass());
    }

    private static PhysicalValue applyBinaryPhysical(String op, PhysicalValue left, PhysicalValue right, EvaluationContext ctx) {
        return switch (op) {
            case "+" -> {
                if (!left.getDimension().equals(right.getDimension())) {
                    throw new ExpressionException(ErrorCode.INVALID_OPERAND,
                            "Dimensional mismatch in addition: " + left.getDimension().toDerivedString() + " vs " + right.getDimension().toDerivedString());
                }
                yield new PhysicalValue(left.getValue().add(right.getValue(), ctx.getMathContext()), left.getDimension());
            }
            case "-" -> {
                if (!left.getDimension().equals(right.getDimension())) {
                    throw new ExpressionException(ErrorCode.INVALID_OPERAND,
                            "Dimensional mismatch in subtraction: " + left.getDimension().toDerivedString() + " vs " + right.getDimension().toDerivedString());
                }
                yield new PhysicalValue(left.getValue().subtract(right.getValue(), ctx.getMathContext()), left.getDimension());
            }
            case "*" -> new PhysicalValue(left.getValue().multiply(right.getValue(), ctx.getMathContext()),
                    left.getDimension().multiply(right.getDimension()));
            case "/" -> {
                if (right.getValue().signum() == 0) {
                    throw new ExpressionException(ErrorCode.DIVISION_BY_ZERO,
                            "Division by zero (" + formatPhysical(left) + " / 0)");
                }
                yield new PhysicalValue(left.getValue().divide(right.getValue(), ctx.getMathContext()),
                        left.getDimension().divide(right.getDimension()));
            }
            case "^" -> {
                if (!right.getDimension().isDimensionless()) {
                    throw new ExpressionException(ErrorCode.INVALID_OPERAND, "Exponent must be dimensionless");
                }
                int expInt = right.getValue().intValueExact();
                BigDecimal val = MathFunctions.power(left.getValue(), right.getValue(), ctx.getMathContext());
                yield new PhysicalValue(val, left.getDimension().pow(expInt));
            }
            default -> throw new IllegalStateException("Unknown operator: " + op);
        };
    }

    private static String formatPhysical(PhysicalValue pv) {
        if (pv.getDimension().isDimensionless()) {
            return NumberFormatter.plain(pv.getValue());
        }
        return NumberFormatter.plain(pv.getValue()) + "[" + pv.getDimension().toDerivedString() + "]";
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

    private static void recordStep(EvaluationContext ctx, String title, String expression, PhysicalValue result) {
        ctx.addTrailStep(TrailStep.builder()
                .stage(TrailStage.COMPUTATION)
                .title("Step " + ctx.nextComputationStepNumber() + ": " + title)
                .expression(expression)
                .value(formatPhysical(result))
                .build());
    }
}
