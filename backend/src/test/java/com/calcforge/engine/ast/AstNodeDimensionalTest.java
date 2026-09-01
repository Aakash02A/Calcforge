package com.calcforge.engine.ast;

import com.calcforge.engine.AngleMode;
import com.calcforge.engine.EvaluationContext;
import com.calcforge.engine.unit.PhysicalValue;
import com.calcforge.engine.unit.UnitParser;
import com.calcforge.exception.DimensionalMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AstNodeDimensionalTest {

    private final EvaluationContext ctx = new EvaluationContext(AngleMode.DEGREES, 20);

    @Test
    void testAddNodeMatchingDimensions() throws Exception {
        AstNode left = new ValueNode(new PhysicalValue(new BigDecimal("10"), UnitParser.parse("m")));
        AstNode right = new ValueNode(new PhysicalValue(new BigDecimal("5"), UnitParser.parse("m")));
        AstNode add = new AddNode(left, right);

        PhysicalValue result = add.evaluate(ctx);
        assertEquals(new BigDecimal("15"), result.getValue());
        assertEquals(UnitParser.parse("m"), result.getDimension());
    }

    @Test
    void testAddNodeMismatchThrowsException() {
        AstNode left = new ValueNode(new PhysicalValue(new BigDecimal("10"), UnitParser.parse("m")));
        AstNode right = new ValueNode(new PhysicalValue(new BigDecimal("5"), UnitParser.parse("s")));
        AstNode add = new AddNode(left, right);

        DimensionalMismatchException ex = assertThrows(DimensionalMismatchException.class, () -> add.evaluate(ctx));
        assertEquals("+", ex.getOperation());
        assertEquals(UnitParser.parse("m"), ex.getLeftDimension());
        assertEquals(UnitParser.parse("s"), ex.getRightDimension());
    }

    @Test
    void testSubtractNodeMismatchThrowsException() {
        AstNode left = new ValueNode(new PhysicalValue(new BigDecimal("10"), UnitParser.parse("kg")));
        AstNode right = new ValueNode(new PhysicalValue(new BigDecimal("5"), UnitParser.parse("N")));
        AstNode sub = new SubtractNode(left, right);

        DimensionalMismatchException ex = assertThrows(DimensionalMismatchException.class, () -> sub.evaluate(ctx));
        assertEquals("-", ex.getOperation());
    }

    @Test
    void testMultiplyNodeCombinesDimensions() throws Exception {
        AstNode mass = new ValueNode(new PhysicalValue(new BigDecimal("50"), UnitParser.parse("kg")));
        AstNode accel = new ValueNode(new PhysicalValue(new BigDecimal("9.81"), UnitParser.parse("m/s^2")));
        AstNode mul = new MultiplyNode(mass, accel);

        PhysicalValue result = mul.evaluate(ctx);
        assertEquals(new BigDecimal("490.50"), result.getValue());
        assertEquals(UnitParser.parse("N"), result.getDimension());
    }

    @Test
    void testDivideNodeSubtractsDimensions() throws Exception {
        AstNode energy = new ValueNode(new PhysicalValue(new BigDecimal("100"), UnitParser.parse("J")));
        AstNode time = new ValueNode(new PhysicalValue(new BigDecimal("5"), UnitParser.parse("s")));
        AstNode div = new DivideNode(energy, time);

        PhysicalValue result = div.evaluate(ctx);
        assertEquals(new BigDecimal("20"), result.getValue());
        assertEquals(UnitParser.parse("W"), result.getDimension());
    }
}
