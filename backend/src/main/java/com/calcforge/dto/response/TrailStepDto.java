package com.calcforge.dto.response;

public record TrailStepDto(
        String stage,
        String title,
        String expression,
        String value,
        String note
) {
}
