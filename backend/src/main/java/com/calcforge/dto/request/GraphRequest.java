package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record GraphRequest(
        @NotBlank String expression,
        @NotBlank String variable,
        @NotNull BigDecimal min,
        @NotNull BigDecimal max,
        Integer samples,
        Map<String, BigDecimal> variables,
        Long workspaceId,
        String angleMode
) {
}
