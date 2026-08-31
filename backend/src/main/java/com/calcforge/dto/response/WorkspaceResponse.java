package com.calcforge.dto.response;

import java.time.Instant;

public record WorkspaceResponse(
        Long id,
        Long userId,
        String name,
        String description,
        boolean shared,
        long calculationCount,
        long variableCount,
        long formulaCount,
        Instant createdAt,
        Instant updatedAt
) {
}
