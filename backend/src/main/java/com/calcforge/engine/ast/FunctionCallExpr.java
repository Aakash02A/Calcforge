package com.calcforge.engine.ast;

import lombok.Value;

import java.util.List;

/** A named function call with zero or more arguments, e.g. {@code sin(x)}, {@code max(1,2,3)}. */
@Value
public class FunctionCallExpr implements Expr {
    String name;
    List<Expr> args;
}
