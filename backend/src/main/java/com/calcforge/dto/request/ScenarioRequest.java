package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record ScenarioRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull Map<String, BigDecimal> variables
) {
}
