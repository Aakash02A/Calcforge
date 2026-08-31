package com.calcforge.engine;

import lombok.Value;

/** A single lexical token with its source position, used for precise error messages. */
@Value
public class Token {
    TokenType type;
    String text;
    int position;
}
