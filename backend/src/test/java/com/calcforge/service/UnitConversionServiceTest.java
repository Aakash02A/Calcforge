package com.calcforge.service;

import com.calcforge.domain.Unit;
import com.calcforge.dto.request.UnitConversionRequest;
import com.calcforge.dto.response.UnitConversionResponse;
import com.calcforge.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit conversion is tested against a mocked repository rather than a real database,
 * since the interesting logic (the affine base-unit transform) lives entirely in
 * {@link UnitConversionService}, not in the seeded data.
 */
@ExtendWith(MockitoExtension.class)
class UnitConversionServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Test
    void convertsCelsiusToFahrenheit() {
        UnitConversionService service = new UnitConversionService(unitRepository);

        Unit celsius = Unit.builder().category("temperature").symbol("C")
                .toBaseFactor(BigDecimal.ONE).toBaseOffset(new BigDecimal("273.15")).baseUnit(false).build();
        Unit fahrenheit = Unit.builder().category("temperature").symbol("F")
                .toBaseFactor(new BigDecimal("0.5555555555555556"))
                .toBaseOffset(new BigDecimal("255.3722222222222")).baseUnit(false).build();

        when(unitRepository.findByCategoryAndSymbol("temperature", "C")).thenReturn(Optional.of(celsius));
        when(unitRepository.findByCategoryAndSymbol("temperature", "F")).thenReturn(Optional.of(fahrenheit));

        UnitConversionResponse response = service.convert(
                new UnitConversionRequest("temperature", "C", "F", BigDecimal.valueOf(100)));

        // 100C = 212F (boiling point of water)
        assertEquals(0, new BigDecimal("212")
                .compareTo(response.toValue().setScale(0, RoundingMode.HALF_UP)));
    }

    @Test
    void convertsFreezingPointCorrectly() {
        UnitConversionService service = new UnitConversionService(unitRepository);

        Unit celsius = Unit.builder().category("temperature").symbol("C")
                .toBaseFactor(BigDecimal.ONE).toBaseOffset(new BigDecimal("273.15")).baseUnit(false).build();
        Unit kelvin = Unit.builder().category("temperature").symbol("K")
                .toBaseFactor(BigDecimal.ONE).toBaseOffset(BigDecimal.ZERO).baseUnit(true).build();

        when(unitRepository.findByCategoryAndSymbol("temperature", "C")).thenReturn(Optional.of(celsius));
        when(unitRepository.findByCategoryAndSymbol("temperature", "K")).thenReturn(Optional.of(kelvin));

        UnitConversionResponse response = service.convert(
                new UnitConversionRequest("temperature", "C", "K", BigDecimal.ZERO));

        // 0C = 273.15K exactly
        assertEquals(0, new BigDecimal("273.15").compareTo(response.toValue()));
    }

    @Test
    void convertsMetersToFeet() {
        UnitConversionService service = new UnitConversionService(unitRepository);

        Unit meter = Unit.builder().category("length").symbol("m")
                .toBaseFactor(BigDecimal.ONE).toBaseOffset(BigDecimal.ZERO).baseUnit(true).build();
        Unit foot = Unit.builder().category("length").symbol("ft")
                .toBaseFactor(new BigDecimal("0.3048")).toBaseOffset(BigDecimal.ZERO).baseUnit(false).build();

        when(unitRepository.findByCategoryAndSymbol("length", "m")).thenReturn(Optional.of(meter));
        when(unitRepository.findByCategoryAndSymbol("length", "ft")).thenReturn(Optional.of(foot));

        UnitConversionResponse response = service.convert(
                new UnitConversionRequest("length", "m", "ft", BigDecimal.ONE));

        // 1 meter ~= 3.28084 feet
        double result = response.toValue().doubleValue();
        assertTrue(Math.abs(result - 3.28084) < 0.0001);
    }

    @Test
    void sameUnitConversionIsIdentity() {
        UnitConversionService service = new UnitConversionService(unitRepository);

        Unit meter = Unit.builder().category("length").symbol("m")
                .toBaseFactor(BigDecimal.ONE).toBaseOffset(BigDecimal.ZERO).baseUnit(true).build();

        when(unitRepository.findByCategoryAndSymbol("length", "m")).thenReturn(Optional.of(meter));

        UnitConversionResponse response = service.convert(
                new UnitConversionRequest("length", "m", "m", BigDecimal.valueOf(42)));

        assertEquals(0, BigDecimal.valueOf(42).compareTo(response.toValue()));
    }
}
