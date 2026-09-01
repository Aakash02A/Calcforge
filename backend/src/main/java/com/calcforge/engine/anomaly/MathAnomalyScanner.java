package com.calcforge.engine.anomaly;

import com.calcforge.engine.AngleMode;
import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.Evaluator;
import com.calcforge.engine.ExpressionException;
import com.calcforge.engine.ast.Expr;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * Specialized mathematical anomaly scanning engine.
 * Inspects mathematical curves alongside the adaptive BigDecimal rendering loop to detect:
 * 1. Vertical asymptotes (massive slope with rapid sign flip across infinitesimal interval).
 * 2. Structural holes & discontinuities (0/0 removable holes, division by zero, non-real domains).
 * 3. Numerical precision loss & high-frequency oscillatory instability.
 */
public final class MathAnomalyScanner {

    private static final BigDecimal MASSIVE_GRADIENT_THRESHOLD = BigDecimal.valueOf(500);
    private static final BigDecimal LARGE_MAGNITUDE_THRESHOLD = BigDecimal.valueOf(15);
    private static final BigDecimal EPSILON_OFFSET = new BigDecimal("0.00001");

    private MathAnomalyScanner() {
    }

    /**
     * Scans an expression across a viewport and returns an ordered map of anomaly coordinates.
     */
    public static Map<BigDecimal, MathAnomaly> scan(
            Expr ast,
            String variable,
            BigDecimal startX,
            BigDecimal endX,
            Map<String, BigDecimal> baseVariables,
            AngleMode angleMode,
            int precision,
            int baseSamples
    ) {
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);
        Map<BigDecimal, MathAnomaly> anomalies = new TreeMap<>();

        int samples = Math.max(50, Math.min(500, baseSamples));
        BigDecimal range = endX.subtract(startX);
        BigDecimal step = range.divide(BigDecimal.valueOf(samples - 1), mc);

        List<PointScanResult> sampleResults = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            BigDecimal x = (i == samples - 1)
                    ? endX
                    : startX.add(step.multiply(BigDecimal.valueOf(i), mc), mc);

            PointScanResult result = evaluatePoint(ast, variable, x, baseVariables, angleMode, precision);
            sampleResults.add(result);

            if (result.hasException()) {
                handleExplicitException(ast, variable, x, result, baseVariables, angleMode, precision, anomalies);
            }
        }

        detectGradientAsymptotesAndOscillations(ast, variable, sampleResults, baseVariables, angleMode, precision, anomalies);

        return anomalies;
    }

    private static PointScanResult evaluatePoint(
            Expr ast,
            String variable,
            BigDecimal x,
            Map<String, BigDecimal> baseVariables,
            AngleMode angleMode,
            int precision
    ) {
        EvaluationContext ctx = new EvaluationContext(angleMode, precision);
        ctx.setVariables(baseVariables);
        ctx.setVariable(variable, x);

        try {
            BigDecimal y = Evaluator.evaluate(ast, ctx);
            return new PointScanResult(x, y, null, null, null);
        } catch (ExpressionException ex) {
            return new PointScanResult(x, null, ex, ex.getErrorCode(), ex.getMessage());
        } catch (ArithmeticException ex) {
            return new PointScanResult(x, null, ex, ExpressionException.ErrorCode.DIVISION_BY_ZERO, ex.getMessage());
        } catch (Exception ex) {
            return new PointScanResult(x, null, ex, ExpressionException.ErrorCode.DOMAIN_ERROR, ex.getMessage());
        }
    }

    private static void handleExplicitException(
            Expr ast,
            String variable,
            BigDecimal x,
            PointScanResult res,
            Map<String, BigDecimal> baseVariables,
            AngleMode angleMode,
            int precision,
            Map<BigDecimal, MathAnomaly> anomalies
    ) {
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);
        BigDecimal leftX = x.subtract(EPSILON_OFFSET, mc);
        BigDecimal rightX = x.add(EPSILON_OFFSET, mc);

        PointScanResult leftRes = evaluatePoint(ast, variable, leftX, baseVariables, angleMode, precision);
        PointScanResult rightRes = evaluatePoint(ast, variable, rightX, baseVariables, angleMode, precision);

        if (res.errorCode == ExpressionException.ErrorCode.DIVISION_BY_ZERO) {
            if (leftRes.value != null && rightRes.value != null) {
                BigDecimal diff = rightRes.value.subtract(leftRes.value, mc).abs();
                boolean oppositeSigns = (leftRes.value.signum() != rightRes.value.signum())
                        && (leftRes.value.abs().compareTo(LARGE_MAGNITUDE_THRESHOLD) > 0 || rightRes.value.abs().compareTo(LARGE_MAGNITUDE_THRESHOLD) > 0);

                if (oppositeSigns || diff.compareTo(BigDecimal.valueOf(100)) > 0) {
                    anomalies.put(x, MathAnomaly.asymptote(
                            x,
                            "Vertical Asymptote (Division by Zero Pole)",
                            "Denominator approaches zero with diverging limits (" + res.errorMessage + ")",
                            null,
                            leftRes.value,
                            rightRes.value,
                            leftX,
                            rightX
                    ));
                    return;
                } else if (diff.compareTo(new BigDecimal("0.5")) < 0) {
                    anomalies.put(x, MathAnomaly.hole(
                            x,
                            "Removable Discontinuity (0/0 Hole)",
                            "Continuous limit exists across point but direct evaluation yields 0/0",
                            leftX,
                            rightX
                    ));
                    return;
                }
            }
            anomalies.put(x, MathAnomaly.hole(
                    x,
                    "Division by Zero Singularity",
                    "Undefined division by zero at x = " + x,
                    leftX,
                    rightX
            ));
        } else if (res.errorCode == ExpressionException.ErrorCode.DOMAIN_ERROR) {
            anomalies.put(x, MathAnomaly.hole(
                    x,
                    "Domain Boundary Discontinuity",
                    "Non-real result or domain violation: " + (res.errorMessage != null ? res.errorMessage : "Domain Error"),
                    leftX,
                    rightX
            ));
        } else if (res.errorCode == ExpressionException.ErrorCode.OVERFLOW || res.errorCode == ExpressionException.ErrorCode.LIMIT_EXCEEDED) {
            anomalies.put(x, MathAnomaly.precisionLoss(
                    x,
                    "Numerical Precision Overflow",
                    "Magnitude exceeded calculation precision limits (" + res.errorMessage + ")",
                    null,
                    leftX,
                    rightX
            ));
        } else {
            anomalies.put(x, MathAnomaly.hole(
                    x,
                    "Mathematical Discontinuity",
                    res.errorMessage != null ? res.errorMessage : "Evaluation Error",
                    leftX,
                    rightX
            ));
        }
    }

    private static void detectGradientAsymptotesAndOscillations(
            Expr ast,
            String variable,
            List<PointScanResult> points,
            Map<String, BigDecimal> baseVariables,
            AngleMode angleMode,
            int precision,
            Map<BigDecimal, MathAnomaly> anomalies
    ) {
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);
        int rapidSlopeSignChanges = 0;
        BigDecimal lastSlope = null;

        for (int i = 0; i < points.size() - 1; i++) {
            PointScanResult p1 = points.get(i);
            PointScanResult p2 = points.get(i + 1);

            if (p1.value != null && p2.value != null) {
                BigDecimal dx = p2.x.subtract(p1.x, mc);
                if (dx.compareTo(BigDecimal.ZERO) == 0) continue;

                BigDecimal dy = p2.value.subtract(p1.value, mc);
                BigDecimal slope = dy.divide(dx, mc);

                if (lastSlope != null && slope.signum() != lastSlope.signum() && slope.abs().compareTo(BigDecimal.valueOf(50)) > 0) {
                    rapidSlopeSignChanges++;
                }
                lastSlope = slope;

                boolean signFlip = (p1.value.signum() != p2.value.signum())
                        && (p1.value.abs().compareTo(LARGE_MAGNITUDE_THRESHOLD) > 0 || p2.value.abs().compareTo(LARGE_MAGNITUDE_THRESHOLD) > 0);

                if (slope.abs().compareTo(MASSIVE_GRADIENT_THRESHOLD) > 0 && signFlip) {
                    BigDecimal asympX = refineAsymptoteCoordinate(ast, variable, p1.x, p2.x, baseVariables, angleMode, precision);
                    if (!anomalies.containsKey(asympX)) {
                        anomalies.put(asympX, MathAnomaly.asymptote(
                                asympX,
                                "Vertical Asymptote (Rapid Sign Inversion)",
                                "Massive gradient " + slope.setScale(2, RoundingMode.HALF_UP) + " with instant sign inversion",
                                slope,
                                p1.value,
                                p2.value,
                                p1.x,
                                p2.x
                        ));
                    }
                }
            } else if ((p1.value == null && p2.value != null) || (p1.value != null && p2.value == null)) {
                BigDecimal boundaryX = p1.value == null ? p1.x : p2.x;
                if (!anomalies.containsKey(boundaryX)) {
                    anomalies.put(boundaryX, MathAnomaly.hole(
                            boundaryX,
                            "Domain Transition Boundary",
                            "Boundary between valid real domain and undefined/non-real region",
                            p1.x,
                            p2.x
                    ));
                }
            }
        }

        if (rapidSlopeSignChanges >= 5) {
            BigDecimal midX = points.get(points.size() / 2).x;
            anomalies.put(midX, MathAnomaly.precisionLoss(
                    midX,
                    "High-Frequency Oscillatory Instability",
                    "Detected " + rapidSlopeSignChanges + " high-frequency gradient inversions; curve exceeds sampling resolution",
                    lastSlope,
                    points.get(0).x,
                    points.get(points.size() - 1).x
            ));
        }
    }

    private static BigDecimal refineAsymptoteCoordinate(
            Expr ast,
            String variable,
            BigDecimal x1,
            BigDecimal x2,
            Map<String, BigDecimal> baseVariables,
            AngleMode angleMode,
            int precision
    ) {
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);
        BigDecimal left = x1;
        BigDecimal right = x2;

        for (int iter = 0; iter < 8; iter++) {
            BigDecimal mid = left.add(right, mc).divide(BigDecimal.valueOf(2), mc);
            PointScanResult res = evaluatePoint(ast, variable, mid, baseVariables, angleMode, precision);
            if (res.hasException()) {
                return mid;
            }
            if (res.value != null && res.value.signum() == evaluatePoint(ast, variable, left, baseVariables, angleMode, precision).value.signum()) {
                left = mid;
            } else {
                right = mid;
            }
        }

        return left.add(right, mc).divide(BigDecimal.valueOf(2), mc);
    }

    private record PointScanResult(
            BigDecimal x,
            BigDecimal value,
            Exception exception,
            ExpressionException.ErrorCode errorCode,
            String errorMessage
    ) {
        public boolean hasException() {
            return exception != null;
        }
    }
}
