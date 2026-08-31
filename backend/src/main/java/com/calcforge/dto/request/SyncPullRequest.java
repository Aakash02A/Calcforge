package com.calcforge.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record SyncPullRequest(
        @NotBlank String clientId,
        Instant since
) {
}
