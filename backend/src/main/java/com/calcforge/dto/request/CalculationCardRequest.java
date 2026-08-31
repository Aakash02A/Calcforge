package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record CalculationCardRequest(
        @NotBlank @Size(max = 2000) String expression,
        @Size(max = 255) String label,
        Map<String, BigDecimal> variables,
        String angleMode,
        Integer precision,
        Integer positionIndex
) {
}
