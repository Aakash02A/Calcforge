package com.calcforge.repository;

import com.calcforge.domain.SyncMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncMetadataRepository extends JpaRepository<SyncMetadata, Long> {
    Optional<SyncMetadata> findByUserIdAndClientIdAndEntityTypeAndEntityId(
            Long userId, String clientId, SyncMetadata.EntityType entityType, Long entityId);

    List<SyncMetadata> findAllByUserIdAndClientIdAndSyncStatus(
            Long userId, String clientId, SyncMetadata.SyncStatus syncStatus);
}
