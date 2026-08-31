package com.calcforge.controller.local;

import com.calcforge.dto.request.CalculateRequest;
import com.calcforge.dto.response.CalculationResponse;
import com.calcforge.dto.response.ValidateExpressionResponse;
import com.calcforge.engine.ExprFormatter;
import com.calcforge.engine.ExpressionException;
import com.calcforge.engine.Parser;
import com.calcforge.service.CalculationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/local/calculate")
@RequiredArgsConstructor
public class CalculatorController {

    private final CalculationService calculationService;

    @PostMapping
    public CalculationResponse calculate(@Valid @RequestBody CalculateRequest request) {
        return calculationService.calculate(request);
    }

    /** Cheap syntax check for live-typing feedback in the input bar - does not evaluate or save anything. */
    @PostMapping("/validate")
    public ValidateExpressionResponse validate(@RequestBody CalculateRequest request) {
        try {
            var ast = Parser.parse(request.expression());
            return new ValidateExpressionResponse(true, ExprFormatter.format(ast), null, null);
        } catch (ExpressionException e) {
            return new ValidateExpressionResponse(false, null, e.getErrorCode().name(), e.getMessage());
        }
    }
}
