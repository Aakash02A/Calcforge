package com.calcforge.engine.ast;

import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.unit.PhysicalValue;

import java.util.Objects;

public record ValueNode(PhysicalValue value) implements AstNode {
    public ValueNode {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public PhysicalValue evaluate(EvaluationContext ctx) {
        return value;
    }
}
