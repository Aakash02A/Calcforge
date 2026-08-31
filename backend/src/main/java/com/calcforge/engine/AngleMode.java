package com.calcforge.engine;

/** Determines how trigonometric function arguments/results are interpreted. */
public enum AngleMode {
    DEGREES,
    RADIANS,
    GRADIANS;

    /** Converts a raw angle value in this mode to radians (for use with {@code java.lang.Math}). */
    public double toRadians(double value) {
        return switch (this) {
            case DEGREES -> Math.toRadians(value);
            case GRADIANS -> value * (Math.PI / 200.0);
            case RADIANS -> value;
        };
    }

    /** Converts a radians value (as produced by {@code java.lang.Math} inverse trig functions) to this mode. */
    public double fromRadians(double radians) {
        return switch (this) {
            case DEGREES -> Math.toDegrees(radians);
            case GRADIANS -> radians * (200.0 / Math.PI);
            case RADIANS -> radians;
        };
    }
}
