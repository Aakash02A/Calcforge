package com.calcforge.service;

import com.calcforge.domain.Variable;
import com.calcforge.dto.request.VariableRequest;
import com.calcforge.dto.response.VariableResponse;
import com.calcforge.exception.DuplicateResourceException;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** CRUD for reusable, named variables scoped to a workspace. */
@Service
@RequiredArgsConstructor
public class VariableService {

    private static final Set<String> RESERVED_NAMES = Set.of("pi", "e", "tau", "phi");

    private final VariableRepository variableRepository;
    private final WorkspaceService workspaceService;

    @Transactional
    public VariableResponse create(Long workspaceId, VariableRequest request) {
        workspaceService.getEntity(workspaceId); // 404s if missing
        validateName(workspaceId, request.name(), null);

        Variable variable = Variable.builder()
                .workspaceId(workspaceId)
                .name(request.name())
                .value(request.value())
                .unit(request.unit())
                .description(request.description())
                .build();
        return toResponse(variableRepository.save(variable));
    }

    public List<VariableResponse> list(Long workspaceId) {
        return variableRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(workspaceId)
                .stream().map(this::toResponse).toList();
    }

    public VariableResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public Variable getEntity(Long id) {
        return variableRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Variable", id));
    }

    @Transactional
    public VariableResponse update(Long id, VariableRequest request) {
        Variable variable = getEntity(id);
        validateName(variable.getWorkspaceId(), request.name(), id);
        variable.setName(request.name());
        variable.setValue(request.value());
        variable.setUnit(request.unit());
        variable.setDescription(request.description());
        return toResponse(variableRepository.save(variable));
    }

    @Transactional
    public void delete(Long id) {
        Variable variable = getEntity(id);
        variable.setDeletedAt(java.time.Instant.now());
        variableRepository.save(variable);
    }

    private void validateName(Long workspaceId, String name, Long selfId) {
        if (RESERVED_NAMES.contains(name.toLowerCase())) {
            throw new IllegalArgumentException(
                    "'" + name + "' is a reserved constant name and cannot be used for a variable");
        }
        variableRepository.findByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(workspaceId, name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "A variable named '" + name + "' already exists in this workspace");
                });
    }

    private VariableResponse toResponse(Variable v) {
        return new VariableResponse(v.getId(), v.getWorkspaceId(), v.getName(), v.getValue(), v.getUnit(),
                v.getDescription(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
