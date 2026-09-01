package com.calcforge.engine.unit;

import java.util.Objects;

public record UnitDimension(
        int mass,
        int length,
        int time,
        int current,
        int temperature,
        int substance,
        int luminosity
) {
    public static final UnitDimension DIMENSIONLESS = new UnitDimension(0, 0, 0, 0, 0, 0, 0);
    public static final UnitDimension MASS = new UnitDimension(1, 0, 0, 0, 0, 0, 0);
    public static final UnitDimension LENGTH = new UnitDimension(0, 1, 0, 0, 0, 0, 0);
    public static final UnitDimension TIME = new UnitDimension(0, 0, 1, 0, 0, 0, 0);
    public static final UnitDimension CURRENT = new UnitDimension(0, 0, 0, 1, 0, 0, 0);
    public static final UnitDimension TEMPERATURE = new UnitDimension(0, 0, 0, 0, 1, 0, 0);
    public static final UnitDimension SUBSTANCE = new UnitDimension(0, 0, 0, 0, 0, 1, 0);
    public static final UnitDimension LUMINOSITY = new UnitDimension(0, 0, 0, 0, 0, 0, 1);

    public boolean isDimensionless() {
        return mass == 0 && length == 0 && time == 0 && current == 0
                && temperature == 0 && substance == 0 && luminosity == 0;
    }

    public UnitDimension multiply(UnitDimension other) {
        Objects.requireNonNull(other, "other UnitDimension must not be null");
        return new UnitDimension(
                this.mass + other.mass,
                this.length + other.length,
                this.time + other.time,
                this.current + other.current,
                this.temperature + other.temperature,
                this.substance + other.substance,
                this.luminosity + other.luminosity
        );
    }

    public UnitDimension divide(UnitDimension other) {
        Objects.requireNonNull(other, "other UnitDimension must not be null");
        return new UnitDimension(
                this.mass - other.mass,
                this.length - other.length,
                this.time - other.time,
                this.current - other.current,
                this.temperature - other.temperature,
                this.substance - other.substance,
                this.luminosity - other.luminosity
        );
    }

    public UnitDimension pow(int exponent) {
        return new UnitDimension(
                this.mass * exponent,
                this.length * exponent,
                this.time * exponent,
                this.current * exponent,
                this.temperature * exponent,
                this.substance * exponent,
                this.luminosity * exponent
        );
    }

    public String toDerivedString() {
        if (isDimensionless()) return "";
        if (this.equals(MASS.multiply(LENGTH).divide(TIME.pow(2)))) return "N";
        if (this.equals(MASS.multiply(LENGTH.pow(2)).divide(TIME.pow(2)))) return "J";
        if (this.equals(MASS.multiply(LENGTH.pow(2)).divide(TIME.pow(3)))) return "W";
        if (this.equals(MASS.divide(LENGTH.multiply(TIME.pow(2))))) return "Pa";
        if (this.equals(TIME.pow(-1))) return "Hz";
        if (this.equals(CURRENT.multiply(TIME))) return "C";
        
        StringBuilder num = new StringBuilder();
        StringBuilder den = new StringBuilder();
        appendDim(num, den, "kg", mass);
        appendDim(num, den, "m", length);
        appendDim(num, den, "s", time);
        appendDim(num, den, "A", current);
        appendDim(num, den, "K", temperature);
        appendDim(num, den, "mol", substance);
        appendDim(num, den, "cd", luminosity);

        if (num.isEmpty() && den.isEmpty()) return "";
        if (den.isEmpty()) return num.toString();
        if (num.isEmpty()) return "1/" + den;
        return num + "/" + den;
    }

    private void appendDim(StringBuilder num, StringBuilder den, String symbol, int exp) {
        if (exp > 0) {
            if (!num.isEmpty()) num.append("*");
            num.append(symbol);
            if (exp > 1) num.append("^").append(exp);
        } else if (exp < 0) {
            if (!den.isEmpty()) den.append("*");
            den.append(symbol);
            if (exp < -1) den.append("^").append(-exp);
        }
    }
}
