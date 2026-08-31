package com.calcforge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One entry in the offline unit-conversion database. Conversion is always expressed
 * relative to the category's base unit via an affine transform:
 * {@code baseValue = value * toBaseFactor + toBaseOffset}, and inverted for the reverse
 * direction. For purely multiplicative units (length, mass, ...) {@code toBaseOffset} is 0.
 * Temperature units are the main case that need a non-zero offset.
 */
@Entity
@Table(name = "units", uniqueConstraints = @UniqueConstraint(columnNames = {"category", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "length", "mass", "temperature", "volume", "area", "speed", "time", "data", "pressure", "energy", "angle" */
    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "to_base_factor", nullable = false, columnDefinition = "DECIMAL(38,18)")
    private BigDecimal toBaseFactor;

    @Column(name = "to_base_offset", nullable = false, columnDefinition = "DECIMAL(38,18)")
    @Builder.Default
    private BigDecimal toBaseOffset = BigDecimal.ZERO;

    @Column(name = "is_base_unit", nullable = false)
    @Builder.Default
    private boolean baseUnit = false;

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
}
