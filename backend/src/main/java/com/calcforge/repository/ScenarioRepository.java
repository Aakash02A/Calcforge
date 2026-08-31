package com.calcforge.repository;

import com.calcforge.domain.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {
    List<Scenario> findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long workspaceId);
    Optional<Scenario> findByIdAndDeletedAtIsNull(Long id);
}
