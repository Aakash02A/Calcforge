package com.calcforge.repository;

import com.calcforge.domain.Variable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VariableRepository extends JpaRepository<Variable, Long> {
    List<Variable> findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(Long workspaceId);
    Optional<Variable> findByIdAndDeletedAtIsNull(Long id);
    Optional<Variable> findByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(Long workspaceId, String name);
    boolean existsByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(Long workspaceId, String name);
}
