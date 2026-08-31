package com.calcforge.repository;

import com.calcforge.domain.Calculation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalculationRepository extends JpaRepository<Calculation, Long> {
    List<Calculation> findAllByWorkspaceIdAndDeletedAtIsNullOrderByPositionIndexAscCreatedAtAsc(Long workspaceId);
    Optional<Calculation> findByIdAndDeletedAtIsNull(Long id);
    long countByWorkspaceIdAndDeletedAtIsNull(Long workspaceId);
}
