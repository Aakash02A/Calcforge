package com.calcforge.controller.cloud;

import com.calcforge.config.CloudFeatureProperties;
import com.calcforge.dto.response.CurrencyRatesResponse;
import com.calcforge.dto.response.UnitCategoryResponse;
import com.calcforge.service.UnitConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cloud/currency")
@RequiredArgsConstructor
public class CurrencyController {

    private final UnitConversionService unitConversionService;
    private final CloudFeatureProperties featureProperties;

    /** Currency conversion itself works offline via {@code POST /api/v1/local/units/convert} - this endpoint
     * just reports whether the "currency" category is backed by live or seeded static rates. */
    @GetMapping("/rates")
    public CurrencyRatesResponse rates() {
        List<UnitCategoryResponse> categories = unitConversionService.listCategories();
        var currency = categories.stream().filter(c -> "currency".equals(c.category())).findFirst();
        return new CurrencyRatesResponse(featureProperties.isLiveCurrencyEnabled(),
                currency.map(UnitCategoryResponse::units).orElse(List.of()));
    }
}
