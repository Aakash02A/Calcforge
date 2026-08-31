package com.calcforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One calculation "card" living on a {@link Workspace} canvas. */
@Entity
@Table(name = "calculations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Calculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** Optional user-facing label for the card, e.g. "Kitchen remodel budget". */
    @Column(length = 255)
    private String label;

    @Column(nullable = false, length = 2000)
    private String expression;

    @Column(length = 255)
    private String result;

    /** Serialized {@code CalculationTrail} JSON - see docs/CALCULATION_TRAIL.md. */
    @Column(name = "trail_json", columnDefinition = "JSON")
    private String trailJson;

    @Column(name = "position_index")
    @Builder.Default
    private int positionIndex = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
