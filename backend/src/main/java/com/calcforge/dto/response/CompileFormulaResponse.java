package com.calcforge.dto.response;

public record CompileFormulaResponse(
        String java,
        String python,
        String rust,
        String blueprintJson
) {}
