package com.calcforge.engine.anomaly;

import com.calcforge.engine.AngleMode;
import com.calcforge.engine.Parser;
import com.calcforge.engine.ast.Expr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MathAnomalyScannerTest {

    @Test
    @DisplayName("Detects vertical asymptote at x=0 for 1/x")
    void testReciprocalAsymptote() {
        Expr ast = Parser.parse("1 / x");
        Map<BigDecimal, MathAnomaly> anomalies = MathAnomalyScanner.scan(
                ast,
                "x",
                BigDecimal.valueOf(-2),
                BigDecimal.valueOf(2),
                Map.of(),
                AngleMode.RADIANS,
                15,
                100
        );

        assertFalse(anomalies.isEmpty(), "Should detect asymptote at x=0");
        boolean hasAsymptote = anomalies.values().stream()
                .anyMatch(a -> a.type() == AnomalyType.ASYMPTOTE && a.x().abs().compareTo(new BigDecimal("0.1")) < 0);
        assertTrue(hasAsymptote, "Should flag ASYMPTOTE near x=0 for 1/x");
    }

    @Test
    @DisplayName("Detects vertical asymptotes for tan(x) around pi/2")
    void testTangentAsymptotes() {
        Expr ast = Parser.parse("tan(x)");
        Map<BigDecimal, MathAnomaly> anomalies = MathAnomalyScanner.scan(
                ast,
                "x",
                BigDecimal.valueOf(0),
                BigDecimal.valueOf(3.14159),
                Map.of(),
                AngleMode.RADIANS,
                15,
                120
        );

        assertFalse(anomalies.isEmpty());
        // pi/2 is ~ 1.57079
        boolean hasPiOver2Asymptote = anomalies.values().stream()
                .anyMatch(a -> a.type() == AnomalyType.ASYMPTOTE && a.x().subtract(new BigDecimal("1.57")).abs().compareTo(new BigDecimal("0.2")) < 0);
        assertTrue(hasPiOver2Asymptote, "Should flag ASYMPTOTE near pi/2 for tan(x)");
    }

    @Test
    @DisplayName("Catches domain error for ln(x) when x <= 0 as HOLE")
    void testLogarithmHole() {
        Expr ast = Parser.parse("ln(x)");
        Map<BigDecimal, MathAnomaly> anomalies = MathAnomalyScanner.scan(
                ast,
                "x",
                BigDecimal.valueOf(-2),
                BigDecimal.valueOf(2),
                Map.of(),
                AngleMode.RADIANS,
                15,
                50
        );

        assertFalse(anomalies.isEmpty());
        boolean hasDomainHole = anomalies.values().stream()
                .anyMatch(a -> a.type() == AnomalyType.HOLE && a.x().compareTo(BigDecimal.ZERO) <= 0);
        assertTrue(hasDomainHole, "Should flag HOLE for non-positive logarithm evaluation");
    }

    @Test
    @DisplayName("Catches domain error for sqrt(x) when x < 0 as HOLE")
    void testSqrtNegativeHole() {
        Expr ast = Parser.parse("sqrt(x)");
        Map<BigDecimal, MathAnomaly> anomalies = MathAnomalyScanner.scan(
                ast,
                "x",
                BigDecimal.valueOf(-4),
                BigDecimal.valueOf(4),
                Map.of(),
                AngleMode.RADIANS,
                15,
                50
        );

        assertFalse(anomalies.isEmpty());
        boolean hasSqrtHole = anomalies.values().stream()
                .anyMatch(a -> a.type() == AnomalyType.HOLE && a.x().compareTo(BigDecimal.ZERO) <= 0);
        assertTrue(hasSqrtHole, "Should flag HOLE for negative square root domain error");
    }

    @Test
    @DisplayName("Detects 0/0 removable discontinuity hole")
    void testRemovableHole() {
        Expr ast = Parser.parse("(x^2 - 1) / (x - 1)");
        Map<BigDecimal, MathAnomaly> anomalies = MathAnomalyScanner.scan(
                ast,
                "x",
                BigDecimal.valueOf(0),
                BigDecimal.valueOf(2),
                Map.of(),
                AngleMode.RADIANS,
                15,
                101
        );

        // At x=1, direct eval is 0/0, but limit is 2
        assertFalse(anomalies.isEmpty());
        boolean hasHole = anomalies.values().stream()
                .anyMatch(a -> a.type() == AnomalyType.HOLE && a.x().subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.1")) < 0);
        assertTrue(hasHole, "Should flag HOLE near x=1 for (x^2-1)/(x-1)");
    }
}
