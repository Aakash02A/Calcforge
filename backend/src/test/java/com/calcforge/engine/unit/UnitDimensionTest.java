package com.calcforge.engine.unit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitDimensionTest {

    @Test
    void testDerivedUnitsEquivalence() {
        UnitDimension newtonFromSymbol = UnitParser.parse("N");
        UnitDimension newtonFromFormula = UnitParser.parse("kg*m/s^2");
        assertEquals(newtonFromSymbol, newtonFromFormula);
        assertEquals(1, newtonFromSymbol.mass());
        assertEquals(1, newtonFromSymbol.length());
        assertEquals(-2, newtonFromSymbol.time());

        UnitDimension jouleFromSymbol = UnitParser.parse("J");
        UnitDimension jouleFromForce = UnitParser.parse("N*m");
        UnitDimension jouleFromBase = UnitParser.parse("kg*m^2/s^2");
        assertEquals(jouleFromSymbol, jouleFromForce);
        assertEquals(jouleFromSymbol, jouleFromBase);

        UnitDimension wattFromSymbol = UnitParser.parse("W");
        UnitDimension wattFromEnergy = UnitParser.parse("J/s");
        UnitDimension wattFromBase = UnitParser.parse("kg*m^2/s^3");
        assertEquals(wattFromSymbol, wattFromEnergy);
        assertEquals(wattFromSymbol, wattFromBase);

        UnitDimension pascalFromSymbol = UnitParser.parse("Pa");
        UnitDimension pascalFromForceArea = UnitParser.parse("N/m^2");
        UnitDimension pascalFromBase = UnitParser.parse("kg/(m*s^2)");
        assertEquals(pascalFromSymbol, pascalFromForceArea);
        assertEquals(pascalFromSymbol, pascalFromBase);

        UnitDimension voltFromSymbol = UnitParser.parse("V");
        UnitDimension voltFromPower = UnitParser.parse("W/A");
        assertEquals(voltFromSymbol, voltFromPower);
    }

    @Test
    void testDimensionlessAndPowers() {
        UnitDimension dimensionless = UnitParser.parse("1");
        assertTrue(dimensionless.isDimensionless());

        UnitDimension velocity = UnitParser.parse("m/s");
        UnitDimension acceleration = UnitParser.parse("m/s^2");
        assertEquals(acceleration, velocity.divide(UnitParser.parse("s")));

        UnitDimension area = UnitParser.parse("m^2");
        UnitDimension volume = UnitParser.parse("m^3");
        assertEquals(volume, area.multiply(UnitParser.parse("m")));
    }

    @Test
    void testPhysicalValueArithmetic() {
        PhysicalValue mass = new PhysicalValue(new BigDecimal("50"), UnitParser.parse("kg"));
        PhysicalValue accel = new PhysicalValue(new BigDecimal("9.81"), UnitParser.parse("m/s^2"));

        PhysicalValue force = mass.multiply(accel);
        assertEquals(new BigDecimal("490.50"), force.getValue());
        assertEquals(UnitParser.parse("N"), force.getDimension());

        PhysicalValue distance = new PhysicalValue(new BigDecimal("10"), UnitParser.parse("m"));
        PhysicalValue work = force.multiply(distance);
        assertEquals(UnitParser.parse("J"), work.getDimension());

        PhysicalValue time = new PhysicalValue(new BigDecimal("2"), UnitParser.parse("s"));
        PhysicalValue power = work.divide(time, MathContext.DECIMAL64);
        assertEquals(UnitParser.parse("W"), power.getDimension());

        PhysicalValue mass2 = new PhysicalValue(new BigDecimal("20"), UnitParser.parse("kg"));
        PhysicalValue totalMass = mass.add(mass2);
        assertEquals(new BigDecimal("70"), totalMass.getValue());
        assertEquals(UnitParser.parse("kg"), totalMass.getDimension());

        assertThrows(IllegalArgumentException.class, () -> mass.add(distance));
        assertThrows(IllegalArgumentException.class, () -> force.subtract(time));
    }
}
