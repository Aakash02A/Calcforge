package com.calcforge.engine;

import com.calcforge.engine.ast.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests focused on parsing/syntax rather than numeric evaluation. */
class ParserTest {

    private static String normalize(String expression) {
        Expr ast = Parser.parse(expression);
        return ExprFormatter.format(ast);
    }

    @Test
    void normalizesImplicitMultiplication() {
        assertEquals("2 * pi", normalize("2pi"));
    }

    @Test
    void normalizesPrecedenceWithExplicitParens() {
        assertEquals("2 + (3 * 4)", normalize("2+3*4"));
    }

    @Test
    void normalizesNegationOfPower() {
        assertEquals("-(2 ^ 2)", normalize("-2^2"));
    }

    @Test
    void emptyExpressionThrows() {
        ExpressionException ex = assertThrows(ExpressionException.class, () -> Parser.parse(""));
        assertEquals(ExpressionException.ErrorCode.EMPTY_EXPRESSION, ex.getErrorCode());
    }

    @Test
    void blankExpressionThrows() {
        ExpressionException ex = assertThrows(ExpressionException.class, () -> Parser.parse("   "));
        assertEquals(ExpressionException.ErrorCode.EMPTY_EXPRESSION, ex.getErrorCode());
    }

    @Test
    void unbalancedOpeningParenThrows() {
        assertThrows(ExpressionException.class, () -> Parser.parse("(1 + 2"));
    }

    @Test
    void unbalancedClosingParenThrows() {
        assertThrows(ExpressionException.class, () -> Parser.parse("1 + 2)"));
    }

    @Test
    void danglingOperatorThrows() {
        assertThrows(ExpressionException.class, () -> Parser.parse("1 +"));
    }

    @Test
    void malformedNumberThrows() {
        assertThrows(ExpressionException.class, () -> Parser.parse("1..2"));
    }

    @Test
    void acceptsScientificNotation() {
        // 6.022e23 should parse as a single NUMBER token, not "6.022" implicit-multiplied by "e23"
        assertEquals("602200000000000000000000", normalize("6.022e23"));
    }

    @Test
    void unicodeMultiplyAliasBehavesLikeAsciiStar() {
        // × is a common calculator-keypad glyph and should lex/parse exactly like *
        assertEquals(normalize("4*2"), normalize("4\u00D72"));
    }

    @Test
    void unicodeMinusAliasBehavesLikeAsciiHyphen() {
        assertEquals(normalize("4-2"), normalize("4\u22122"));
    }
}
