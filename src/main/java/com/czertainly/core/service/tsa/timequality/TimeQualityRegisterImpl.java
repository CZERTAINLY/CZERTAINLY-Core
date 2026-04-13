package com.czertainly.core.service.tsa.timequality;


import com.czertainly.core.model.signing.timequality.ExplicitTimeQualityConfiguration;
import com.czertainly.core.model.signing.timequality.LocalClockTimeQualityConfiguration;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.service.tsa.clocksource.ClockSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TimeQualityRegisterImpl implements TimeQualityRegister {

    private static final Logger logger = LoggerFactory.getLogger(TimeQualityRegisterImpl.class);

    private final ConcurrentHashMap<String, AtomicReference<TimeQualityResult>> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TimeQualityStatus> lastLoggedStatus = new ConcurrentHashMap<>();
    private final ClockSource clockSource;
    private final LeapSecondGuard leapSecondGuard;
    private final MonotonicDriftDetector driftDetector;

    @Autowired
    public TimeQualityRegisterImpl(ClockSource clockSource) {
        this(clockSource, new LeapSecondGuard(clockSource), new MonotonicDriftDetector(clockSource));
    }

    TimeQualityRegisterImpl(ClockSource clockSource, LeapSecondGuard leapSecondGuard, MonotonicDriftDetector driftDetector) {
        this.clockSource = clockSource;
        this.leapSecondGuard = leapSecondGuard;
        this.driftDetector = driftDetector;
    }

    public void update(TimeQualityResult result) {
        entryFor(result.profile()).set(result);

        if (result.status() == TimeQualityStatus.OK) {
            driftDetector.captureReference(result.profile(), result.measuredDriftMs() != null ? result.measuredDriftMs() : 0.0);
        } else {
            driftDetector.clearReference(result.profile());
        }

        logger.atTrace()
                .addKeyValue("profile", result.profile())
                .addKeyValue("status", result.status())
                .addKeyValue("reason", result.reason())
                .addKeyValue("driftMs", result.measuredDriftMs())
                .log("Received time quality result");
    }

    public TimeQualityStatus getStatus(TimeQualityConfigurationModel profile) {
        return switch (profile) {
            case LocalClockTimeQualityConfiguration ignored -> TimeQualityStatus.OK;
            case ExplicitTimeQualityConfiguration explicit -> getStatus(explicit);
        };
    }

    private TimeQualityStatus getStatus(ExplicitTimeQualityConfiguration config) {
        var result = entryFor(config.getName()).get();
        if (result == null) {
            return degraded(config.getName(), "no result received yet");
        }

        var expiresAt = result.timestamp().plus(config.accuracy());
        if (clockSource.wallTimeInstant().isAfter(expiresAt)) {
            return degraded(config.getName(), "result is stale (received at %s, max age %s)"
                    .formatted(result.timestamp(), config.accuracy()));
        }

        if (result.status() == TimeQualityStatus.DEGRADED) {
            return degraded(config.getName(), "TimeMonitor reported degraded status");
        }

        if (Boolean.TRUE.equals(config.leapSecondGuard()) && leapSecondGuard.isLeapSecondRisk(result.leapSecondWarning())) {
            return degraded(config.getName(), "leap second guard active");
        }

        if (driftDetector.isDriftExceeded(config.getName(), config.maxClockDrift())) {
            return degraded(config.getName(), "clock drift exceeded threshold");
        }

        return ok(config.getName());
    }

    private AtomicReference<TimeQualityResult> entryFor(String profile) {
        return entries.computeIfAbsent(profile, ignored -> new AtomicReference<>());
    }

    private TimeQualityStatus degraded(String profile, String reason) {
        var previousStatus = lastLoggedStatus.put(profile, TimeQualityStatus.DEGRADED);
        if (previousStatus != TimeQualityStatus.DEGRADED) {
            logger.atWarn()
                    .addKeyValue("profile", profile)
                    .addKeyValue("status", "DEGRADED")
                    .addKeyValue("reason", reason)
                    .log("Time quality degraded");
        }
        return TimeQualityStatus.DEGRADED;
    }

    private TimeQualityStatus ok(String profile) {
        var previousStatus = lastLoggedStatus.put(profile, TimeQualityStatus.OK);
        if (previousStatus != TimeQualityStatus.OK) {
            logger.atDebug()
                    .addKeyValue("profile", profile)
                    .addKeyValue("status", "OK")
                    .log("Time quality recovered");
        }
        return TimeQualityStatus.OK;
    }
}