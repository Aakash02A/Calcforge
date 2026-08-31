package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record CalculateRequest(
        @NotBlank(message = "expression must not be blank")
        @Size(max = 2000, message = "expression is too long (max 2000 characters)")
        String expression,
        Map<String, BigDecimal> variables,
        Long workspaceId,
        String angleMode,
        Integer precision,
        Boolean saveToHistory,
        String tags
) {
}
