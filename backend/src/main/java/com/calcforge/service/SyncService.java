package com.calcforge.service;

import com.calcforge.domain.Formula;
import com.calcforge.domain.HistoryEntry;
import com.calcforge.domain.SyncMetadata;
import com.calcforge.domain.Variable;
import com.calcforge.domain.Workspace;
import com.calcforge.dto.request.SyncPullRequest;
import com.calcforge.dto.request.SyncPushRequest;
import com.calcforge.dto.response.SyncResultResponse;
import com.calcforge.repository.FormulaRepository;
import com.calcforge.repository.HistoryEntryRepository;
import com.calcforge.repository.SyncMetadataRepository;
import com.calcforge.repository.VariableRepository;
import com.calcforge.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately simple, last-write-wins synchronization service: each pushed item wins
 * over the server's copy unless the server's {@code updatedAt} is strictly newer, in which
 * case it is reported back as a conflict and left untouched (client should re-pull and
 * re-apply). This is enough for the common "one user, a couple of devices" case the
 * product targets; it is not a CRDT and does not merge field-by-field.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final WorkspaceRepository workspaceRepository;
    private final VariableRepository variableRepository;
    private final FormulaRepository formulaRepository;
    private final HistoryEntryRepository historyEntryRepository;
    private final SyncMetadataRepository syncMetadataRepository;

    @Transactional
    public SyncResultResponse push(Long userId, SyncPushRequest request) {
        List<String> conflicts = new ArrayList<>();

        for (SyncPushRequest.SyncItem item : request.workspaces()) {
            pushWorkspace(userId, request.clientId(), item, conflicts);
        }
        for (SyncPushRequest.SyncItem item : request.variables()) {
            pushVariable(userId, request.clientId(), item, conflicts);
        }
        for (SyncPushRequest.SyncItem item : request.formulas()) {
            pushFormula(userId, request.clientId(), item, conflicts);
        }
        for (SyncPushRequest.SyncItem item : request.historyEntries()) {
            pushHistoryEntry(userId, request.clientId(), item, conflicts);
        }

        return pull(userId, new SyncPullRequest(request.clientId(), null), conflicts);
    }

    public SyncResultResponse pull(Long userId, SyncPullRequest request) {
        return pull(userId, request, List.of());
    }

    /** Full export of everything owned by {@code userId}, for the "Backup" button. */
    public SyncResultResponse exportAll(Long userId) {
        return pull(userId, new SyncPullRequest("backup-export", null));
    }

    /**
     * Re-applies a previously exported bundle (as produced by {@link #exportAll}) for the
     * same account, via the same last-write-wins push path used by normal sync.
     */
    @Transactional
    public SyncResultResponse restoreFromBackup(Long userId, SyncResultResponse backup) {
        List<SyncPushRequest.SyncItem> workspaces = backup.workspaces().stream().map(this::toSyncItem).toList();
        List<SyncPushRequest.SyncItem> variables = backup.variables().stream().map(this::toSyncItem).toList();
        List<SyncPushRequest.SyncItem> formulas = backup.formulas().stream().map(this::toSyncItem).toList();
        List<SyncPushRequest.SyncItem> history = backup.historyEntries().stream().map(this::toSyncItem).toList();

        SyncPushRequest pushRequest = new SyncPushRequest("backup-restore", workspaces, variables, formulas, history);
        return push(userId, pushRequest);
    }

    private SyncPushRequest.SyncItem toSyncItem(Map<String, Object> record) {
        Long id = record.get("id") == null ? null : Long.valueOf(String.valueOf(record.get("id")));
        Instant updatedAt = Instant.now(); // restoring is treated as "now" so it always wins over the current server copy
        return new SyncPushRequest.SyncItem(null, id, record, updatedAt, false);
    }

    private SyncResultResponse pull(Long userId, SyncPullRequest request, List<String> conflicts) {
        Instant since = request.since() == null ? Instant.EPOCH : request.since();

        List<Map<String, Object>> workspaces = workspaceRepository.findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId)
                .stream().filter(w -> w.getUpdatedAt().isAfter(since))
                .map(this::workspaceToMap).toList();

        List<Map<String, Object>> variables = new ArrayList<>();
        List<Map<String, Object>> formulas = new ArrayList<>();
        for (Map<String, Object> ws : workspaces) {
            Long wsId = (Long) ws.get("id");
            variableRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(wsId)
                    .forEach(v -> variables.add(variableToMap(v)));
            formulaRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByNameAsc(wsId)
                    .forEach(f -> formulas.add(formulaToMap(f)));
        }

        List<Map<String, Object>> history = historyEntryRepository
                .search(userId, null, null, org.springframework.data.domain.PageRequest.of(0, 500,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .filter(h -> h.getCreatedAt().isAfter(since))
                .map(this::historyToMap).toList();

        return new SyncResultResponse(Instant.now(), workspaces, variables, formulas, history, conflicts);
    }

    private void pushWorkspace(Long userId, String clientId, SyncPushRequest.SyncItem item, List<String> conflicts) {
        if (item.serverId() == null && item.deleted()) {
            return; // nothing was ever created server-side, nothing to delete
        }
        try {
            Workspace entity = item.serverId() != null
                    ? workspaceRepository.findByIdAndDeletedAtIsNull(item.serverId()).orElse(null)
                    : null;
            if (entity == null) {
                entity = Workspace.builder().userId(userId).build();
            } else if (!userId.equals(entity.getUserId())) {
                conflicts.add("workspace:" + item.serverId() + " belongs to a different account");
                return;
            } else if (entity.getUpdatedAt() != null && item.updatedAt() != null && entity.getUpdatedAt().isAfter(item.updatedAt())) {
                conflicts.add("workspace:" + item.serverId() + " - server copy is newer");
                return;
            }
            if (item.deleted()) {
                entity.setDeletedAt(Instant.now());
            } else {
                entity.setName(str(item.fields(), "name", entity.getName() == null ? "Untitled" : entity.getName()));
                entity.setDescription(str(item.fields(), "description", entity.getDescription()));
            }
            workspaceRepository.save(entity);
            recordMetadata(userId, clientId, SyncMetadata.EntityType.WORKSPACE, entity.getId());
        } catch (Exception e) {
            log.warn("Failed to sync workspace item {}: {}", item, e.getMessage());
            conflicts.add("workspace:" + item.serverId() + " - " + e.getMessage());
        }
    }

    private void pushVariable(Long userId, String clientId, SyncPushRequest.SyncItem item, List<String> conflicts) {
        if (item.serverId() == null && item.deleted()) {
            return;
        }
        try {
            Variable entity = item.serverId() != null
                    ? variableRepository.findByIdAndDeletedAtIsNull(item.serverId()).orElse(null)
                    : null;
            Long workspaceId = numberField(item.fields(), "workspaceId");
            if (entity == null) {
                entity = Variable.builder().workspaceId(workspaceId).build();
            }
            if (item.deleted()) {
                entity.setDeletedAt(Instant.now());
            } else {
                entity.setName(str(item.fields(), "name", entity.getName()));
                entity.setValue(decimal(item.fields(), "value", entity.getValue()));
                entity.setUnit(str(item.fields(), "unit", entity.getUnit()));
                entity.setDescription(str(item.fields(), "description", entity.getDescription()));
            }
            variableRepository.save(entity);
            recordMetadata(userId, clientId, SyncMetadata.EntityType.VARIABLE, entity.getId());
        } catch (Exception e) {
            log.warn("Failed to sync variable item {}: {}", item, e.getMessage());
            conflicts.add("variable:" + item.serverId() + " - " + e.getMessage());
        }
    }

    private void pushFormula(Long userId, String clientId, SyncPushRequest.SyncItem item, List<String> conflicts) {
        if (item.serverId() == null && item.deleted()) {
            return;
        }
        try {
            Formula entity = item.serverId() != null
                    ? formulaRepository.findByIdAndDeletedAtIsNull(item.serverId()).orElse(null)
                    : null;
            Long workspaceId = numberField(item.fields(), "workspaceId");
            if (entity == null) {
                entity = Formula.builder().workspaceId(workspaceId).build();
            }
            if (item.deleted()) {
                entity.setDeletedAt(Instant.now());
            } else {
                entity.setName(str(item.fields(), "name", entity.getName()));
                entity.setExpression(str(item.fields(), "expression", entity.getExpression()));
                entity.setDescription(str(item.fields(), "description", entity.getDescription()));
            }
            formulaRepository.save(entity);
            recordMetadata(userId, clientId, SyncMetadata.EntityType.FORMULA, entity.getId());
        } catch (Exception e) {
            log.warn("Failed to sync formula item {}: {}", item, e.getMessage());
            conflicts.add("formula:" + item.serverId() + " - " + e.getMessage());
        }
    }

    private void pushHistoryEntry(Long userId, String clientId, SyncPushRequest.SyncItem item, List<String> conflicts) {
        if (item.serverId() == null && item.deleted()) {
            return;
        }
        try {
            HistoryEntry entity = item.serverId() != null
                    ? historyEntryRepository.findByIdAndDeletedAtIsNull(item.serverId()).orElse(null)
                    : null;
            if (entity == null) {
                entity = HistoryEntry.builder().userId(userId).build();
            }
            if (item.deleted()) {
                entity.setDeletedAt(Instant.now());
            } else {
                entity.setExpression(str(item.fields(), "expression", entity.getExpression()));
                entity.setResult(str(item.fields(), "result", entity.getResult()));
                entity.setTags(str(item.fields(), "tags", entity.getTags()));
            }
            historyEntryRepository.save(entity);
            recordMetadata(userId, clientId, SyncMetadata.EntityType.HISTORY_ENTRY, entity.getId());
        } catch (Exception e) {
            log.warn("Failed to sync history item {}: {}", item, e.getMessage());
            conflicts.add("history:" + item.serverId() + " - " + e.getMessage());
        }
    }

    private void recordMetadata(Long userId, String clientId, SyncMetadata.EntityType type, Long entityId) {
        SyncMetadata meta = syncMetadataRepository
                .findByUserIdAndClientIdAndEntityTypeAndEntityId(userId, clientId, type, entityId)
                .orElseGet(() -> SyncMetadata.builder()
                        .userId(userId).clientId(clientId).entityType(type).entityId(entityId).build());
        meta.setLocalUpdatedAt(Instant.now());
        meta.setRemoteUpdatedAt(Instant.now());
        meta.setSyncStatus(SyncMetadata.SyncStatus.SYNCED);
        syncMetadataRepository.save(meta);
    }

    private Map<String, Object> workspaceToMap(Workspace w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("name", w.getName());
        m.put("description", w.getDescription());
        m.put("shared", w.isShared());
        m.put("updatedAt", w.getUpdatedAt());
        return m;
    }

    private Map<String, Object> variableToMap(Variable v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("workspaceId", v.getWorkspaceId());
        m.put("name", v.getName());
        m.put("value", v.getValue());
        m.put("unit", v.getUnit());
        m.put("description", v.getDescription());
        m.put("updatedAt", v.getUpdatedAt());
        return m;
    }

    private Map<String, Object> formulaToMap(Formula f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("workspaceId", f.getWorkspaceId());
        m.put("name", f.getName());
        m.put("expression", f.getExpression());
        m.put("description", f.getDescription());
        m.put("updatedAt", f.getUpdatedAt());
        return m;
    }

    private Map<String, Object> historyToMap(HistoryEntry h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("expression", h.getExpression());
        m.put("result", h.getResult());
        m.put("tags", h.getTags());
        m.put("createdAt", h.getCreatedAt());
        return m;
    }

    private String str(Map<String, Object> fields, String key, String fallback) {
        if (fields == null || !fields.containsKey(key) || fields.get(key) == null) return fallback;
        return String.valueOf(fields.get(key));
    }

    private BigDecimal decimal(Map<String, Object> fields, String key, BigDecimal fallback) {
        if (fields == null || !fields.containsKey(key) || fields.get(key) == null) return fallback;
        return new BigDecimal(String.valueOf(fields.get(key)));
    }

    private Long numberField(Map<String, Object> fields, String key) {
        if (fields == null || !fields.containsKey(key) || fields.get(key) == null) return null;
        return Long.valueOf(String.valueOf(fields.get(key)).replace(".0", ""));
    }
}
