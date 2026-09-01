package com.calcforge.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.Map;

public record GraphAnalyzeRequest(
        @NotBlank String expression,
        String variable,
        @JsonProperty("startX") BigDecimal startX,
        @JsonProperty("min") BigDecimal min,
        @JsonProperty("endX") BigDecimal endX,
        @JsonProperty("max") BigDecimal max,
        Map<String, BigDecimal> variables,
        Long workspaceId,
        String angleMode,
        Integer precision,
        BigDecimal thresholdPercentage,
        Integer baseSamples,
        Integer subdivisionFactor
) {
    public String effectiveVariable() {
        return (variable != null && !variable.isBlank()) ? variable.trim() : "x";
    }

    public BigDecimal effectiveStartX() {
        if (startX != null) return startX;
        if (min != null) return min;
        return BigDecimal.valueOf(-10);
    }

    public BigDecimal effectiveEndX() {
        if (endX != null) return endX;
        if (max != null) return max;
        return BigDecimal.valueOf(10);
    }

    public int effectivePrecision() {
        return (precision != null && precision >= 4 && precision <= 50) ? precision : 15;
    }

    public BigDecimal effectiveThresholdPercentage() {
        return (thresholdPercentage != null && thresholdPercentage.compareTo(BigDecimal.ZERO) > 0)
                ? thresholdPercentage
                : BigDecimal.valueOf(10.0);
    }

    public int effectiveBaseSamples() {
        return (baseSamples != null && baseSamples >= 10 && baseSamples <= 500) ? baseSamples : 100;
    }

    public int effectiveSubdivisionFactor() {
        return (subdivisionFactor != null && subdivisionFactor >= 2 && subdivisionFactor <= 20) ? subdivisionFactor : 10;
    }
}
