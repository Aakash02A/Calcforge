package com.calcforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CalculationResponse(
        String expression,
        BigDecimal resultValue,
        String resultPlain,
        String resultDisplay,
        String angleMode,
        int precision,
        CalculationTrailDto trail,
        Long historyEntryId,
        Instant computedAt
) {
}
