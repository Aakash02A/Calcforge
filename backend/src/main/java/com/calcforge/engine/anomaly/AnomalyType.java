package com.calcforge.engine.anomaly;

/**
 * Categorization of mathematical anomalies and structural vulnerabilities detected
 * during adaptive curve scanning.
 */
public enum AnomalyType {
    /**
     * Vertical asymptote where function values diverge toward infinity / negative infinity
     * or experience an infinite gradient with instant sign reversal.
     */
    ASYMPTOTE,

    /**
     * Structural hole, non-real result, or point discontinuity (e.g. 0/0 removable hole,
     * division by zero, non-positive logarithm, or negative square root).
     */
    HOLE,

    /**
     * Numerical precision loss, catastrophic cancellation, or extreme high-frequency
     * oscillation exceeding standard floating-point / sampling resolution.
     */
    PRECISION_LOSS
}
