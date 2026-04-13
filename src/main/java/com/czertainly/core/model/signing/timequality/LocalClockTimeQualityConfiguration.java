package com.czertainly.core.model.signing.timequality;

import java.time.Duration;
import java.util.Optional;

/**
 * Time-quality configuration variant representing the absence of any user-provided
 * configuration — the local system clock is used as-is.
 */
public enum LocalClockTimeQualityConfiguration implements TimeQualityConfigurationModel {

    INSTANCE;

    private static final String NAME = "LocalTime";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Optional<Duration> getAccuracy() {
        return Optional.empty();
    }

    @Override
    public TimeQualitySource getSource() {
        return TimeQualitySource.LOCAL_CLOCK;
    }
}