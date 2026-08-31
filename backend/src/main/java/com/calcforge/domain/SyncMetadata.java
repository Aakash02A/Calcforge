package com.calcforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Tracks per-entity sync state for the optional cloud layer, enabling simple
 * last-write-wins conflict resolution between a device's local database and the
 * cloud copy. One row per (user, entity type, entity id).
 */
@Entity
@Table(name = "sync_metadata",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "entity_type", "entity_id", "client_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncMetadata {

    public enum SyncStatus { SYNCED, PENDING, CONFLICT }
    public enum EntityType { WORKSPACE, CALCULATION, VARIABLE, FORMULA, HISTORY_ENTRY, SCENARIO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Identifies the originating device/browser so multiple clients can sync independently. */
    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "local_updated_at", nullable = false)
    private Instant localUpdatedAt;

    @Column(name = "remote_updated_at")
    private Instant remoteUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
