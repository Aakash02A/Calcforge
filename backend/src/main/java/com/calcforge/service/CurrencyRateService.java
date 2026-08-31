package com.calcforge.service;

import com.calcforge.config.CloudFeatureProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Non-critical scheduled refresh of currency conversion rates. Disabled by default (see
 * {@link CloudFeatureProperties#isLiveCurrencyEnabled()}); while disabled, the "currency"
 * unit category simply uses its seeded static rates, which is exactly the offline-safe
 * fallback the local-first architecture requires. This is the integration point where a
 * real deployment would plug in a live FX rate provider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyRateService {

    private final CloudFeatureProperties featureProperties;

    @Scheduled(cron = "${calcforge.cloud.currency.refresh-cron:0 0 * * * *}")
    public void refreshRates() {
        if (!featureProperties.isLiveCurrencyEnabled()) {
            return;
        }
        // TODO: call a configured FX rate provider and update `units` rows where
        // category = 'currency'. Deliberately not implemented here - it requires a
        // deployment-specific API key and external network access.
        log.debug("Live currency refresh is enabled but no rate provider is configured; " +
                "currency units continue using their seeded static rates.");
    }
}
