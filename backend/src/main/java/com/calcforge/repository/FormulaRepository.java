package com.calcforge.repository;

import com.calcforge.domain.Formula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormulaRepository extends JpaRepository<Formula, Long> {
    List<Formula> findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(Long workspaceId);
    Optional<Formula> findByIdAndDeletedAtIsNull(Long id);
    Optional<Formula> findByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(Long workspaceId, String name);
    boolean existsByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(Long workspaceId, String name);
}
