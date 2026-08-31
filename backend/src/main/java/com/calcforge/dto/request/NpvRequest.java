package com.calcforge.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record NpvRequest(
        @NotNull BigDecimal discountRatePercent,
        @NotEmpty List<BigDecimal> cashFlows
) {
}
