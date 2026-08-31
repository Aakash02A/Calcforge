package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UnitConversionRequest(
        @NotBlank String category,
        @NotBlank String fromSymbol,
        @NotBlank String toSymbol,
        @NotNull BigDecimal value
) {
}
