package com.calcforge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A persistent, searchable log of every calculation performed, independent of which
 * (if any) workspace card it came from. {@code userId} is nullable for local/anonymous
 * usage. {@code tags} is a comma-separated list of free-text labels the user can filter by.
 */
@Entity
@Table(name = "history_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(nullable = false, length = 2000)
    private String expression;

    @Column(length = 255)
    private String result;

    @Lob
    @Column(name = "trail_json", columnDefinition = "JSON")
    private String trailJson;

    @Column(length = 500)
    private String tags;

    @Builder.Default
    @Column(nullable = false)
    private boolean favorite = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
