package com.calcforge.service;

import com.calcforge.domain.HistoryEntry;
import com.calcforge.domain.Variable;
import com.calcforge.domain.Workspace;
import com.calcforge.dto.request.CalculateRequest;
import com.calcforge.dto.response.CalculationResponse;
import com.calcforge.dto.response.CalculationTrailDto;
import com.calcforge.dto.response.TrailStepDto;
import com.calcforge.engine.AngleMode;
import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.Evaluator;
import com.calcforge.engine.ExprFormatter;
import com.calcforge.engine.ExprUtils;
import com.calcforge.engine.MathConstants;
import com.calcforge.engine.NumberFormatter;
import com.calcforge.engine.Parser;
import com.calcforge.engine.TrailStep;
import com.calcforge.engine.ast.Expr;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.HistoryEntryRepository;
import com.calcforge.repository.VariableRepository;
import com.calcforge.repository.WorkspaceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The heart of CalcForge: parses and evaluates expressions and assembles the mandatory
 * Input -> Assumptions -> Formula -> Computation -> Result trail. Fully deterministic,
 * requires no authentication, and never touches the network.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalculationService {

    public static final int DEFAULT_PRECISION = 20;

    private final HistoryEntryRepository historyEntryRepository;
    private final VariableRepository variableRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;

    /** Full end-to-end flow for the primary "type an expression, get a result" use case. */
    @Transactional
    public CalculationResponse calculate(CalculateRequest request) {
        AngleMode angleMode = parseAngleMode(request.angleMode());
        int precision = request.precision() != null ? request.precision() : DEFAULT_PRECISION;

        Map<String, BigDecimal> variables = resolveVariables(request.workspaceId(), request.variables());

        EvaluationOutcome outcome = evaluate(request.expression(), variables, angleMode, precision);
        CalculationTrailDto trail = buildTrail(request.expression(), outcome);

        Long historyId = null;
        if (request.saveToHistory() == null || request.saveToHistory()) {
            historyId = saveHistory(request.expression(), outcome.getResult(), trail, request.tags(), null, request.workspaceId());
        }

        return new CalculationResponse(
                request.expression(),
                outcome.getResult(),
                NumberFormatter.plain(outcome.getResult()),
                NumberFormatter.display(outcome.getResult()),
                angleMode.name(),
                precision,
                trail,
                historyId,
                Instant.now());
    }

    /** Parses and evaluates a raw expression against a variable map. No persistence, no trail formatting. */
    public EvaluationOutcome evaluate(String expression, Map<String, BigDecimal> variables, AngleMode angleMode, int precision) {
        Expr ast = Parser.parse(expression);
        EvaluationContext ctx = new EvaluationContext(angleMode, precision);
        if (variables != null) {
            ctx.setVariables(variables);
        }
        BigDecimal result = Evaluator.evaluate(ast, ctx);
        return new EvaluationOutcome(ast, result, ctx);
    }

    /** Merges a workspace's saved variables (if any) with inline overrides, overrides taking precedence. */
    public Map<String, BigDecimal> resolveVariables(Long workspaceId, Map<String, BigDecimal> inlineOverrides) {
        Map<String, BigDecimal> variables = new HashMap<>();
        if (workspaceId != null) {
            Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Workspace", workspaceId));
            for (Variable v : variableRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(workspace.getId())) {
                variables.put(v.getName().toLowerCase(), v.getValue());
            }
        }
        if (inlineOverrides != null) {
            inlineOverrides.forEach((k, v) -> variables.put(k.toLowerCase(), v));
        }
        return variables;
    }

    /** Assembles the full five-stage trail from a raw input string and the outcome of evaluating it. */
    public CalculationTrailDto buildTrail(String rawInput, EvaluationOutcome outcome) {
        EvaluationContext ctx = outcome.getContext();
        BigDecimal result = outcome.getResult();
        List<TrailStepDto> steps = new ArrayList<>();

        steps.add(new TrailStepDto("INPUT", "Input", rawInput, null, null));

        steps.add(new TrailStepDto("ASSUMPTIONS", "Angle mode", null, ctx.getAngleMode().name(),
                "Applies to trigonometric functions"));
        steps.add(new TrailStepDto("ASSUMPTIONS", "Precision", null, ctx.getPrecision() + " significant digits", null));

        Set<String> referenced = ExprUtils.collectVariableNames(outcome.getAst());
        for (String name : referenced) {
            if (MathConstants.isConstant(name)) {
                BigDecimal constantValue = MathConstants.resolve(name, ctx.getMathContext());
                steps.add(new TrailStepDto("ASSUMPTIONS", "Constant " + name, null,
                        NumberFormatter.plain(constantValue), "Built-in constant"));
            } else if (ctx.hasVariable(name)) {
                steps.add(new TrailStepDto("ASSUMPTIONS", "Variable " + name, null,
                        NumberFormatter.plain(ctx.lookupVariable(name)), null));
            }
        }

        steps.add(new TrailStepDto("FORMULA", "Normalized formula", ExprFormatter.format(outcome.getAst()), null,
                "Implicit multiplication and operator precedence made explicit"));

        List<TrailStep> computationSteps = ctx.getTrail();
        if (computationSteps.isEmpty()) {
            steps.add(new TrailStepDto("COMPUTATION", "Direct value", rawInput,
                    NumberFormatter.plain(result), "No further reduction needed"));
        } else {
            for (TrailStep s : computationSteps) {
                steps.add(new TrailStepDto(s.getStage().name(), s.getTitle(), s.getExpression(), s.getValue(), s.getNote()));
            }
        }

        steps.add(new TrailStepDto("RESULT", "Result", null, NumberFormatter.display(result), null));

        return new CalculationTrailDto(steps);
    }

    @Transactional
    public Long saveHistory(String expression, BigDecimal result, CalculationTrailDto trail, String tags,
                             Long userId, Long workspaceId) {
        HistoryEntry entry = HistoryEntry.builder()
                .userId(userId)
                .workspaceId(workspaceId)
                .expression(expression)
                .result(NumberFormatter.display(result))
                .trailJson(serializeTrail(trail))
                .tags(tags)
                .build();
        return historyEntryRepository.save(entry).getId();
    }

    public AngleMode parseAngleMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return AngleMode.DEGREES;
        }
        try {
            return AngleMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "angleMode must be one of DEGREES, RADIANS, GRADIANS (got '" + raw + "')");
        }
    }

    public String serializeTrail(CalculationTrailDto trail) {
        try {
            return objectMapper.writeValueAsString(trail);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize calculation trail, storing empty trail", e);
            return "{\"steps\":[]}";
        }
    }

    public CalculationTrailDto deserializeTrail(String json) {
        if (json == null || json.isBlank()) {
            return new CalculationTrailDto(List.of());
        }
        try {
            return objectMapper.readValue(json, CalculationTrailDto.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize stored calculation trail", e);
            return new CalculationTrailDto(List.of());
        }
    }
}
