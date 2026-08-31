package com.calcforge.repository;

import com.calcforge.domain.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findAllByUserIdIsNullAndDeletedAtIsNullOrderByUpdatedAtDesc();
    List<Workspace> findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long userId);
    Optional<Workspace> findByIdAndDeletedAtIsNull(Long id);
}
