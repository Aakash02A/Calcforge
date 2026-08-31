package com.calcforge.dto.response;

import java.time.Instant;
import java.util.List;

public record FormulaResponse(
        Long id,
        Long workspaceId,
        String name,
        String expression,
        String description,
        List<String> parameters,
        Instant createdAt,
        Instant updatedAt
) {
}
