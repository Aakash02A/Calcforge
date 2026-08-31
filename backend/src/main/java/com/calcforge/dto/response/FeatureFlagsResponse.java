package com.calcforge.dto.response;

public record FeatureFlagsResponse(
        boolean aiAssistEnabled,
        boolean liveCurrencyEnabled,
        boolean sharedWorkspacesEnabled,
        boolean analyticsEnabled,
        boolean syncEnabled
) {
}
