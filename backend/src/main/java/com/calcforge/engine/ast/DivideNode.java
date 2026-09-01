package com.calcforge.engine.ast;

import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.ExpressionException;
import com.calcforge.engine.NumberFormatter;
import com.calcforge.engine.unit.PhysicalValue;
import com.calcforge.exception.DimensionalMismatchException;

import java.util.Objects;

public record DivideNode(AstNode left, AstNode right) implements AstNode {
    public DivideNode {
        Objects.requireNonNull(left, "left node must not be null");
        Objects.requireNonNull(right, "right node must not be null");
    }

    @Override
    public PhysicalValue evaluate(EvaluationContext ctx) throws DimensionalMismatchException {
        PhysicalValue leftVal = left.evaluate(ctx);
        PhysicalValue rightVal = right.evaluate(ctx);
        if (rightVal.getValue().signum() == 0) {
            throw new ExpressionException(
                    ExpressionException.ErrorCode.DIVISION_BY_ZERO,
                    "Division by zero (" + NumberFormatter.plain(leftVal.getValue()) + " / 0)"
            );
        }
        return new PhysicalValue(
                leftVal.getValue().divide(rightVal.getValue(), ctx.getMathContext()),
                leftVal.getDimension().divide(rightVal.getDimension())
        );
    }
}
