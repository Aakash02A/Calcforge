package com.calcforge.engine;

import com.calcforge.engine.ExpressionException.ErrorCode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * The library of named functions callable from an expression (e.g. {@code sin(x)}).
 *
 * <p><b>Precision model:</b> the four basic operations, integer powers, {@code sqrt},
 * {@code abs}, {@code floor}, {@code ceil} and {@code round} are computed with exact or
 * arbitrary-precision {@link BigDecimal} arithmetic, honoring the caller's requested
 * precision (up to 50 significant digits). Transcendental functions (trig, logs, exp,
 * non-integer powers/roots) are computed in IEEE-754 double precision (~15-17 significant
 * digits) because arbitrary-precision transcendental math would require a full
 * Taylor/CORDIC implementation; this is a deliberate, documented trade-off, not an
 * oversight, and the trail never claims more precision than a function actually delivers.</p>
 */
public final class MathFunctions {

    private MathFunctions() {
    }

    /** Maximum n for which n! (and combinatorics built on it) will be computed, to bound resource usage. */
    private static final int MAX_FACTORIAL_N = 5000;

    private static final Set<String> ONE_ARG = Set.of(
            "sin", "cos", "tan", "asin", "acos", "atan",
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
            "csc", "sec", "cot",
            "sqrt", "cbrt", "ln", "log", "log10", "log2", "exp",
            "abs", "floor", "ceil", "round", "sign", "fact"
    );

    private static final Set<String> TWO_ARG = Set.of(
            "pow", "root", "logb", "mod", "gcd", "lcm", "ncr", "npr", "hypot"
    );

    /** Functions that accept one or more arguments (checked separately from the fixed-arity sets). */
    private static final Set<String> VARIADIC = Set.of("min", "max", "avg", "mean", "sum");

    public static boolean isKnownFunction(String name) {
        String n = name.toLowerCase();
        return ONE_ARG.contains(n) || TWO_ARG.contains(n) || VARIADIC.contains(n);
    }

    public static BigDecimal apply(String rawName, List<BigDecimal> args, EvaluationContext ctx) {
        String name = rawName.toLowerCase();
        MathContext mc = ctx.getMathContext();

        if (VARIADIC.contains(name)) {
            requireAtLeast(name, args, 1);
            return applyVariadic(name, args, mc);
        }
        if (ONE_ARG.contains(name)) {
            requireExactly(name, args, 1);
            return applyOneArg(name, args.get(0), ctx);
        }
        if (TWO_ARG.contains(name)) {
            requireExactly(name, args, 2);
            return applyTwoArg(name, args.get(0), args.get(1), ctx);
        }
        throw new ExpressionException(ErrorCode.UNKNOWN_FUNCTION, "Unknown function '" + rawName + "'");
    }

    // ---------------------------------------------------------------- one-arg

    private static BigDecimal applyOneArg(String name, BigDecimal x, EvaluationContext ctx) {
        MathContext mc = ctx.getMathContext();
        AngleMode mode = ctx.getAngleMode();

        return switch (name) {
            case "sin" -> fromDouble(Math.sin(mode.toRadians(x.doubleValue())), mc);
            case "cos" -> fromDouble(Math.cos(mode.toRadians(x.doubleValue())), mc);
            case "tan" -> fromDouble(Math.tan(mode.toRadians(x.doubleValue())), mc);
            case "csc" -> fromDouble(1.0 / Math.sin(mode.toRadians(x.doubleValue())), mc);
            case "sec" -> fromDouble(1.0 / Math.cos(mode.toRadians(x.doubleValue())), mc);
            case "cot" -> fromDouble(1.0 / Math.tan(mode.toRadians(x.doubleValue())), mc);
            case "asin" -> {
                requireDomain(name, x.compareTo(BigDecimal.ONE.negate()) >= 0 && x.compareTo(BigDecimal.ONE) <= 0,
                        "asin is only defined for -1 <= x <= 1");
                yield fromDouble(mode.fromRadians(Math.asin(x.doubleValue())), mc);
            }
            case "acos" -> {
                requireDomain(name, x.compareTo(BigDecimal.ONE.negate()) >= 0 && x.compareTo(BigDecimal.ONE) <= 0,
                        "acos is only defined for -1 <= x <= 1");
                yield fromDouble(mode.fromRadians(Math.acos(x.doubleValue())), mc);
            }
            case "atan" -> fromDouble(mode.fromRadians(Math.atan(x.doubleValue())), mc);
            case "sinh" -> fromDouble(Math.sinh(x.doubleValue()), mc);
            case "cosh" -> fromDouble(Math.cosh(x.doubleValue()), mc);
            case "tanh" -> fromDouble(Math.tanh(x.doubleValue()), mc);
            case "asinh" -> {
                double d = x.doubleValue();
                yield fromDouble(Math.log(d + Math.sqrt(d * d + 1)), mc);
            }
            case "acosh" -> {
                requireDomain(name, x.compareTo(BigDecimal.ONE) >= 0, "acosh is only defined for x >= 1");
                double d = x.doubleValue();
                yield fromDouble(Math.log(d + Math.sqrt(d * d - 1)), mc);
            }
            case "atanh" -> {
                requireDomain(name, x.compareTo(BigDecimal.ONE.negate()) > 0 && x.compareTo(BigDecimal.ONE) < 0,
                        "atanh is only defined for -1 < x < 1");
                double d = x.doubleValue();
                yield fromDouble(0.5 * Math.log((1 + d) / (1 - d)), mc);
            }
            case "sqrt" -> {
                requireDomain(name, x.signum() >= 0, "sqrt is not defined for negative numbers");
                yield x.sqrt(mc);
            }
            case "cbrt" -> fromDouble(Math.cbrt(x.doubleValue()), mc);
            case "ln" -> {
                requireDomain(name, x.signum() > 0, "ln is only defined for x > 0");
                yield fromDouble(Math.log(x.doubleValue()), mc);
            }
            case "log", "log10" -> {
                requireDomain(name, x.signum() > 0, "log is only defined for x > 0");
                yield fromDouble(Math.log10(x.doubleValue()), mc);
            }
            case "log2" -> {
                requireDomain(name, x.signum() > 0, "log2 is only defined for x > 0");
                yield fromDouble(Math.log(x.doubleValue()) / Math.log(2), mc);
            }
            case "exp" -> fromDouble(Math.exp(x.doubleValue()), mc);
            case "abs" -> x.abs(mc);
            case "floor" -> x.setScale(0, RoundingMode.FLOOR);
            case "ceil" -> x.setScale(0, RoundingMode.CEILING);
            case "round" -> x.setScale(0, RoundingMode.HALF_UP);
            case "sign" -> BigDecimal.valueOf(x.signum());
            case "fact" -> factorial(x);
            default -> throw new ExpressionException(ErrorCode.UNKNOWN_FUNCTION, "Unknown function '" + name + "'");
        };
    }

    // ---------------------------------------------------------------- two-arg

    private static BigDecimal applyTwoArg(String name, BigDecimal a, BigDecimal b, EvaluationContext ctx) {
        MathContext mc = ctx.getMathContext();
        return switch (name) {
            case "pow" -> power(a, b, mc);
            case "root" -> nthRoot(a, b, mc);
            case "logb" -> {
                requireDomain(name, a.signum() > 0, "logb is only defined for x > 0");
                requireDomain(name, b.signum() > 0 && b.compareTo(BigDecimal.ONE) != 0, "logb base must be > 0 and != 1");
                yield fromDouble(Math.log(a.doubleValue()) / Math.log(b.doubleValue()), mc);
            }
            case "mod" -> mathematicalModulo(a, b, mc);
            case "gcd" -> new BigDecimal(toBigIntegerExact(a, "gcd").gcd(toBigIntegerExact(b, "gcd")));
            case "lcm" -> lcm(toBigIntegerExact(a, "lcm"), toBigIntegerExact(b, "lcm"));
            case "ncr" -> combinations(a, b);
            case "npr" -> permutations(a, b);
            case "hypot" -> fromDouble(Math.hypot(a.doubleValue(), b.doubleValue()), mc);
            default -> throw new ExpressionException(ErrorCode.UNKNOWN_FUNCTION, "Unknown function '" + name + "'");
        };
    }

    // ---------------------------------------------------------------- variadic

    private static BigDecimal applyVariadic(String name, List<BigDecimal> args, MathContext mc) {
        return switch (name) {
            case "min" -> args.stream().min(BigDecimal::compareTo).orElseThrow();
            case "max" -> args.stream().max(BigDecimal::compareTo).orElseThrow();
            case "sum" -> args.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case "avg", "mean" -> {
                BigDecimal sum = args.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                yield sum.divide(BigDecimal.valueOf(args.size()), mc);
            }
            default -> throw new ExpressionException(ErrorCode.UNKNOWN_FUNCTION, "Unknown function '" + name + "'");
        };
    }

    // ---------------------------------------------------------------- shared helpers (also used by Evaluator)

    /**
     * General exponentiation used both by {@code pow(x,y)} and the {@code ^} operator.
     * Integer exponents in a safe range are computed with exact/arbitrary-precision
     * {@link BigDecimal} arithmetic; all other exponents fall back to double precision.
     */
    public static BigDecimal power(BigDecimal base, BigDecimal exponent, MathContext mc) {
        if (isIntegerValued(exponent) && exponent.abs().compareTo(BigDecimal.valueOf(999)) <= 0) {
            int exp = exponent.intValueExact();
            if (exp == 0) {
                requireDomain("pow", base.signum() != 0, "0^0 is undefined");
                return BigDecimal.ONE;
            }
            if (exp > 0) {
                return base.pow(exp, mc);
            }
            requireDomain("pow", base.signum() != 0, "Cannot raise 0 to a negative power");
            return BigDecimal.ONE.divide(base.pow(-exp, mc), mc);
        }
        requireDomain("pow", base.signum() >= 0,
                "Non-integer powers of negative numbers are not supported (result would be complex)");
        return fromDouble(Math.pow(base.doubleValue(), exponent.doubleValue()), mc);
    }

    private static BigDecimal nthRoot(BigDecimal x, BigDecimal n, MathContext mc) {
        requireDomain("root", n.signum() != 0, "Cannot take a 0th root");
        double xd = x.doubleValue();
        double nd = n.doubleValue();
        if (xd < 0) {
            boolean nIsOddInteger = isIntegerValued(n) && (n.toBigInteger().testBit(0));
            requireDomain("root", nIsOddInteger, "Even root of a negative number is not supported (result would be complex)");
            return fromDouble(-Math.pow(-xd, 1.0 / nd), mc);
        }
        return fromDouble(Math.pow(xd, 1.0 / nd), mc);
    }

    private static BigDecimal mathematicalModulo(BigDecimal a, BigDecimal b, MathContext mc) {
        requireDomain("mod", b.signum() != 0, "Division by zero in mod(a, b)");
        BigDecimal remainder = a.remainder(b, mc);
        if (remainder.signum() != 0 && remainder.signum() != b.signum()) {
            remainder = remainder.add(b);
        }
        return remainder;
    }

    private static BigDecimal lcm(BigInteger a, BigInteger b) {
        if (a.signum() == 0 || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigInteger gcd = a.gcd(b);
        return new BigDecimal(a.divide(gcd).multiply(b).abs());
    }

    public static BigDecimal factorial(BigDecimal x) {
        requireDomain("fact", isIntegerValued(x) && x.signum() >= 0,
                "Factorial is only defined for non-negative whole numbers");
        if (x.compareTo(BigDecimal.valueOf(MAX_FACTORIAL_N)) > 0) {
            throw new ExpressionException(ErrorCode.LIMIT_EXCEEDED,
                    "Factorial argument too large (limit is " + MAX_FACTORIAL_N + ")");
        }
        int n = x.intValueExact(); // safe: bounded above by MAX_FACTORIAL_N, well within int range
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return new BigDecimal(result);
    }

    private static BigDecimal combinations(BigDecimal nDec, BigDecimal rDec) {
        BigInteger n = toBigIntegerExact(nDec, "ncr");
        BigInteger r = toBigIntegerExact(rDec, "ncr");
        requireDomain("ncr", n.signum() >= 0 && r.signum() >= 0 && r.compareTo(n) <= 0,
                "nCr requires 0 <= r <= n");
        requireDomain("ncr", n.compareTo(BigInteger.valueOf(MAX_FACTORIAL_N)) <= 0,
                "n is too large (limit is " + MAX_FACTORIAL_N + ")");
        return new BigDecimal(binomial(n, r));
    }

    private static BigDecimal permutations(BigDecimal nDec, BigDecimal rDec) {
        BigInteger n = toBigIntegerExact(nDec, "npr");
        BigInteger r = toBigIntegerExact(rDec, "npr");
        requireDomain("npr", n.signum() >= 0 && r.signum() >= 0 && r.compareTo(n) <= 0,
                "nPr requires 0 <= r <= n");
        requireDomain("npr", n.compareTo(BigInteger.valueOf(MAX_FACTORIAL_N)) <= 0,
                "n is too large (limit is " + MAX_FACTORIAL_N + ")");
        BigInteger result = BigInteger.ONE;
        BigInteger k = n;
        for (BigInteger i = BigInteger.ZERO; i.compareTo(r) < 0; i = i.add(BigInteger.ONE)) {
            result = result.multiply(k);
            k = k.subtract(BigInteger.ONE);
        }
        return new BigDecimal(result);
    }

    /** Computes C(n, r) using the smaller-side multiplicative formula, avoiding full factorials. */
    private static BigInteger binomial(BigInteger n, BigInteger r) {
        BigInteger rr = r.min(n.subtract(r));
        BigInteger result = BigInteger.ONE;
        BigInteger i = BigInteger.ZERO;
        BigInteger numerator = n;
        while (i.compareTo(rr) < 0) {
            result = result.multiply(numerator).divide(i.add(BigInteger.ONE));
            numerator = numerator.subtract(BigInteger.ONE);
            i = i.add(BigInteger.ONE);
        }
        return result;
    }

    // ---------------------------------------------------------------- primitives

    public static boolean isIntegerValued(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    private static BigInteger toBigIntegerExact(BigDecimal value, String fn) {
        requireDomain(fn, isIntegerValued(value), fn + " requires a whole number argument");
        return value.toBigInteger();
    }

    private static BigDecimal fromDouble(double d, MathContext mc) {
        if (Double.isNaN(d)) {
            throw new ExpressionException(ErrorCode.DOMAIN_ERROR, "Result is not a real number");
        }
        if (Double.isInfinite(d)) {
            throw new ExpressionException(ErrorCode.OVERFLOW, "Result is too large to represent");
        }
        return new BigDecimal(Double.toString(d)).round(mc);
    }

    private static void requireDomain(String fn, boolean condition, String message) {
        if (!condition) {
            throw new ExpressionException(ErrorCode.DOMAIN_ERROR, message);
        }
    }

    private static void requireExactly(String fn, List<BigDecimal> args, int count) {
        if (args.size() != count) {
            throw new ExpressionException(ErrorCode.WRONG_ARGUMENT_COUNT,
                    fn + "() expects " + count + " argument" + (count == 1 ? "" : "s") + ", got " + args.size());
        }
    }

    private static void requireAtLeast(String fn, List<BigDecimal> args, int count) {
        if (args.size() < count) {
            throw new ExpressionException(ErrorCode.WRONG_ARGUMENT_COUNT,
                    fn + "() expects at least " + count + " argument" + (count == 1 ? "" : "s"));
        }
    }
}
