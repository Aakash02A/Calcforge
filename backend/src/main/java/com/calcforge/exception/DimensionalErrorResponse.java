package com.calcforge.exception;

import com.calcforge.engine.unit.UnitDimension;

import java.time.Instant;

public record DimensionalErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        String operation,
        UnitDimension leftDimension,
        UnitDimension rightDimension
) {}
