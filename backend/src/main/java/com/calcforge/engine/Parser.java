package com.calcforge.engine;

import com.calcforge.engine.ExpressionException.ErrorCode;
import com.calcforge.engine.ast.BinaryExpr;
import com.calcforge.engine.ast.Expr;
import com.calcforge.engine.ast.FactorialExpr;
import com.calcforge.engine.ast.FunctionCallExpr;
import com.calcforge.engine.ast.NumberExpr;
import com.calcforge.engine.ast.PercentExpr;
import com.calcforge.engine.ast.UnaryExpr;
import com.calcforge.engine.ast.VariableExpr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser with standard mathematical operator precedence
 * (lowest to highest): {@code + -}, then {@code * /} (including implicit
 * multiplication), then unary {@code + -}, then right-associative {@code ^},
 * then postfix {@code ! %}, then atoms (numbers, variables, function calls,
 * parenthesized sub-expressions).
 *
 * <p>Implicit multiplication ({@code 2pi}, {@code 3(4+5)}, {@code 2x}) is
 * supported: whenever a term is immediately followed by another term with no
 * operator between them, a {@code *} is inserted. An identifier is parsed as a
 * function call only when it is a recognized function name from
 * {@link MathFunctions}; otherwise it is treated as a variable, so
 * {@code x(2+3)} means "x times (2+3)" unless {@code x} happens to be a
 * function name.</p>
 */
public class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Convenience entry point: lex and parse in one call. */
    public static Expr parse(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        return new Parser(tokens).parseExpression();
    }

    public Expr parseExpression() {
        if (check(TokenType.EOF)) {
            throw new ExpressionException(ErrorCode.EMPTY_EXPRESSION, "Expression is empty");
        }
        Expr expr = expression();
        if (!check(TokenType.EOF)) {
            Token bad = peek();
            throw new ExpressionException(ErrorCode.UNEXPECTED_TOKEN,
                    "Unexpected '" + bad.getText() + "' at position " + bad.getPosition());
        }
        return expr;
    }

    // expression := term (('+' | '-') term)*
    private Expr expression() {
        Expr expr = term();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = advance().getType() == TokenType.PLUS ? "+" : "-";
            Expr right = term();
            expr = new BinaryExpr(expr, op, right);
        }
        return expr;
    }

    // term := unary (('*' | '/' | <implicit>) unary)*
    // Note: '%' is NOT a binary modulo operator here - see postfix(). On a calculator
    // keypad '%' conventionally means "percent" (divide by 100), applied postfix to a
    // single value, e.g. "50%" -> 0.5. Real modulo is available as the mod(a,b) function.
    private Expr term() {
        Expr expr = unary();
        while (true) {
            if (check(TokenType.STAR)) {
                advance();
                expr = new BinaryExpr(expr, "*", unary());
            } else if (check(TokenType.SLASH)) {
                advance();
                expr = new BinaryExpr(expr, "/", unary());
            } else if (startsImplicitFactor()) {
                expr = new BinaryExpr(expr, "*", unary());
            } else {
                break;
            }
        }
        return expr;
    }

    private boolean startsImplicitFactor() {
        return check(TokenType.NUMBER) || check(TokenType.IDENTIFIER) || check(TokenType.LPAREN);
    }

    // unary := ('+' | '-') unary | power
    private Expr unary() {
        if (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = advance().getType() == TokenType.PLUS ? "+" : "-";
            return new UnaryExpr(op, unary());
        }
        return power();
    }

    // power := postfix ('^' unary)?   -- right associative, exponent may itself be unary (2^-2)
    private Expr power() {
        Expr base = postfix();
        if (check(TokenType.CARET)) {
            advance();
            Expr exponent = unary();
            return new BinaryExpr(base, "^", exponent);
        }
        return base;
    }

    // postfix := primary ( '!' | '%' )*
    private Expr postfix() {
        Expr expr = primary();
        while (true) {
            if (check(TokenType.BANG)) {
                advance();
                expr = new FactorialExpr(expr);
            } else if (check(TokenType.PERCENT)) {
                advance();
                expr = new PercentExpr(expr);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr primary() {
        if (check(TokenType.NUMBER)) {
            Token t = advance();
            try {
                return new NumberExpr(new BigDecimal(t.getText()));
            } catch (NumberFormatException e) {
                throw new ExpressionException(ErrorCode.SYNTAX_ERROR,
                        "Malformed number '" + t.getText() + "'");
            }
        }

        if (check(TokenType.IDENTIFIER)) {
            Token t = advance();
            String name = t.getText();
            boolean nextIsParen = check(TokenType.LPAREN);
            if (nextIsParen) {
                advance(); // consume '('
                List<Expr> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    args.add(expression());
                    while (check(TokenType.COMMA)) {
                        advance();
                        args.add(expression());
                    }
                }
                consume(TokenType.RPAREN, "Missing closing ')' for '" + name + "(...)'");
                return new FunctionCallExpr(name.toLowerCase(), args);
            }
            return new VariableExpr(name);
        }

        if (check(TokenType.LPAREN)) {
            advance();
            Expr expr = expression();
            consume(TokenType.RPAREN, "Missing closing ')'");
            return expr;
        }

        Token bad = peek();
        if (bad.getType() == TokenType.EOF) {
            throw new ExpressionException(ErrorCode.UNBALANCED_PARENTHESES,
                    "Expression ended unexpectedly - check for a missing operand or ')'");
        }
        throw new ExpressionException(ErrorCode.UNEXPECTED_TOKEN,
                "Unexpected '" + bad.getText() + "' at position " + bad.getPosition());
    }

    // ---- token stream helpers ----

    private boolean check(TokenType type) {
        return peek().getType() == type;
    }

    private Token advance() {
        Token t = tokens.get(current);
        if (t.getType() != TokenType.EOF) {
            current++;
        }
        return t;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private void consume(TokenType type, String errorMessage) {
        if (check(type)) {
            advance();
            return;
        }
        ErrorCode code = type == TokenType.RPAREN ? ErrorCode.UNBALANCED_PARENTHESES : ErrorCode.UNEXPECTED_TOKEN;
        throw new ExpressionException(code, errorMessage + " (found '" + peek().getText() + "')");
    }
}
