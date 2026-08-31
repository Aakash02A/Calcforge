package com.calcforge.engine.ast;

/**
 * Marker interface for every node in the expression Abstract Syntax Tree produced by
 * {@link com.calcforge.engine.Parser}. Nodes are plain, immutable data holders; all
 * evaluation logic lives in {@link com.calcforge.engine.Evaluator} so the AST stays a
 * simple, inspectable description of "what the user typed" - useful on its own for
 * building the transparent calculation trail.
 */
public interface Expr {
}
