package com.calcforge.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

/**
 * Named mathematical constants, stored at 50 significant digits so that results stay
 * accurate even when the user requests high-precision output. Values are rounded down
 * to the caller's {@link MathContext} on lookup.
 */
public final class MathConstants {

    private static final BigDecimal PI =
            new BigDecimal("3.14159265358979323846264338327950288419716939937510");
    private static final BigDecimal E =
            new BigDecimal("2.71828182845904523536028747135266249775724709369995");
    private static final BigDecimal TAU =
            new BigDecimal("6.28318530717958647692528676655900577839433879875021");
    private static final BigDecimal PHI =
            new BigDecimal("1.61803398874989484820458683436563811772030917980576");

    private static final Map<String, BigDecimal> CONSTANTS = Map.of(
            "pi", PI,
            "\u03c0", PI, // literal 'π'
            "e", E,
            "tau", TAU,
            "phi", PHI
    );

    private MathConstants() {
    }

    public static boolean isConstant(String name) {
        return CONSTANTS.containsKey(name.toLowerCase());
    }

    /** Returns the constant's value rounded to {@code mc}, or {@code null} if {@code name} is not a constant. */
    public static BigDecimal resolve(String name, MathContext mc) {
        BigDecimal value = CONSTANTS.get(name.toLowerCase());
        return value == null ? null : value.round(mc);
    }
}
