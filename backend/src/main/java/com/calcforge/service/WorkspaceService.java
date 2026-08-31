package com.calcforge.service;

import com.calcforge.domain.Workspace;
import com.calcforge.dto.request.WorkspaceRequest;
import com.calcforge.dto.response.WorkspaceResponse;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.CalculationRepository;
import com.calcforge.repository.FormulaRepository;
import com.calcforge.repository.VariableRepository;
import com.calcforge.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Workspaces are local by default (no account needed). A workspace created through the
 * unauthenticated local API always has {@code userId == null}; cloud sync attaches an
 * owner later, it never requires one up front.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final CalculationRepository calculationRepository;
    private final VariableRepository variableRepository;
    private final FormulaRepository formulaRepository;

    @Transactional
    public WorkspaceResponse create(WorkspaceRequest request, Long ownerUserId) {
        Workspace workspace = Workspace.builder()
                .userId(ownerUserId)
                .name(request.name())
                .description(request.description())
                .shared(false)
                .build();
        return toResponse(workspaceRepository.save(workspace));
    }

    public List<WorkspaceResponse> listLocal() {
        return workspaceRepository.findAllByUserIdIsNullAndDeletedAtIsNullOrderByUpdatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    public List<WorkspaceResponse> listForUser(Long userId) {
        return workspaceRepository.findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public WorkspaceResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public Workspace getEntity(Long id) {
        return workspaceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Workspace", id));
    }

    @Transactional
    public WorkspaceResponse update(Long id, WorkspaceRequest request) {
        Workspace workspace = getEntity(id);
        workspace.setName(request.name());
        workspace.setDescription(request.description());
        return toResponse(workspaceRepository.save(workspace));
    }

    /** Toggles cloud sharing on a workspace the caller owns. Local (userId == null) workspaces cannot be shared. */
    @Transactional
    public WorkspaceResponse setShared(Long id, boolean shared, Long requestingUserId) {
        Workspace workspace = getEntity(id);
        if (workspace.getUserId() == null || !workspace.getUserId().equals(requestingUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the owning account can change sharing on this workspace");
        }
        workspace.setShared(shared);
        return toResponse(workspaceRepository.save(workspace));
    }

    /** Read-only lookup for the public "shared workspace" viewer - only returns workspaces with shared == true. */
    public WorkspaceResponse getSharedOrThrow(Long id) {
        Workspace workspace = getEntity(id);
        if (!workspace.isShared()) {
            throw ResourceNotFoundException.of("Shared workspace", id);
        }
        return toResponse(workspace);
    }

    @Transactional
    public void delete(Long id) {
        Workspace workspace = getEntity(id);
        workspace.setDeletedAt(Instant.now());
        workspaceRepository.save(workspace);
    }

    private WorkspaceResponse toResponse(Workspace w) {
        long calcCount = calculationRepository.countByWorkspaceIdAndDeletedAtIsNull(w.getId());
        long varCount = variableRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(w.getId()).size();
        long formulaCount = formulaRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(w.getId()).size();
        return new WorkspaceResponse(w.getId(), w.getUserId(), w.getName(), w.getDescription(), w.isShared(),
                calcCount, varCount, formulaCount, w.getCreatedAt(), w.getUpdatedAt());
    }
}
