package com.calcforge.dto.response;

import com.calcforge.engine.anomaly.MathAnomaly;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record GraphAnalyzeResponse(
        String expression,
        String variable,
        BigDecimal startX,
        BigDecimal endX,
        int totalPoints,
        int injectedPoints,
        int steepSegmentsCount,
        int anomaliesCount,
        List<GraphPointDto> points,
        List<SteepRegionDto> steepRegions,
        Map<BigDecimal, MathAnomaly> anomalies
) {
    public record GraphPointDto(BigDecimal x, BigDecimal y) {
    }

    public record SteepRegionDto(
            BigDecimal startX,
            BigDecimal endX,
            BigDecimal deltaY,
            BigDecimal deltaPercent,
            int injectedCount
    ) {
    }
}
