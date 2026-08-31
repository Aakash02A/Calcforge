package com.calcforge.engine;

import java.math.BigDecimal;
import java.math.MathContext;

/** Consistent, safe string rendering of {@link BigDecimal} values across the engine and API. */
public final class NumberFormatter {

    private NumberFormatter() {
    }

    /** Always non-scientific ("plain") representation, used inside the trail and for editable numbers. */
    public static String plain(BigDecimal value) {
        String s = value.stripTrailingZeros().toPlainString();
        return "-0".equals(s) ? "0" : s;
    }

    /** Display representation: plain for "normal magnitude" numbers, scientific for very large/small ones. */
    public static String display(BigDecimal value) {
        String s = value.stripTrailingZeros().toString();
        return "-0".equals(s) ? "0" : s;
    }

    /** Engineering notation: mantissa scaled so the exponent is always a multiple of 3 (e.g. 12.345E+3). */
    public static String engineering(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        BigDecimal v = value.stripTrailingZeros();
        int adjustedExponent = v.precision() - v.scale() - 1;
        int engExponent = Math.floorDiv(adjustedExponent, 3) * 3;
        BigDecimal mantissa = v.movePointLeft(engExponent).round(new MathContext(v.precision() + 2));
        String mantissaStr = mantissa.stripTrailingZeros().toPlainString();
        return mantissaStr + "E" + (engExponent >= 0 ? "+" : "") + engExponent;
    }
}
