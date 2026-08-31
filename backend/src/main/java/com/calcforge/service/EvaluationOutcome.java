package com.calcforge.service;

import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.ast.Expr;
import lombok.Value;

import java.math.BigDecimal;

/** Bundles everything produced by one expression evaluation: the parsed AST, the numeric
 * result, and the context (which carries the computation trail, angle mode and precision used). */
@Value
public class EvaluationOutcome {
    Expr ast;
    BigDecimal result;
    EvaluationContext context;
}
