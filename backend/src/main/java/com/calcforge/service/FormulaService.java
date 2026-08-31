package com.calcforge.service;

import com.calcforge.domain.Formula;
import com.calcforge.dto.request.FormulaEvaluateRequest;
import com.calcforge.dto.request.FormulaRequest;
import com.calcforge.dto.response.CalculationResponse;
import com.calcforge.dto.response.FormulaResponse;
import com.calcforge.engine.AngleMode;
import com.calcforge.engine.ExprUtils;
import com.calcforge.engine.MathConstants;
import com.calcforge.engine.Parser;
import com.calcforge.exception.DuplicateResourceException;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.FormulaRepository;
import com.calcforge.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRUD and evaluation for named, reusable formulas (e.g. {@code monthlyPayment}). A
 * formula's "parameters" are whichever variable names it references that are neither
 * built-in constants nor already defined in its workspace - i.e. the values a caller
 * must supply to evaluate it.
 */
@Service
@RequiredArgsConstructor
public class FormulaService {

    private final FormulaRepository formulaRepository;
    private final VariableRepository variableRepository;
    private final WorkspaceService workspaceService;
    private final CalculationService calculationService;

    @Transactional
    public FormulaResponse create(Long workspaceId, FormulaRequest request) {
        workspaceService.getEntity(workspaceId);
        validateName(workspaceId, request.name(), null);
        // Validate the expression parses, so a broken formula is rejected at save time.
        Parser.parse(request.expression());

        Formula formula = Formula.builder()
                .workspaceId(workspaceId)
                .name(request.name())
                .expression(request.expression())
                .description(request.description())
                .build();
        return toResponse(formulaRepository.save(formula));
    }

    public List<FormulaResponse> list(Long workspaceId) {
        return formulaRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(workspaceId)
                .stream().map(this::toResponse).toList();
    }

    public FormulaResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public Formula getEntity(Long id) {
        return formulaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Formula", id));
    }

    @Transactional
    public FormulaResponse update(Long id, FormulaRequest request) {
        Formula formula = getEntity(id);
        validateName(formula.getWorkspaceId(), request.name(), id);
        Parser.parse(request.expression());
        formula.setName(request.name());
        formula.setExpression(request.expression());
        formula.setDescription(request.description());
        return toResponse(formulaRepository.save(formula));
    }

    @Transactional
    public void delete(Long id) {
        Formula formula = getEntity(id);
        formula.setDeletedAt(Instant.now());
        formulaRepository.save(formula);
    }

    /** Evaluates a saved formula, merging its workspace's variables with the caller-supplied arguments (which win). */
    public CalculationResponse evaluate(Long id, FormulaEvaluateRequest request) {
        Formula formula = getEntity(id);
        AngleMode angleMode = calculationService.parseAngleMode(request.angleMode());
        int precision = request.precision() != null ? request.precision() : CalculationService.DEFAULT_PRECISION;

        Map<String, BigDecimal> variables = calculationService.resolveVariables(formula.getWorkspaceId(), request.arguments());

        EvaluationOutcome outcome = calculationService.evaluate(formula.getExpression(), variables, angleMode, precision);
        var trail = calculationService.buildTrail(formula.getName() + " = " + formula.getExpression(), outcome);

        return new CalculationResponse(formula.getExpression(), outcome.getResult(),
                com.calcforge.engine.NumberFormatter.plain(outcome.getResult()),
                com.calcforge.engine.NumberFormatter.display(outcome.getResult()),
                angleMode.name(), precision, trail, null, Instant.now());
    }

    private List<String> computeParameters(Formula formula) {
        Set<String> referenced = ExprUtils.collectVariableNames(Parser.parse(formula.getExpression()));
        return referenced.stream()
                .filter(name -> !MathConstants.isConstant(name))
                .filter(name -> variableRepository
                        .findByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(formula.getWorkspaceId(), name).isEmpty())
                .toList();
    }

    private void validateName(Long workspaceId, String name, Long selfId) {
        formulaRepository.findByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(workspaceId, name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "A formula named '" + name + "' already exists in this workspace");
                });
    }

    private FormulaResponse toResponse(Formula f) {
        return new FormulaResponse(f.getId(), f.getWorkspaceId(), f.getName(), f.getExpression(), f.getDescription(),
                computeParameters(f), f.getCreatedAt(), f.getUpdatedAt());
    }
}
