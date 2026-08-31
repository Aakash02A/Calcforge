package com.calcforge.dto.response;

public record AiAssistResponse(
        boolean available,
        String answer,
        String model
) {
}
