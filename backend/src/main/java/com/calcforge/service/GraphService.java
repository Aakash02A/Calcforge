package com.calcforge.service;

import com.calcforge.dto.request.GraphRequest;
import com.calcforge.dto.response.GraphResponse;
import com.calcforge.engine.AngleMode;
import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.Evaluator;
import com.calcforge.engine.ExpressionException;
import com.calcforge.engine.Parser;
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
 * Prepares (x, y) sample points for 2D function graphing. The expression is parsed once
 * and re-evaluated at each sample point with the swept variable rebound - points where
 * the function is undefined (division by zero, domain errors, ...) come back with a null
 * {@code y}, which the frontend renders as a gap in the line rather than an error.
 */
@Service
@RequiredArgsConstructor
public class GraphService {

    private static final int DEFAULT_SAMPLES = 200;
    private static final int MAX_SAMPLES = 2000;
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
                    ? request.max() // avoid drifting past max due to repeated addition of `step`
                    : request.min().add(step.multiply(BigDecimal.valueOf(i)), ROUND_MC);

            EvaluationContext ctx = new EvaluationContext(angleMode, GRAPH_PRECISION);
            ctx.setVariables(baseVariables);
            ctx.setVariable(request.variable(), x);

            BigDecimal y;
            try {
                y = Evaluator.evaluate(ast, ctx);
            } catch (ExpressionException | ArithmeticException ex) {
                y = null; // undefined at this x - rendered as a gap by the frontend
            }
            points.add(new GraphResponse.GraphPointDto(x, y));
        }

        return new GraphResponse(request.expression(), request.variable(), request.min(), request.max(), points);
    }
}
