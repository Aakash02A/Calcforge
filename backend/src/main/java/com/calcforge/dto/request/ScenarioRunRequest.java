package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ScenarioRunRequest(
        @NotBlank String expression,
        java.util.List<Long> scenarioIds,
        String angleMode,
        Integer precision
) {
}
