package com.calcforge.dto.response;

import java.math.BigDecimal;

public record UnitConversionResponse(
        String category,
        BigDecimal fromValue,
        String fromSymbol,
        BigDecimal toValue,
        String toValueDisplay,
        String toSymbol,
        CalculationTrailDto trail
) {
}
