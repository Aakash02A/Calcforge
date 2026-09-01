package com.calcforge.exception;

import com.calcforge.engine.unit.UnitDimension;

public class DimensionalMismatchException extends Exception {
    private final UnitDimension leftDimension;
    private final UnitDimension rightDimension;
    private final String operation;

    public DimensionalMismatchException(String message, UnitDimension leftDimension, UnitDimension rightDimension, String operation) {
        super(message);
        this.leftDimension = leftDimension;
        this.rightDimension = rightDimension;
        this.operation = operation;
    }

    public UnitDimension getLeftDimension() {
        return leftDimension;
    }

    public UnitDimension getRightDimension() {
        return rightDimension;
    }

    public String getOperation() {
        return operation;
    }
}
