package com.calcforge.engine;

/** Lexical categories produced by {@link Lexer}. */
public enum TokenType {
    NUMBER,
    NUMBER_WITH_UNIT,
    IDENTIFIER,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    CARET,
    BANG,
    LPAREN,
    RPAREN,
    COMMA,
    EOF
}
