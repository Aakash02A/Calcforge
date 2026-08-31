package com.calcforge.dto.response;

import java.util.List;

public record CalculationTrailDto(
        List<TrailStepDto> steps
) {
}
