package com.calcforge.dto.response;

import java.util.List;

public record CurrencyRatesResponse(
        boolean live,
        List<UnitResponse> currencies
) {
}
