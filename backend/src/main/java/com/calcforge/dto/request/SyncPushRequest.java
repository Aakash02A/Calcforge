package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SyncPushRequest(
        @NotBlank String clientId,
        @NotNull List<SyncItem> workspaces,
        @NotNull List<SyncItem> variables,
        @NotNull List<SyncItem> formulas,
        @NotNull List<SyncItem> historyEntries
) {
    public record SyncItem(
            Long clientTempId,
            Long serverId,
            java.util.Map<String, Object> fields,
            java.time.Instant updatedAt,
            boolean deleted
    ) {
    }
}
