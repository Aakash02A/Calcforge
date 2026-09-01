package com.calcforge.engine.ast;

import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.unit.PhysicalValue;
import com.calcforge.exception.DimensionalMismatchException;

public interface AstNode {
    PhysicalValue evaluate(EvaluationContext ctx) throws DimensionalMismatchException;
}
