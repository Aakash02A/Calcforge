package com.calcforge.dto.response;

import java.math.BigDecimal;

public record TipSplitResponse(
        BigDecimal billAmount,
        BigDecimal taxAmount,
        BigDecimal tipAmount,
        BigDecimal totalAmount,
        BigDecimal perPersonAmount
) {
}
