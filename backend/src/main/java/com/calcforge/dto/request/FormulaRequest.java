package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FormulaRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]*$", message = "name must start with a letter or underscore and contain only letters, digits, underscores")
        String name,
        @NotBlank @Size(max = 2000) String expression,
        @Size(max = 1000) String description
) {
}
