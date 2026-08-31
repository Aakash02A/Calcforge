package com.calcforge.dto.response;

import java.time.Instant;
import java.util.List;

public record HistoryEntryResponse(
        Long id,
        String expression,
        String result,
        CalculationTrailDto trail,
        List<String> tags,
        boolean favorite,
        Instant createdAt
) {
}
