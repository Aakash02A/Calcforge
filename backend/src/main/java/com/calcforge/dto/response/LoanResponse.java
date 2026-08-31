package com.calcforge.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record LoanResponse(
        BigDecimal monthlyPayment,
        BigDecimal totalPayment,
        BigDecimal totalInterest,
        List<AmortizationRowDto> schedule
) {
    public record AmortizationRowDto(
            int period,
            BigDecimal payment,
            BigDecimal principalPortion,
            BigDecimal interestPortion,
            BigDecimal remainingBalance
    ) {
    }
}
