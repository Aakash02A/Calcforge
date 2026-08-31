package com.calcforge.dto.response;

import java.math.BigDecimal;

public record NpvResponse(
        BigDecimal netPresentValue,
        BigDecimal discountRatePercent
) {
}
