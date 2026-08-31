package com.calcforge.controller.cloud;

import com.calcforge.config.CloudFeatureProperties;
import com.calcforge.dto.response.FeatureFlagsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cloud/feature-flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final CloudFeatureProperties properties;

    @GetMapping
    public FeatureFlagsResponse get() {
        return new FeatureFlagsResponse(
                properties.isAiAssistEnabled(),
                properties.isLiveCurrencyEnabled(),
                properties.isSharedWorkspacesEnabled(),
                properties.isAnalyticsEnabled(),
                properties.isSyncEnabled());
    }
}
