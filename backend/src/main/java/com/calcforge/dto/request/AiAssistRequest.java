package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiAssistRequest(
        @NotBlank @Size(max = 2000) String question,
        Long workspaceId
) {
}
