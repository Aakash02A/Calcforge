package com.calcforge.service;

import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.ast.Expr;
import com.calcforge.engine.unit.UnitDimension;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class EvaluationOutcome {
    Expr ast;
    BigDecimal result;
    UnitDimension dimension;
    EvaluationContext context;

    public EvaluationOutcome(Expr ast, BigDecimal result, EvaluationContext context) {
        this(ast, result, UnitDimension.DIMENSIONLESS, context);
    }

    public EvaluationOutcome(Expr ast, BigDecimal result, UnitDimension dimension, EvaluationContext context) {
        this.ast = ast;
        this.result = result;
        this.dimension = dimension == null ? UnitDimension.DIMENSIONLESS : dimension;
        this.context = context;
    }
}
