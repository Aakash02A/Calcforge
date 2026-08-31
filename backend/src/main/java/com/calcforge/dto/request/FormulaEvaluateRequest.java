package com.calcforge.dto.request;

import java.math.BigDecimal;
import java.util.Map;

public record FormulaEvaluateRequest(
        Map<String, BigDecimal> arguments,
        String angleMode,
        Integer precision
) {
}
