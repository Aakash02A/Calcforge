package com.calcforge.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record GraphResponse(
        String expression,
        String variable,
        BigDecimal min,
        BigDecimal max,
        List<GraphPointDto> points
) {
    public record GraphPointDto(BigDecimal x, BigDecimal y) {
    }
}
