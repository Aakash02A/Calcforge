package com.calcforge.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the evaluator needs to compute a single expression: variable bindings,
 * angle mode, numeric precision, and the growing list of {@link TrailStep}s that
 * document the computation as it happens.
 *
 * <p>Variable names are matched case-insensitively (internally stored lower-cased) so
 * that {@code X} and {@code x} refer to the same value, which matches how most people
 * expect a calculator - as opposed to a programming language - to behave.</p>
 */
public class EvaluationContext {

    private final Map<String, BigDecimal> variables = new HashMap<>();
    private final List<TrailStep> trail = new ArrayList<>();
    private final AngleMode angleMode;
    private final MathContext mathContext;
    private int computationStepCounter = 0;

    public EvaluationContext(AngleMode angleMode, int precision) {
        this.angleMode = angleMode == null ? AngleMode.DEGREES : angleMode;
        int safePrecision = Math.max(4, Math.min(precision <= 0 ? 20 : precision, 50));
        this.mathContext = new MathContext(safePrecision, RoundingMode.HALF_UP);
    }

    public void setVariable(String name, BigDecimal value) {
        variables.put(name.toLowerCase(), value);
    }

    public void setVariables(Map<String, BigDecimal> vars) {
        if (vars == null) return;
        vars.forEach(this::setVariable);
    }

    /** Returns the value bound to {@code name}, or {@code null} if it is not defined (not a constant either). */
    public BigDecimal lookupVariable(String name) {
        return variables.get(name.toLowerCase());
    }

    public boolean hasVariable(String name) {
        return variables.containsKey(name.toLowerCase());
    }

    public Map<String, BigDecimal> getVariables() {
        return variables;
    }

    public AngleMode getAngleMode() {
        return angleMode;
    }

    public MathContext getMathContext() {
        return mathContext;
    }

    public int getPrecision() {
        return mathContext.getPrecision();
    }

    public void addTrailStep(TrailStep step) {
        trail.add(step);
    }

    public int nextComputationStepNumber() {
        return ++computationStepCounter;
    }

    public List<TrailStep> getTrail() {
        return trail;
    }
}
