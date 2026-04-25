package com.czertainly.core.service.tsa.timequality;


import com.czertainly.core.model.signing.timequality.ExplicitTimeQualityConfiguration;
import com.czertainly.core.model.signing.timequality.LocalClockTimeQualityConfiguration;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.service.tsa.clocksource.ClockSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class TimeQualityRegisterImpl implements TimeQualityRegister {
    private final ConcurrentHashMap<UUID, AtomicReference<TimeQualityResult>> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TimeQualityStatus> lastLoggedStatus = new ConcurrentHashMap<>();
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
        entryFor(result.profileUuid()).set(result);

        if (result.status() == TimeQualityStatus.OK) {
            driftDetector.captureReference(result.profileUuid(), result.measuredDriftMs() != null ? result.measuredDriftMs() : 0.0);
        } else {
            driftDetector.clearReference(result.profileUuid());
        }

        log.atTrace()
                .addKeyValue("profile UUID", result.profileUuid())
                .addKeyValue("profile name", result.profileName())
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
        var result = entryFor(config.getUuid()).get();
        if (result == null) {
            return degraded(config.getUuid(), config.getName(), "no result received yet");
        }

        var expiresAt = result.timestamp().plus(config.accuracy());
        if (clockSource.wallTimeInstant().isAfter(expiresAt)) {
            return degraded(config.getUuid(), config.getName(), "result is stale (received at %s, max age %s)"
                    .formatted(result.timestamp(), config.accuracy()));
        }

        if (result.status() == TimeQualityStatus.DEGRADED) {
            return degraded(config.getUuid(), config.getName(), "TimeMonitor reported degraded status");
        }

        if (Boolean.TRUE.equals(config.leapSecondGuard()) && leapSecondGuard.isLeapSecondRisk(result.leapSecondWarning())) {
            return degraded(config.getUuid(), config.getName(), "leap second guard active");
        }

        if (driftDetector.isDriftExceeded(config.getUuid(), config.maxClockDrift())) {
            return degraded(config.getUuid(), config.getName(), "clock drift exceeded threshold");
        }

        return ok(config.getUuid(), config.getName());
    }

    private AtomicReference<TimeQualityResult> entryFor(UUID profileUuid) {
        return entries.computeIfAbsent(profileUuid, ignored -> new AtomicReference<>());
    }

    private TimeQualityStatus degraded(UUID profileUuid, String profileName, String reason) {
        var previousStatus = lastLoggedStatus.put(profileUuid, TimeQualityStatus.DEGRADED);
        if (previousStatus != TimeQualityStatus.DEGRADED) {
            log.atWarn()
                    .addKeyValue("profile UUID", profileUuid)
                    .addKeyValue("profile name", profileName)
                    .addKeyValue("status", "DEGRADED")
                    .addKeyValue("reason", reason)
                    .log("Time quality degraded");
        }
        return TimeQualityStatus.DEGRADED;
    }

    private TimeQualityStatus ok(UUID profileUuid, String profileName) {
        var previousStatus = lastLoggedStatus.put(profileUuid, TimeQualityStatus.OK);
        if (previousStatus != TimeQualityStatus.OK) {
            log.atDebug()
                    .addKeyValue("profile UUID", profileUuid)
                    .addKeyValue("profile name", profileName)
                    .addKeyValue("status", "OK")
                    .log("Time quality recovered");
        }
        return TimeQualityStatus.OK;
    }
}