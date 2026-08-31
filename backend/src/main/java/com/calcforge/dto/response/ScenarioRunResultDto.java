package com.calcforge.dto.response;

public record ScenarioRunResultDto(
        Long scenarioId,
        String scenarioName,
        String resultDisplay,
        CalculationTrailDto trail
) {
}
