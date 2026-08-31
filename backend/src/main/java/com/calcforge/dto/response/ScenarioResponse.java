package com.calcforge.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ScenarioResponse(
        Long id,
        Long workspaceId,
        String name,
        Map<String, BigDecimal> variables,
        Instant createdAt,
        Instant updatedAt
) {
}
