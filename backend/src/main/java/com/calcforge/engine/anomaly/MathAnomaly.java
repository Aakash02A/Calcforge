package com.calcforge.engine.anomaly;

import java.math.BigDecimal;

/**
 * Diagnostic record representing an identified mathematical anomaly at a specific coordinate.
 */
public record MathAnomaly(
        BigDecimal x,
        AnomalyType type,
        String description,
        String rootCause,
        BigDecimal gradient,
        BigDecimal leftValue,
        BigDecimal rightValue,
        BigDecimal leftBound,
        BigDecimal rightBound
) {
    public static MathAnomaly asymptote(BigDecimal x, String description, String rootCause, BigDecimal gradient,
                                        BigDecimal leftValue, BigDecimal rightValue, BigDecimal leftBound, BigDecimal rightBound) {
        return new MathAnomaly(x, AnomalyType.ASYMPTOTE, description, rootCause, gradient, leftValue, rightValue, leftBound, rightBound);
    }

    public static MathAnomaly hole(BigDecimal x, String description, String rootCause, BigDecimal leftBound, BigDecimal rightBound) {
        return new MathAnomaly(x, AnomalyType.HOLE, description, rootCause, null, null, null, leftBound, rightBound);
    }

    public static MathAnomaly precisionLoss(BigDecimal x, String description, String rootCause, BigDecimal gradient, BigDecimal leftBound, BigDecimal rightBound) {
        return new MathAnomaly(x, AnomalyType.PRECISION_LOSS, description, rootCause, gradient, null, null, leftBound, rightBound);
    }
}
