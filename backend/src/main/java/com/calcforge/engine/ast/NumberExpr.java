package com.calcforge.engine.ast;

import lombok.Value;

import java.math.BigDecimal;

/** A literal numeric value, e.g. {@code 3.14} or {@code 6.022e23}. */
@Value
public class NumberExpr implements Expr {
    BigDecimal value;
}
