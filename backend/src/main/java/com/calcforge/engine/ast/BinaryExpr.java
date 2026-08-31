package com.calcforge.engine.ast;

import lombok.Value;

/**
 * A binary operation. {@code operator} is one of {@code + - * / % ^}.
 * Implicit multiplication (e.g. {@code 2pi}) is normalized to an explicit {@code *}
 * during parsing so the trail always shows the operator that was actually applied.
 */
@Value
public class BinaryExpr implements Expr {
    Expr left;
    String operator;
    Expr right;
}
