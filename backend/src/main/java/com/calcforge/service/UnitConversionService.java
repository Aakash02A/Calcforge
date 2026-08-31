package com.calcforge.service;

import com.calcforge.domain.Unit;
import com.calcforge.dto.request.UnitConversionRequest;
import com.calcforge.dto.response.CalculationTrailDto;
import com.calcforge.dto.response.TrailStepDto;
import com.calcforge.dto.response.UnitCategoryResponse;
import com.calcforge.dto.response.UnitConversionResponse;
import com.calcforge.dto.response.UnitResponse;
import com.calcforge.engine.NumberFormatter;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts between units within a category using each unit's affine transform to that
 * category's base unit: {@code base = value * toBaseFactor + toBaseOffset}. Entirely
 * table-driven from the offline {@code units} table seeded by Flyway - no network calls,
 * unlike live currency rates (which are a strictly optional cloud feature).
 */
@Service
@RequiredArgsConstructor
public class UnitConversionService {

    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_UP);

    private final UnitRepository unitRepository;

    public List<UnitCategoryResponse> listCategories() {
        List<UnitCategoryResponse> categories = new ArrayList<>();
        for (String category : unitRepository.findAllCategories()) {
            List<UnitResponse> units = unitRepository.findAllByCategoryOrderBySortOrderAsc(category).stream()
                    .map(u -> new UnitResponse(u.getId(), u.getCategory(), u.getName(), u.getSymbol(), u.isBaseUnit()))
                    .toList();
            categories.add(new UnitCategoryResponse(category, units));
        }
        return categories;
    }

    public UnitConversionResponse convert(UnitConversionRequest request) {
        Unit fromUnit = findUnit(request.category(), request.fromSymbol());
        Unit toUnit = findUnit(request.category(), request.toSymbol());

        BigDecimal baseValue = request.value().multiply(fromUnit.getToBaseFactor(), MC).add(fromUnit.getToBaseOffset(), MC);
        if (toUnit.getToBaseFactor().signum() == 0) {
            throw new IllegalStateException("Unit " + toUnit.getSymbol() + " has an invalid conversion factor");
        }
        BigDecimal targetValue = baseValue.subtract(toUnit.getToBaseOffset(), MC).divide(toUnit.getToBaseFactor(), MC);

        CalculationTrailDto trail = buildTrail(request, fromUnit, toUnit, baseValue, targetValue);

        return new UnitConversionResponse(request.category(), request.value(), fromUnit.getSymbol(),
                targetValue, NumberFormatter.display(targetValue), toUnit.getSymbol(), trail);
    }

    private Unit findUnit(String category, String symbol) {
        return unitRepository.findByCategoryAndSymbol(category, symbol)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No unit '" + symbol + "' in category '" + category + "'"));
    }

    private CalculationTrailDto buildTrail(UnitConversionRequest req, Unit from, Unit to,
                                            BigDecimal baseValue, BigDecimal targetValue) {
        List<TrailStepDto> steps = new ArrayList<>();
        steps.add(new TrailStepDto("INPUT", "Input",
                NumberFormatter.plain(req.value()) + " " + from.getSymbol() + " -> " + to.getSymbol(), null, null));
        steps.add(new TrailStepDto("ASSUMPTIONS", "Category", null, req.category(), null));
        steps.add(new TrailStepDto("ASSUMPTIONS", "Base unit", null,
                from.isBaseUnit() ? from.getSymbol() : (to.isBaseUnit() ? to.getSymbol() : req.category() + " base"), null));
        steps.add(new TrailStepDto("FORMULA", "Conversion formula",
                "base = value * " + NumberFormatter.plain(from.getToBaseFactor()) +
                        (from.getToBaseOffset().signum() != 0 ? " + " + NumberFormatter.plain(from.getToBaseOffset()) : "") +
                        "; result = (base - " + NumberFormatter.plain(to.getToBaseOffset()) + ") / " +
                        NumberFormatter.plain(to.getToBaseFactor()),
                null, null));
        steps.add(new TrailStepDto("COMPUTATION", "Step 1: Convert to base unit",
                NumberFormatter.plain(req.value()) + " " + from.getSymbol(), NumberFormatter.plain(baseValue), null));
        steps.add(new TrailStepDto("COMPUTATION", "Step 2: Convert from base unit",
                NumberFormatter.plain(baseValue), NumberFormatter.plain(targetValue), null));
        steps.add(new TrailStepDto("RESULT", "Result", null, NumberFormatter.display(targetValue) + " " + to.getSymbol(), null));
        return new CalculationTrailDto(steps);
    }
}
