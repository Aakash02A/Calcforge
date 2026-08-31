package com.calcforge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Toggles for every optional cloud feature, bound from {@code calcforge.cloud.*} in
 * application.yml. All default to {@code false} in the {@code local} profile - the app
 * must be 100% usable with every one of these off.
 */
@Component
@ConfigurationProperties(prefix = "calcforge.cloud")
@Getter
@Setter
public class CloudFeatureProperties {
    private boolean aiAssistEnabled = false;
    private boolean liveCurrencyEnabled = false;
    private boolean sharedWorkspacesEnabled = false;
    private boolean analyticsEnabled = false;
    private boolean syncEnabled = true;
}
