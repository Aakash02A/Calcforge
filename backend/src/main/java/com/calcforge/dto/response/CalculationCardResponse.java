package com.calcforge.dto.response;

import java.time.Instant;

public record CalculationCardResponse(
        Long id,
        Long workspaceId,
        String label,
        String expression,
        String resultDisplay,
        CalculationTrailDto trail,
        int positionIndex,
        Instant createdAt
) {
}
