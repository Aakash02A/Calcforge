package com.calcforge.engine.ast;

import lombok.Value;

/** A postfix factorial, e.g. {@code 5!}. */
@Value
public class FactorialExpr implements Expr {
    Expr operand;
}
