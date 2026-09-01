package com.calcforge.engine.ast;

import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.unit.PhysicalValue;
import com.calcforge.exception.DimensionalMismatchException;

import java.util.Objects;

public record MultiplyNode(AstNode left, AstNode right) implements AstNode {
    public MultiplyNode {
        Objects.requireNonNull(left, "left node must not be null");
        Objects.requireNonNull(right, "right node must not be null");
    }

    @Override
    public PhysicalValue evaluate(EvaluationContext ctx) throws DimensionalMismatchException {
        PhysicalValue leftVal = left.evaluate(ctx);
        PhysicalValue rightVal = right.evaluate(ctx);
        return new PhysicalValue(
                leftVal.getValue().multiply(rightVal.getValue(), ctx.getMathContext()),
                leftVal.getDimension().multiply(rightVal.getDimension())
        );
    }
}
