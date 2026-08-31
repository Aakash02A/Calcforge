package com.calcforge.engine.ast;

import lombok.Value;

/** A postfix percent, e.g. {@code 50%} means "50 / 100". */
@Value
public class PercentExpr implements Expr {
    Expr operand;
}
