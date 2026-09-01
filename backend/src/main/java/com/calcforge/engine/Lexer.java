package com.calcforge.engine;

import com.calcforge.engine.ExpressionException.ErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a raw expression string into a flat list of {@link Token}s.
 *
 * <p>Supports standard ASCII operators plus a handful of Unicode aliases that
 * calculator keypads commonly produce ({@code × ÷ −}), so pasted or keypad-built
 * input behaves identically to typed ASCII input.</p>
 */
public class Lexer {

    private final String source;
    private int pos = 0;

    public Lexer(String source) {
        this.source = source == null ? "" : source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (isAtEnd()) {
                tokens.add(new Token(TokenType.EOF, "", pos));
                break;
            }

            char c = peek();
            int start = pos;

            if (Character.isDigit(c) || c == '.') {
                tokens.add(readNumber());
                continue;
            }

            if (isIdentifierStart(c)) {
                tokens.add(readIdentifier());
                continue;
            }

            switch (c) {
                case '+' -> {
                    advance();
                    tokens.add(new Token(TokenType.PLUS, "+", start));
                }
                case '-', '\u2212' -> { // ASCII hyphen-minus or Unicode minus sign
                    advance();
                    tokens.add(new Token(TokenType.MINUS, "-", start));
                }
                case '*', '\u00D7' -> { // '*' or Unicode multiplication sign ×
                    advance();
                    tokens.add(new Token(TokenType.STAR, "*", start));
                }
                case '/', '\u00F7' -> { // '/' or Unicode division sign ÷
                    advance();
                    tokens.add(new Token(TokenType.SLASH, "/", start));
                }
                case '%' -> {
                    advance();
                    tokens.add(new Token(TokenType.PERCENT, "%", start));
                }
                case '^' -> {
                    advance();
                    tokens.add(new Token(TokenType.CARET, "^", start));
                }
                case '!' -> {
                    advance();
                    tokens.add(new Token(TokenType.BANG, "!", start));
                }
                case '(' -> {
                    advance();
                    tokens.add(new Token(TokenType.LPAREN, "(", start));
                }
                case ')' -> {
                    advance();
                    tokens.add(new Token(TokenType.RPAREN, ")", start));
                }
                case ',' -> {
                    advance();
                    tokens.add(new Token(TokenType.COMMA, ",", start));
                }
                default -> throw new ExpressionException(
                        ErrorCode.SYNTAX_ERROR,
                        "Unexpected character '" + c + "' at position " + pos);
            }
        }
        return tokens;
    }

    private Token readNumber() {
        int start = pos;
        int dotCount = 0;
        boolean sawDigit = false;

        while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '.')) {
            if (peek() == '.') {
                dotCount++;
            } else {
                sawDigit = true;
            }
            advance();
        }

        if (dotCount > 1) {
            throw new ExpressionException(ErrorCode.SYNTAX_ERROR,
                    "Malformed number with multiple decimal points at position " + start);
        }

        if (!sawDigit) {
            throw new ExpressionException(ErrorCode.SYNTAX_ERROR,
                    "Malformed number at position " + start);
        }

        // Optional exponent: e / E, optional sign, one or more digits.
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            int savedPos = pos;
            int lookahead = pos + 1;
            if (lookahead < source.length() && (source.charAt(lookahead) == '+' || source.charAt(lookahead) == '-')) {
                lookahead++;
            }
            if (lookahead < source.length() && Character.isDigit(source.charAt(lookahead))) {
                advance(); // consume e/E
                if (peek() == '+' || peek() == '-') {
                    advance();
                }
                while (!isAtEnd() && Character.isDigit(peek())) {
                    advance();
                }
            } else {
                pos = savedPos; // not actually an exponent (e.g. "3e" used as "3*e")
            }
        }

        if (!isAtEnd() && peek() == '[') {
            int closeBracket = source.indexOf(']', pos);
            if (closeBracket == -1) {
                throw new ExpressionException(ErrorCode.SYNTAX_ERROR,
                        "Unclosed unit bracket '[' at position " + pos);
            }
            pos = closeBracket + 1;
            return new Token(TokenType.NUMBER_WITH_UNIT, source.substring(start, pos), start);
        }

        return new Token(TokenType.NUMBER, source.substring(start, pos), start);
    }

    private Token readIdentifier() {
        int start = pos;
        while (!isAtEnd() && isIdentifierPart(peek())) {
            advance();
        }
        return new Token(TokenType.IDENTIFIER, source.substring(start, pos), start);
    }

    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '\u03C0'; // allow literal 'π'
    }

    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) {
            advance();
        }
    }

    private boolean isAtEnd() {
        return pos >= source.length();
    }

    private char peek() {
        return source.charAt(pos);
    }

    private void advance() {
        pos++;
    }
}
