package com.calcforge.controller.local;

import com.calcforge.dto.request.UnitConversionRequest;
import com.calcforge.dto.response.UnitCategoryResponse;
import com.calcforge.dto.response.UnitConversionResponse;
import com.calcforge.service.UnitConversionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/local/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitConversionService unitConversionService;

    @GetMapping("/categories")
    public List<UnitCategoryResponse> categories() {
        return unitConversionService.listCategories();
    }

    @PostMapping("/convert")
    public UnitConversionResponse convert(@Valid @RequestBody UnitConversionRequest request) {
        return unitConversionService.convert(request);
    }
}
