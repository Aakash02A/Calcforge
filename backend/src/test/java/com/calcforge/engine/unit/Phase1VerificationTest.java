package com.calcforge.engine.unit;

import com.calcforge.engine.AngleMode;
import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.ast.AddNode;
import com.calcforge.engine.ast.AstNode;
import com.calcforge.engine.ast.DivideNode;
import com.calcforge.engine.ast.MultiplyNode;
import com.calcforge.engine.ast.ValueNode;
import com.calcforge.exception.DimensionalMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Phase1VerificationTest {

    private final EvaluationContext ctx = new EvaluationContext(AngleMode.DEGREES, 20);

    @Test
    @DisplayName("Scenario 1: Valid Multiplication (Unit Derivation) - 50[kg] * 9.81[m/s^2] = 490.5[N]")
    void testScenario1_ValidMultiplication() throws Exception {
        UnitDimension kgDim = UnitParser.parse("kg");
        UnitDimension accelDim = UnitParser.parse("m/s^2");
        UnitDimension expectedNewtonDim = UnitParser.parse("N");

        AstNode mass = new ValueNode(new PhysicalValue(new BigDecimal("50"), kgDim));
        AstNode accel = new ValueNode(new PhysicalValue(new BigDecimal("9.81"), accelDim));
        AstNode expr = new MultiplyNode(mass, accel);

        PhysicalValue result = expr.evaluate(ctx);

        assertEquals(0, new BigDecimal("490.5").compareTo(result.getValue()), "Value must equal 490.5");
        assertEquals(expectedNewtonDim, result.getDimension(), "Dimension must match N (kg*m/s^2)");
        assertEquals(1, result.getDimension().mass());
        assertEquals(1, result.getDimension().length());
        assertEquals(-2, result.getDimension().time());
    }

    @Test
    @DisplayName("Scenario 2: Invalid Addition (Type-Safety Guardrail) - 10[kg] + 5[m] throws DimensionalMismatchException")
    void testScenario2_InvalidAddition() {
        UnitDimension kgDim = UnitParser.parse("kg");
        UnitDimension mDim = UnitParser.parse("m");

        AstNode mass = new ValueNode(new PhysicalValue(new BigDecimal("10"), kgDim));
        AstNode length = new ValueNode(new PhysicalValue(new BigDecimal("5"), mDim));
        AstNode expr = new AddNode(mass, length);

        DimensionalMismatchException ex = assertThrows(
                DimensionalMismatchException.class,
                () -> expr.evaluate(ctx),
                "Should throw DimensionalMismatchException"
        );

        assertNotNull(ex.getMessage());
        assertEquals("+", ex.getOperation());
        assertEquals(kgDim, ex.getLeftDimension());
        assertEquals(mDim, ex.getRightDimension());
    }

    @Test
    @DisplayName("Scenario 3: Complex Division (Unit Simplification) - 100[kg*m/s^2] / 10[kg] = 10[m/s^2]")
    void testScenario3_ComplexDivision() throws Exception {
        UnitDimension forceDim = UnitParser.parse("kg*m/s^2");
        UnitDimension massDim = UnitParser.parse("kg");
        UnitDimension expectedAccelDim = UnitParser.parse("m/s^2");

        AstNode force = new ValueNode(new PhysicalValue(new BigDecimal("100"), forceDim));
        AstNode mass = new ValueNode(new PhysicalValue(new BigDecimal("10"), massDim));
        AstNode expr = new DivideNode(force, mass);

        PhysicalValue result = expr.evaluate(ctx);

        assertEquals(0, new BigDecimal("10").compareTo(result.getValue()), "Value must equal 10");
        assertEquals(expectedAccelDim, result.getDimension(), "Dimension must simplify down to m/s^2");
        assertEquals(0, result.getDimension().mass());
        assertEquals(1, result.getDimension().length());
        assertEquals(-2, result.getDimension().time());
    }
}
