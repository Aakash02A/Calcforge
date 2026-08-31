package com.calcforge.engine.ast;

import lombok.Value;

/** A prefix unary operation. {@code operator} is {@code +} or {@code -}. */
@Value
public class UnaryExpr implements Expr {
    String operator;
    Expr operand;
}
