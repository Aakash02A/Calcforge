package com.calcforge.engine;

import com.calcforge.engine.ast.Expr;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness tests for the parser + evaluator, covering precedence, implicit
 * multiplication, all built-in functions, error conditions, and the computation trail.
 * BigDecimal comparisons use compareTo (never equals/assertEquals on BigDecimal directly)
 * because two BigDecimals that are numerically equal can have different scale, e.g. 4 vs
 * 4.00, and .equals() treats those as different.
 */
class EvaluatorTest {

    private static BigDecimal eval(String expression) {
        return eval(expression, AngleMode.DEGREES, 20, Map.of());
    }

    private static BigDecimal eval(String expression, AngleMode mode, int precision, Map<String, BigDecimal> vars) {
        Expr ast = Parser.parse(expression);
        EvaluationContext ctx = new EvaluationContext(mode, precision);
        ctx.setVariables(vars);
        return Evaluator.evaluate(ast, ctx);
    }

    private static void assertNumeric(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual.toPlainString());
    }

    private static void assertApprox(double expected, BigDecimal actual, double tolerance) {
        assertTrue(Math.abs(expected - actual.doubleValue()) < tolerance,
                () -> "expected ~" + expected + " but was " + actual.toPlainString());
    }

    @Nested
    class BasicArithmetic {

        @Test
        void addsSubtractsMultipliesDivides() {
            assertNumeric("7", eval("3 + 4"));
            assertNumeric("-1", eval("3 - 4"));
            assertNumeric("12", eval("3 * 4"));
            assertNumeric("0.75", eval("3 / 4"));
        }

        @Test
        void respectsOperatorPrecedence() {
            assertNumeric("14", eval("2 + 3 * 4"));
            assertNumeric("20", eval("(2 + 3) * 4"));
            assertNumeric("10", eval("2 * 3 + 4"));
        }

        @Test
        void powerIsRightAssociative() {
            // 2^3^2 = 2^(3^2) = 2^9 = 512, NOT (2^3)^2 = 64
            assertNumeric("512", eval("2^3^2"));
        }

        @Test
        void unaryMinusBindsLooserThanPower() {
            // -2^2 = -(2^2) = -4, standard mathematical convention
            assertNumeric("-4", eval("-2^2"));
        }

        @Test
        void negativeExponent() {
            assertNumeric("0.25", eval("2^-2"));
        }

        @Test
        void divisionByZeroThrows() {
            ExpressionException ex = assertThrows(ExpressionException.class, () -> eval("5 / 0"));
            assertEquals(ExpressionException.ErrorCode.DIVISION_BY_ZERO, ex.getErrorCode());
        }
    }

    @Nested
    class ImplicitMultiplication {

        @Test
        void numberFollowedByParenthesis() {
            assertNumeric("27", eval("3(4 + 5)"));
        }

        @Test
        void numberFollowedByConstant() {
            assertApprox(2 * Math.PI, eval("2pi"), 1e-9);
        }

        @Test
        void numberFollowedByFunctionCall() {
            // 2 * sqrt(16) = 8
            assertNumeric("8", eval("2sqrt(16)"));
        }

        @Test
        void variableNotFollowedByParenIsMultiplication() {
            // "x" is not a known function name, so x(2+3) means x * (2+3)
            assertNumeric("10", eval("x(2+3)", AngleMode.DEGREES, 20, Map.of("x", BigDecimal.valueOf(2))));
        }
    }

    @Nested
    class PercentAndFactorial {

        @Test
        void percentIsDivideByOneHundred() {
            assertNumeric("0.5", eval("50%"));
            assertNumeric("2", eval("200%"));
        }

        @Test
        void percentAppliesToBillCalculations() {
            assertNumeric("36", eval("240 * 15%"));
        }

        @Test
        void factorial() {
            assertNumeric("120", eval("5!"));
            assertNumeric("1", eval("0!"));
        }

        @Test
        void factorialOfNegativeThrowsDomainError() {
            ExpressionException ex = assertThrows(ExpressionException.class, () -> eval("(-1)!"));
            assertEquals(ExpressionException.ErrorCode.DOMAIN_ERROR, ex.getErrorCode());
        }
    }

    @Nested
    class Functions {

        @Test
        void sqrtIsExactForPerfectSquares() {
            assertNumeric("4", eval("sqrt(16)"));
            assertNumeric("12", eval("sqrt(144)"));
        }

        @Test
        void sqrtOfNegativeThrowsDomainError() {
            ExpressionException ex = assertThrows(ExpressionException.class, () -> eval("sqrt(-4)"));
            assertEquals(ExpressionException.ErrorCode.DOMAIN_ERROR, ex.getErrorCode());
        }

        @Test
        void trigInDegreesMode() {
            assertApprox(0.5, eval("sin(30)"), 1e-9);
            assertApprox(0.0, eval("cos(90)"), 1e-9);
            assertApprox(1.0, eval("tan(45)"), 1e-9);
        }

        @Test
        void trigInRadiansMode() {
            assertApprox(0.0, eval("sin(0)", AngleMode.RADIANS, 20, Map.of()), 1e-9);
            assertApprox(-1.0, eval("cos(pi)", AngleMode.RADIANS, 20, Map.of()), 1e-9);
        }

        @Test
        void logsAndExp() {
            assertApprox(2.0, eval("log(100)"), 1e-9);   // base-10 log
            assertApprox(1.0, eval("ln(e)"), 1e-9);
            assertApprox(3.0, eval("log2(8)"), 1e-9);
            assertApprox(Math.E, eval("exp(1)"), 1e-9);
        }

        @Test
        void minMaxAvgSum() {
            assertNumeric("1", eval("min(5, 1, 3)"));
            assertNumeric("5", eval("max(5, 1, 3)"));
            assertNumeric("3", eval("avg(1, 3, 5)"));
            assertNumeric("9", eval("sum(1, 3, 5)"));
        }

        @Test
        void combinationsAndPermutations() {
            assertNumeric("10", eval("ncr(5, 2)"));  // 5 choose 2
            assertNumeric("20", eval("npr(5, 2)"));  // 5 permute 2
        }

        @Test
        void gcdAndLcm() {
            assertNumeric("6", eval("gcd(24, 18)"));
            assertNumeric("72", eval("lcm(24, 18)"));
        }

        @Test
        void unknownFunctionThrows() {
            ExpressionException ex = assertThrows(ExpressionException.class, () -> eval("notarealfunction(1)"));
            assertEquals(ExpressionException.ErrorCode.UNKNOWN_FUNCTION, ex.getErrorCode());
        }

        @Test
        void wrongArgumentCountThrows() {
            ExpressionException ex = assertThrows(ExpressionException.class, () -> eval("sin(1, 2)"));
            assertEquals(ExpressionException.ErrorCode.WRONG_ARGUMENT_COUNT, ex.getErrorCode());
        }
    }

    @Nested
    class VariablesAndConstants {

        @Test
        void substitutesUserVariables() {
            assertNumeric("25", eval("x^2", AngleMode.DEGREES, 20, Map.of("x", BigDecimal.valueOf(5))));
        }

        @Test
        void variableLookupIsCaseInsensitive() {
            assertNumeric("25", eval("X^2", AngleMode.DEGREES, 20, Map.of("x", BigDecimal.valueOf(5))));
        }

        @Test
        void unknownVariableThrows() {
            ExpressionException ex = assertThrows(ExpressionException.class, () -> eval("undefinedVar + 1"));
            assertEquals(ExpressionException.ErrorCode.UNKNOWN_VARIABLE, ex.getErrorCode());
        }

        @Test
        void piConstant() {
            assertApprox(Math.PI, eval("pi"), 1e-9);
        }
    }

    @Nested
    class ComputationTrail {

        @Test
        void simpleLiteralProducesNoComputationSteps() {
            EvaluationContext ctx = new EvaluationContext(AngleMode.DEGREES, 20);
            Evaluator.evaluate(Parser.parse("42"), ctx);
            assertTrue(ctx.getTrail().isEmpty());
        }

        @Test
        void compoundExpressionProducesOrderedSteps() {
            EvaluationContext ctx = new EvaluationContext(AngleMode.DEGREES, 20);
            Evaluator.evaluate(Parser.parse("2 + 3 * 4"), ctx);
            // Multiplication happens before addition, so it must appear first in the trail.
            assertEquals(2, ctx.getTrail().size());
            assertTrue(ctx.getTrail().get(0).getTitle().contains("Multiply"));
            assertTrue(ctx.getTrail().get(1).getTitle().contains("Add"));
        }
    }
}
