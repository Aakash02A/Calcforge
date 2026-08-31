package com.calcforge.controller.local;

import com.calcforge.dto.request.FormulaEvaluateRequest;
import com.calcforge.dto.request.FormulaRequest;
import com.calcforge.dto.response.CalculationResponse;
import com.calcforge.dto.response.FormulaResponse;
import com.calcforge.service.FormulaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/local/workspaces/{workspaceId}/formulas")
@RequiredArgsConstructor
public class FormulaController {

    private final FormulaService formulaService;

    @GetMapping
    public List<FormulaResponse> list(@PathVariable Long workspaceId) {
        return formulaService.list(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormulaResponse create(@PathVariable Long workspaceId, @Valid @RequestBody FormulaRequest request) {
        return formulaService.create(workspaceId, request);
    }

    @GetMapping("/{formulaId}")
    public FormulaResponse get(@PathVariable Long workspaceId, @PathVariable Long formulaId) {
        return formulaService.get(formulaId);
    }

    @PutMapping("/{formulaId}")
    public FormulaResponse update(@PathVariable Long workspaceId, @PathVariable Long formulaId,
                                   @Valid @RequestBody FormulaRequest request) {
        return formulaService.update(formulaId, request);
    }

    @DeleteMapping("/{formulaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long formulaId) {
        formulaService.delete(formulaId);
    }

    @PostMapping("/{formulaId}/evaluate")
    public CalculationResponse evaluate(@PathVariable Long workspaceId, @PathVariable Long formulaId,
                                         @RequestBody FormulaEvaluateRequest request) {
        return formulaService.evaluate(formulaId, request);
    }
}
