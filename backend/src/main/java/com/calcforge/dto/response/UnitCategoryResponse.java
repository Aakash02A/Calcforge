package com.calcforge.dto.response;

import java.util.List;

public record UnitCategoryResponse(
        String category,
        List<UnitResponse> units
) {
}
