package com.calcforge.engine.ast;

import lombok.Value;

/** A reference to a named variable or constant, e.g. {@code x} or {@code pi}. */
@Value
public class VariableExpr implements Expr {
    String name;
}
