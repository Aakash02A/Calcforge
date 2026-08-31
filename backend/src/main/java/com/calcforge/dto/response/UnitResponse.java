package com.calcforge.dto.response;

public record UnitResponse(
        Long id,
        String category,
        String name,
        String symbol,
        boolean baseUnit
) {
}
