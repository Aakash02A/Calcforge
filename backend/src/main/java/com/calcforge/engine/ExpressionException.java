package com.calcforge.engine;

import lombok.Getter;

/**
 * Thrown for any problem parsing or evaluating an expression: bad syntax, unknown
 * variables/functions, math domain errors (e.g. sqrt of a negative number), or
 * safety limits being exceeded (e.g. a runaway factorial argument).
 *
 * <p>Carries a stable {@link ErrorCode} so the API layer can return a machine-readable
 * error alongside the human-readable message, and so the frontend can react (e.g.
 * highlight the offending part of the input) without parsing free text.</p>
 */
@Getter
public class ExpressionException extends RuntimeException {

    public enum ErrorCode {
        SYNTAX_ERROR,
        UNEXPECTED_TOKEN,
        UNBALANCED_PARENTHESES,
        UNKNOWN_VARIABLE,
        UNKNOWN_FUNCTION,
        WRONG_ARGUMENT_COUNT,
        DIVISION_BY_ZERO,
        DOMAIN_ERROR,
        OVERFLOW,
        LIMIT_EXCEEDED,
        EMPTY_EXPRESSION
    }

    private final ErrorCode errorCode;

    public ExpressionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
