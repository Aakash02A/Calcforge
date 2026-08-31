package com.calcforge.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SyncResultResponse(
        Instant syncedAt,
        List<Map<String, Object>> workspaces,
        List<Map<String, Object>> variables,
        List<Map<String, Object>> formulas,
        List<Map<String, Object>> historyEntries,
        List<String> conflicts
) {
}
