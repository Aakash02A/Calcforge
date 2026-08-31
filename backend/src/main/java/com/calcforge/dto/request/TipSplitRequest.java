package com.calcforge.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TipSplitRequest(
        @NotNull @DecimalMin(value = "0") BigDecimal billAmount,
        @NotNull @DecimalMin(value = "0") BigDecimal tipPercent,
        @NotNull @Positive Integer numberOfPeople,
        BigDecimal taxPercent
) {
}
