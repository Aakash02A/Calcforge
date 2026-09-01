package com.calcforge.controller.local;

import com.calcforge.dto.request.CalculateRequest;
import com.calcforge.dto.response.CalculationResponse;
import com.calcforge.dto.response.ValidateExpressionResponse;
import com.calcforge.engine.ExprFormatter;
import com.calcforge.engine.ExpressionException;
import com.calcforge.engine.Parser;
import com.calcforge.service.CalculationService;
import com.calcforge.dto.response.CompileFormulaResponse;
import com.calcforge.engine.blueprint.AstBlueprintSerializer;
import com.calcforge.service.FormulaCompilerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/local/calculate")
@RequiredArgsConstructor
public class CalculatorController {

    private final CalculationService calculationService;
    private final FormulaCompilerService formulaCompilerService;

    @PostMapping
    public CalculationResponse calculate(@Valid @RequestBody CalculateRequest request) {
        return calculationService.calculate(request);
    }

    @PostMapping("/compile")
    public CompileFormulaResponse compile(@Valid @RequestBody CalculateRequest request) {
        var ast = Parser.parse(request.expression());
        var blueprint = AstBlueprintSerializer.serialize(ast);
        String python = formulaCompilerService.compileToPython(blueprint);
        String java = formulaCompilerService.compileToJava(blueprint);
        String rust = formulaCompilerService.compileToRust(blueprint);
        String json = AstBlueprintSerializer.toJson(ast);
        return new CompileFormulaResponse(java, python, rust, json);
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
