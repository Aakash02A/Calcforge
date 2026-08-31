package com.calcforge.dto.response;

import java.util.List;

public record SharedWorkspaceResponse(
        WorkspaceResponse workspace,
        List<CalculationCardResponse> calculations,
        List<VariableResponse> variables,
        List<FormulaResponse> formulas
) {
}
