package com.calcforge.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LoanRequest(
        @NotNull @DecimalMin(value = "0.01", message = "principal must be positive") BigDecimal principal,
        @NotNull @DecimalMin(value = "0", message = "annual rate cannot be negative") BigDecimal annualInterestRatePercent,
        @NotNull @Positive(message = "term must be at least 1 month") Integer termMonths,
        Boolean includeSchedule
) {
}
