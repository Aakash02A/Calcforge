package com.calcforge.engine.unit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public final class PhysicalValue {
    private final BigDecimal value;
    private final UnitDimension dimension;

    public PhysicalValue(BigDecimal value, UnitDimension dimension) {
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
    }

    public static PhysicalValue of(BigDecimal value, UnitDimension dimension) {
        return new PhysicalValue(value, dimension);
    }

    public static PhysicalValue dimensionless(BigDecimal value) {
        return new PhysicalValue(value, UnitDimension.DIMENSIONLESS);
    }

    public BigDecimal getValue() {
        return value;
    }

    public UnitDimension getDimension() {
        return dimension;
    }

    public PhysicalValue add(PhysicalValue other) {
        Objects.requireNonNull(other, "other PhysicalValue must not be null");
        if (!this.dimension.equals(other.dimension)) {
            throw new IllegalArgumentException(
                    "Dimension mismatch: cannot add " + this.dimension + " and " + other.dimension);
        }
        return new PhysicalValue(this.value.add(other.value), this.dimension);
    }

    public PhysicalValue subtract(PhysicalValue other) {
        Objects.requireNonNull(other, "other PhysicalValue must not be null");
        if (!this.dimension.equals(other.dimension)) {
            throw new IllegalArgumentException(
                    "Dimension mismatch: cannot subtract " + this.dimension + " and " + other.dimension);
        }
        return new PhysicalValue(this.value.subtract(other.value), this.dimension);
    }

    public PhysicalValue multiply(PhysicalValue other) {
        Objects.requireNonNull(other, "other PhysicalValue must not be null");
        return new PhysicalValue(this.value.multiply(other.value), this.dimension.multiply(other.dimension));
    }

    public PhysicalValue divide(PhysicalValue other, MathContext mc) {
        Objects.requireNonNull(other, "other PhysicalValue must not be null");
        Objects.requireNonNull(mc, "MathContext must not be null");
        return new PhysicalValue(this.value.divide(other.value, mc), this.dimension.divide(other.dimension));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhysicalValue that = (PhysicalValue) o;
        return this.value.compareTo(that.value) == 0 && Objects.equals(dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.stripTrailingZeros(), dimension);
    }

    @Override
    public String toString() {
        return value.toPlainString() + (dimension.isDimensionless() ? "" : " " + dimension);
    }
}
