package com.calcforge.service;

import com.calcforge.domain.Calculation;
import com.calcforge.dto.request.CalculationCardRequest;
import com.calcforge.dto.response.CalculationCardResponse;
import com.calcforge.dto.response.CalculationTrailDto;
import com.calcforge.engine.AngleMode;
import com.calcforge.engine.NumberFormatter;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.CalculationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A workspace "canvas" is simply an ordered list of {@link Calculation} cards. Each card
 * is a full, independently re-evaluated expression - this is what enables side-by-side
 * comparison: change one card's inputs and the others are untouched.
 */
@Service
@RequiredArgsConstructor
public class CalculationCardService {

    private final CalculationRepository calculationRepository;
    private final CalculationService calculationService;
    private final WorkspaceService workspaceService;

    @Transactional
    public CalculationCardResponse create(Long workspaceId, CalculationCardRequest request) {
        workspaceService.getEntity(workspaceId);
        AngleMode angleMode = calculationService.parseAngleMode(request.angleMode());
        int precision = request.precision() != null ? request.precision() : CalculationService.DEFAULT_PRECISION;

        Map<String, BigDecimal> variables = calculationService.resolveVariables(workspaceId, request.variables());
        EvaluationOutcome outcome = calculationService.evaluate(request.expression(), variables, angleMode, precision);
        CalculationTrailDto trail = calculationService.buildTrail(request.expression(), outcome);

        int position = request.positionIndex() != null
                ? request.positionIndex()
                : (int) calculationRepository.countByWorkspaceIdAndDeletedAtIsNull(workspaceId);

        Calculation card = Calculation.builder()
                .workspaceId(workspaceId)
                .label(request.label())
                .expression(request.expression())
                .result(NumberFormatter.display(outcome.getResult()))
                .trailJson(calculationService.serializeTrail(trail))
                .positionIndex(position)
                .build();

        return toResponse(calculationRepository.save(card), trail);
    }

    public List<CalculationCardResponse> list(Long workspaceId) {
        return calculationRepository
                .findAllByWorkspaceIdAndDeletedAtIsNullOrderByPositionIndexAscCreatedAtAsc(workspaceId)
                .stream()
                .map(c -> toResponse(c, calculationService.deserializeTrail(c.getTrailJson())))
                .toList();
    }

    public CalculationCardResponse get(Long id) {
        Calculation card = getEntity(id);
        return toResponse(card, calculationService.deserializeTrail(card.getTrailJson()));
    }

    private Calculation getEntity(Long id) {
        return calculationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Calculation card", id));
    }

    /** Re-evaluates the card with a (possibly) new expression/variables/label/position. */
    @Transactional
    public CalculationCardResponse update(Long id, CalculationCardRequest request) {
        Calculation card = getEntity(id);
        AngleMode angleMode = calculationService.parseAngleMode(request.angleMode());
        int precision = request.precision() != null ? request.precision() : CalculationService.DEFAULT_PRECISION;

        Map<String, BigDecimal> variables = calculationService.resolveVariables(card.getWorkspaceId(), request.variables());
        EvaluationOutcome outcome = calculationService.evaluate(request.expression(), variables, angleMode, precision);
        CalculationTrailDto trail = calculationService.buildTrail(request.expression(), outcome);

        card.setLabel(request.label());
        card.setExpression(request.expression());
        card.setResult(NumberFormatter.display(outcome.getResult()));
        card.setTrailJson(calculationService.serializeTrail(trail));
        if (request.positionIndex() != null) {
            card.setPositionIndex(request.positionIndex());
        }
        return toResponse(calculationRepository.save(card), trail);
    }

    @Transactional
    public void delete(Long id) {
        Calculation card = getEntity(id);
        card.setDeletedAt(Instant.now());
        calculationRepository.save(card);
    }

    /** Persists a new top-to-bottom (or left-to-right) card order for the canvas. */
    @Transactional
    public void reorder(Long workspaceId, List<Long> orderedCardIds) {
        for (int i = 0; i < orderedCardIds.size(); i++) {
            Calculation card = getEntity(orderedCardIds.get(i));
            if (!card.getWorkspaceId().equals(workspaceId)) {
                throw new IllegalArgumentException("Card " + card.getId() + " does not belong to workspace " + workspaceId);
            }
            card.setPositionIndex(i);
            calculationRepository.save(card);
        }
    }

    private CalculationCardResponse toResponse(Calculation c, CalculationTrailDto trail) {
        return new CalculationCardResponse(c.getId(), c.getWorkspaceId(), c.getLabel(), c.getExpression(),
                c.getResult(), trail, c.getPositionIndex(), c.getCreatedAt());
    }
}
