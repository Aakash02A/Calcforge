package com.calcforge.engine.ast;

import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.unit.PhysicalValue;
import com.calcforge.exception.DimensionalMismatchException;

import java.util.Objects;

public record AddNode(AstNode left, AstNode right) implements AstNode {
    public AddNode {
        Objects.requireNonNull(left, "left node must not be null");
        Objects.requireNonNull(right, "right node must not be null");
    }

    @Override
    public PhysicalValue evaluate(EvaluationContext ctx) throws DimensionalMismatchException {
        PhysicalValue leftVal = left.evaluate(ctx);
        PhysicalValue rightVal = right.evaluate(ctx);
        if (!leftVal.getDimension().equals(rightVal.getDimension())) {
            throw new DimensionalMismatchException(
                    "Dimensional mismatch in addition: " + leftVal.getDimension() + " vs " + rightVal.getDimension(),
                    leftVal.getDimension(),
                    rightVal.getDimension(),
                    "+"
            );
        }
        return new PhysicalValue(
                leftVal.getValue().add(rightVal.getValue(), ctx.getMathContext()),
                leftVal.getDimension()
        );
    }
}
