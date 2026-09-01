package com.calcforge.engine.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class UnitParser {
    private static final Map<String, UnitDimension> BASE_UNITS = new HashMap<>();

    static {
        BASE_UNITS.put("kg", UnitDimension.MASS);
        BASE_UNITS.put("g", UnitDimension.MASS);
        BASE_UNITS.put("m", UnitDimension.LENGTH);
        BASE_UNITS.put("s", UnitDimension.TIME);
        BASE_UNITS.put("A", UnitDimension.CURRENT);
        BASE_UNITS.put("K", UnitDimension.TEMPERATURE);
        BASE_UNITS.put("mol", UnitDimension.SUBSTANCE);
        BASE_UNITS.put("cd", UnitDimension.LUMINOSITY);

        UnitDimension newton = UnitDimension.MASS.multiply(UnitDimension.LENGTH).divide(UnitDimension.TIME.pow(2));
        UnitDimension joule = newton.multiply(UnitDimension.LENGTH);
        UnitDimension watt = joule.divide(UnitDimension.TIME);
        UnitDimension pascal = newton.divide(UnitDimension.LENGTH.pow(2));
        UnitDimension hertz = UnitDimension.TIME.pow(-1);
        UnitDimension coulomb = UnitDimension.CURRENT.multiply(UnitDimension.TIME);
        UnitDimension volt = watt.divide(UnitDimension.CURRENT);
        UnitDimension farad = coulomb.divide(volt);
        UnitDimension ohm = volt.divide(UnitDimension.CURRENT);
        UnitDimension tesla = newton.divide(UnitDimension.CURRENT.multiply(UnitDimension.LENGTH));

        BASE_UNITS.put("N", newton);
        BASE_UNITS.put("J", joule);
        BASE_UNITS.put("W", watt);
        BASE_UNITS.put("Pa", pascal);
        BASE_UNITS.put("Hz", hertz);
        BASE_UNITS.put("C", coulomb);
        BASE_UNITS.put("V", volt);
        BASE_UNITS.put("F", farad);
        BASE_UNITS.put("Ohm", ohm);
        BASE_UNITS.put("ohm", ohm);
        BASE_UNITS.put("\u03a9", ohm);
        BASE_UNITS.put("T", tesla);
    }

    private UnitParser() {
    }

    public static UnitDimension parse(String unitSignature) {
        Objects.requireNonNull(unitSignature, "unitSignature must not be null");
        String trimmed = unitSignature.trim();
        if (trimmed.isEmpty() || trimmed.equals("1") || trimmed.equals("dimensionless")) {
            return UnitDimension.DIMENSIONLESS;
        }

        List<Token> tokens = tokenize(trimmed);
        Parser parser = new Parser(tokens);
        UnitDimension result = parser.parseExpression();
        if (parser.hasMore()) {
            throw new IllegalArgumentException("Unexpected trailing token: " + parser.peek().text());
        }
        return result;
    }

    private enum TokenType {
        IDENTIFIER, NUMBER, STAR, SLASH, CARET, LPAREN, RPAREN, EOF
    }

    private record Token(TokenType type, String text) {}

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '*' || c == '\u00B7' || c == '\u00D7') {
                tokens.add(new Token(TokenType.STAR, "*"));
                i++;
            } else if (c == '/') {
                tokens.add(new Token(TokenType.SLASH, "/"));
                i++;
            } else if (c == '^') {
                tokens.add(new Token(TokenType.CARET, "^"));
                i++;
            } else if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                i++;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < input.length() && Character.isDigit(input.charAt(i + 1)))) {
                int start = i;
                if (c == '-') i++;
                while (i < input.length() && Character.isDigit(input.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, input.substring(start, i)));
            } else if (Character.isLetter(c) || c == '\u03a9' || c == '%') {
                int start = i;
                while (i < input.length() && (Character.isLetter(input.charAt(i)) || input.charAt(i) == '\u03a9')) {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, input.substring(start, i)));
            } else {
                throw new IllegalArgumentException("Illegal character in unit signature: '" + c + "' at position " + i);
            }
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int pos = 0;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Token peek() {
            return tokens.get(pos);
        }

        boolean hasMore() {
            return peek().type() != TokenType.EOF;
        }

        Token advance() {
            Token t = peek();
            if (t.type() != TokenType.EOF) pos++;
            return t;
        }

        boolean match(TokenType type) {
            if (peek().type() == type) {
                advance();
                return true;
            }
            return false;
        }

        UnitDimension parseExpression() {
            UnitDimension result = parseTerm();
            while (hasMore()) {
                if (match(TokenType.STAR)) {
                    result = result.multiply(parseTerm());
                } else if (match(TokenType.SLASH)) {
                    result = result.divide(parseTerm());
                } else if (peek().type() == TokenType.IDENTIFIER || peek().type() == TokenType.LPAREN) {
                    result = result.multiply(parseTerm());
                } else {
                    break;
                }
            }
            return result;
        }

        private UnitDimension parseTerm() {
            UnitDimension base = parseFactor();
            if (match(TokenType.CARET)) {
                Token expToken = advance();
                if (expToken.type() != TokenType.NUMBER) {
                    throw new IllegalArgumentException("Expected numeric exponent after '^', found: " + expToken.text());
                }
                int exponent = Integer.parseInt(expToken.text());
                return base.pow(exponent);
            } else if (peek().type() == TokenType.NUMBER) {
                Token expToken = advance();
                int exponent = Integer.parseInt(expToken.text());
                return base.pow(exponent);
            }
            return base;
        }

        private UnitDimension parseFactor() {
            Token t = peek();
            if (match(TokenType.IDENTIFIER)) {
                UnitDimension dim = BASE_UNITS.get(t.text());
                if (dim == null) {
                    throw new IllegalArgumentException("Unknown physical unit symbol: '" + t.text() + "'");
                }
                return dim;
            } else if (match(TokenType.LPAREN)) {
                UnitDimension inside = parseExpression();
                if (!match(TokenType.RPAREN)) {
                    throw new IllegalArgumentException("Missing closing parenthesis in unit expression");
                }
                return inside;
            } else {
                throw new IllegalArgumentException("Unexpected token in unit expression: '" + t.text() + "'");
            }
        }
    }
}
