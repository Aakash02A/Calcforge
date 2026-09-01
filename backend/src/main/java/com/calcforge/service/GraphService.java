package com.calcforge.service;

import com.calcforge.dto.request.GraphAnalyzeRequest;
import com.calcforge.dto.request.GraphRequest;
import com.calcforge.dto.response.GraphAnalyzeResponse;
import com.calcforge.dto.response.GraphResponse;
import com.calcforge.engine.AngleMode;
import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.Evaluator;
import com.calcforge.engine.ExpressionException;
import com.calcforge.engine.Parser;
import com.calcforge.engine.anomaly.MathAnomaly;
import com.calcforge.engine.anomaly.MathAnomalyScanner;
import com.calcforge.engine.ast.Expr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service component for standard uniform graphing, adaptive dynamic edge-case scanning,
 * and mathematical anomaly & vulnerability detection.
 */
@Service
@RequiredArgsConstructor
public class GraphService {

    private static final int DEFAULT_SAMPLES = 200;
    private static final int MAX_SAMPLES = 2000;
    private static final int MAX_TOTAL_ANALYZE_POINTS = 5000;
    private static final int GRAPH_PRECISION = 15;
    private static final MathContext ROUND_MC = new MathContext(15, RoundingMode.HALF_UP);

    private final CalculationService calculationService;

    public GraphResponse generate(GraphRequest request) {
        if (request.min().compareTo(request.max()) >= 0) {
            throw new IllegalArgumentException("min must be less than max");
        }
        int samples = request.samples() != null ? request.samples() : DEFAULT_SAMPLES;
        if (samples < 2 || samples > MAX_SAMPLES) {
            throw new IllegalArgumentException("samples must be between 2 and " + MAX_SAMPLES);
        }

        AngleMode angleMode = calculationService.parseAngleMode(request.angleMode());
        Expr ast = Parser.parse(request.expression());
        Map<String, BigDecimal> baseVariables = calculationService.resolveVariables(request.workspaceId(), request.variables());

        BigDecimal range = request.max().subtract(request.min());
        BigDecimal step = range.divide(BigDecimal.valueOf(samples - 1), ROUND_MC);

        List<GraphResponse.GraphPointDto> points = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            BigDecimal x = (i == samples - 1)
                    ? request.max()
                    : request.min().add(step.multiply(BigDecimal.valueOf(i)), ROUND_MC);

            BigDecimal y = evaluateAt(ast, request.variable(), x, baseVariables, angleMode, GRAPH_PRECISION);
            points.add(new GraphResponse.GraphPointDto(x, y));
        }

        return new GraphResponse(request.expression(), request.variable(), request.min(), request.max(), points);
    }

    /**
     * Phase 4: Dynamic Edge-Case Graph Scanning & Math Anomaly Detection
     * Evaluates high-density mathematical curves with BigDecimal math, injects adaptive
     * sample points in steep regions, and classifies mathematical anomalies (ASYMPTOTE, HOLE, PRECISION_LOSS).
     */
    public GraphAnalyzeResponse analyze(GraphAnalyzeRequest request) {
        BigDecimal startX = request.effectiveStartX();
        BigDecimal endX = request.effectiveEndX();

        if (startX.compareTo(endX) >= 0) {
            throw new IllegalArgumentException("startX must be strictly less than endX");
        }

        int precision = request.effectivePrecision();
        MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);
        int baseSamples = request.effectiveBaseSamples();
        int subFactor = request.effectiveSubdivisionFactor();
        BigDecimal thresholdPercent = request.effectiveThresholdPercentage();
        String variable = request.effectiveVariable();

        AngleMode angleMode = calculationService.parseAngleMode(request.angleMode());
        Expr ast = Parser.parse(request.expression());
        Map<String, BigDecimal> baseVariables = calculationService.resolveVariables(request.workspaceId(), request.variables());

        Map<BigDecimal, MathAnomaly> anomalies = MathAnomalyScanner.scan(
                ast,
                variable,
                startX,
                endX,
                baseVariables,
                angleMode,
                precision,
                baseSamples
        );

        BigDecimal range = endX.subtract(startX);
        BigDecimal baseStep = range.divide(BigDecimal.valueOf(baseSamples - 1), mc);

        List<PointHolder> initialPoints = new ArrayList<>(baseSamples);
        for (int i = 0; i < baseSamples; i++) {
            BigDecimal x = (i == baseSamples - 1)
                    ? endX
                    : startX.add(baseStep.multiply(BigDecimal.valueOf(i), mc), mc);

            BigDecimal y = evaluateAt(ast, variable, x, baseVariables, angleMode, precision);
            initialPoints.add(new PointHolder(x, y));
        }

        List<GraphAnalyzeResponse.GraphPointDto> finalPoints = new ArrayList<>(baseSamples * 2);
        List<GraphAnalyzeResponse.SteepRegionDto> steepRegions = new ArrayList<>();
        int injectedCount = 0;
        int steepSegmentsCount = 0;

        for (int i = 0; i < initialPoints.size() - 1; i++) {
            PointHolder p1 = initialPoints.get(i);
            PointHolder p2 = initialPoints.get(i + 1);

            finalPoints.add(new GraphAnalyzeResponse.GraphPointDto(p1.x, p1.y));

            boolean isSteep = false;
            BigDecimal deltaY = BigDecimal.ZERO;
            BigDecimal deltaPercent = BigDecimal.ZERO;

            if (p1.y != null && p2.y != null) {
                deltaY = p2.y.subtract(p1.y).abs();
                BigDecimal maxRef = p1.y.abs().max(p2.y.abs());
                if (maxRef.compareTo(new BigDecimal("0.000001")) < 0) {
                    maxRef = BigDecimal.ONE;
                }
                deltaPercent = deltaY.divide(maxRef, mc).multiply(BigDecimal.valueOf(100), mc);

                if (deltaPercent.compareTo(thresholdPercent) >= 0 || deltaY.compareTo(BigDecimal.valueOf(50)) >= 0) {
                    isSteep = true;
                }
            } else if ((p1.y == null && p2.y != null) || (p1.y != null && p2.y == null)) {
                isSteep = true;
                deltaPercent = BigDecimal.valueOf(100.0);
            }

            if (isSteep && finalPoints.size() < MAX_TOTAL_ANALYZE_POINTS) {
                steepSegmentsCount++;
                int pointsToInject = subFactor - 1;
                BigDecimal segmentSpan = p2.x.subtract(p1.x);
                BigDecimal subStep = segmentSpan.divide(BigDecimal.valueOf(subFactor), mc);

                for (int k = 1; k <= pointsToInject; k++) {
                    BigDecimal subX = p1.x.add(subStep.multiply(BigDecimal.valueOf(k), mc), mc);
                    BigDecimal subY = evaluateAt(ast, variable, subX, baseVariables, angleMode, precision);
                    finalPoints.add(new GraphAnalyzeResponse.GraphPointDto(subX, subY));
                    injectedCount++;
                }

                steepRegions.add(new GraphAnalyzeResponse.SteepRegionDto(
                        p1.x, p2.x, deltaY, deltaPercent, pointsToInject
                ));
            }
        }

        PointHolder lastPoint = initialPoints.get(initialPoints.size() - 1);
        finalPoints.add(new GraphAnalyzeResponse.GraphPointDto(lastPoint.x, lastPoint.y));

        return new GraphAnalyzeResponse(
                request.expression(),
                variable,
                startX,
                endX,
                finalPoints.size(),
                injectedCount,
                steepSegmentsCount,
                anomalies.size(),
                finalPoints,
                steepRegions,
                anomalies
        );
    }

    private BigDecimal evaluateAt(Expr ast, String variable, BigDecimal x,
                                  Map<String, BigDecimal> baseVariables, AngleMode angleMode, int precision) {
        EvaluationContext ctx = new EvaluationContext(angleMode, precision);
        ctx.setVariables(baseVariables);
        ctx.setVariable(variable, x);

        try {
            return Evaluator.evaluate(ast, ctx);
        } catch (ExpressionException | ArithmeticException ex) {
            return null;
        }
    }

    private record PointHolder(BigDecimal x, BigDecimal y) {
    }
}
