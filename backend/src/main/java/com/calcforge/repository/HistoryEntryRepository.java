package com.calcforge.repository;

import com.calcforge.domain.HistoryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HistoryEntryRepository extends JpaRepository<HistoryEntry, Long> {

    Optional<HistoryEntry> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Searches history for the current scope (local/anonymous when {@code userId} is null,
     * otherwise a specific cloud account), optionally filtering by a free-text match against
     * the expression and/or a tag substring.
     */
    @Query("""
            SELECT h FROM HistoryEntry h
            WHERE h.deletedAt IS NULL
              AND ((:userId IS NULL AND h.userId IS NULL) OR h.userId = :userId)
              AND (:search IS NULL OR LOWER(h.expression) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:tag IS NULL OR LOWER(h.tags) LIKE LOWER(CONCAT('%', :tag, '%')))
            ORDER BY h.createdAt DESC
            """)
    Page<HistoryEntry> search(@Param("userId") Long userId,
                               @Param("search") String search,
                               @Param("tag") String tag,
                               Pageable pageable);
}
