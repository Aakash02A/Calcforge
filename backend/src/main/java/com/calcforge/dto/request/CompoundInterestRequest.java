package com.calcforge.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CompoundInterestRequest(
        @NotNull @DecimalMin(value = "0", message = "principal cannot be negative") BigDecimal principal,
        @NotNull @DecimalMin(value = "0", message = "annual rate cannot be negative") BigDecimal annualInterestRatePercent,
        @NotNull @Positive(message = "compounding periods per year must be positive") Integer compoundsPerYear,
        @NotNull @Positive(message = "years must be positive") BigDecimal years,
        BigDecimal contributionPerPeriod
) {
}
