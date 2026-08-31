package com.calcforge.dto.response;

import java.math.BigDecimal;

public record CompoundInterestResponse(
        BigDecimal futureValue,
        BigDecimal totalPrincipalAndContributions,
        BigDecimal totalInterestEarned
) {
}
