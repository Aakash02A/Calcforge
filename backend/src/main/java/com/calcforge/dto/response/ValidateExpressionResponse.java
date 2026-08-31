package com.calcforge.dto.response;

public record ValidateExpressionResponse(boolean valid, String normalized, String errorCode, String message) {
}
