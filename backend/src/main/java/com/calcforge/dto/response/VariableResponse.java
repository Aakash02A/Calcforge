package com.calcforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record VariableResponse(
        Long id,
        Long workspaceId,
        String name,
        BigDecimal value,
        String unit,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
