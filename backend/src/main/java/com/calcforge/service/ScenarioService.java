package com.calcforge.service;

import com.calcforge.domain.Scenario;
import com.calcforge.dto.request.ScenarioRequest;
import com.calcforge.dto.request.ScenarioRunRequest;
import com.calcforge.dto.response.ScenarioResponse;
import com.calcforge.dto.response.ScenarioRunResultDto;
import com.calcforge.engine.AngleMode;
import com.calcforge.engine.NumberFormatter;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.ScenarioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What-if scenarios: a named set of variable overrides that can be applied on top of a
 * workspace's normal variables to see how a result changes, without touching the
 * workspace's real variables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final WorkspaceService workspaceService;
    private final CalculationService calculationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ScenarioResponse create(Long workspaceId, ScenarioRequest request) {
        workspaceService.getEntity(workspaceId);
        Scenario scenario = Scenario.builder()
                .workspaceId(workspaceId)
                .name(request.name())
                .variablesJson(writeVariables(request.variables()))
                .build();
        return toResponse(scenarioRepository.save(scenario));
    }

    public List<ScenarioResponse> list(Long workspaceId) {
        return scenarioRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId)
                .stream().map(this::toResponse).toList();
    }

    public ScenarioResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    private Scenario getEntity(Long id) {
        return scenarioRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Scenario", id));
    }

    @Transactional
    public ScenarioResponse update(Long id, ScenarioRequest request) {
        Scenario scenario = getEntity(id);
        scenario.setName(request.name());
        scenario.setVariablesJson(writeVariables(request.variables()));
        return toResponse(scenarioRepository.save(scenario));
    }

    @Transactional
    public void delete(Long id) {
        Scenario scenario = getEntity(id);
        scenario.setDeletedAt(Instant.now());
        scenarioRepository.save(scenario);
    }

    /** Evaluates {@code expression} once per requested scenario, each with its overrides applied on top of the workspace. */
    public List<ScenarioRunResultDto> run(Long workspaceId, ScenarioRunRequest request) {
        AngleMode angleMode = calculationService.parseAngleMode(request.angleMode());
        int precision = request.precision() != null ? request.precision() : CalculationService.DEFAULT_PRECISION;

        List<ScenarioRunResultDto> results = new java.util.ArrayList<>();

        // Baseline: workspace variables, no scenario overrides.
        Map<String, BigDecimal> baseline = calculationService.resolveVariables(workspaceId, null);
        EvaluationOutcome baseOutcome = calculationService.evaluate(request.expression(), baseline, angleMode, precision);
        results.add(new ScenarioRunResultDto(null, "Baseline",
                NumberFormatter.display(baseOutcome.getResult()),
                calculationService.buildTrail(request.expression(), baseOutcome)));

        if (request.scenarioIds() != null) {
            for (Long scenarioId : request.scenarioIds()) {
                Scenario scenario = getEntity(scenarioId);
                Map<String, BigDecimal> overrides = readVariables(scenario.getVariablesJson());
                Map<String, BigDecimal> merged = calculationService.resolveVariables(workspaceId, overrides);
                EvaluationOutcome outcome = calculationService.evaluate(request.expression(), merged, angleMode, precision);
                results.add(new ScenarioRunResultDto(scenario.getId(), scenario.getName(),
                        NumberFormatter.display(outcome.getResult()),
                        calculationService.buildTrail(request.expression(), outcome)));
            }
        }
        return results;
    }

    private String writeVariables(Map<String, BigDecimal> variables) {
        try {
            return objectMapper.writeValueAsString(variables == null ? Map.of() : variables);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize scenario variables", e);
            return "{}";
        }
    }

    private Map<String, BigDecimal> readVariables(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, BigDecimal>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize scenario variables", e);
            return new HashMap<>();
        }
    }

    private ScenarioResponse toResponse(Scenario s) {
        return new ScenarioResponse(s.getId(), s.getWorkspaceId(), s.getName(), readVariables(s.getVariablesJson()),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
