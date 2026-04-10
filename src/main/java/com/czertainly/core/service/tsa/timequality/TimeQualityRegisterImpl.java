package com.czertainly.core.service.tsa.timequality;


import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.core.service.tsa.clocksource.ClockSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class TimeQualityRegisterImpl implements TimeQualityRegister {

    private static final Logger logger = LoggerFactory.getLogger(TimeQualityRegisterImpl.class);

    private final ConcurrentHashMap<String, AtomicReference<TimeQualityResult>> entries;
    private final ConcurrentHashMap<String, TimeQualityStatus> lastLoggedStatus;
    private final Map<String, TimestampingWorkflowDto> profiles;
    private final ClockSource clockSource;
    private final LeapSecondGuard leapSecondGuard;
    private final MonotonicDriftDetector driftDetector;

    public TimeQualityRegisterImpl(Map<String, TimestampingWorkflowDto> profiles, ClockSource clockSource) {
        this(profiles, clockSource, new LeapSecondGuard(clockSource),
                new MonotonicDriftDetector(clockSource, profiles.keySet()));
    }

    TimeQualityRegisterImpl(Map<String, TimestampingWorkflowDto> profiles, ClockSource clockSource,
                            LeapSecondGuard leapSecondGuard, MonotonicDriftDetector driftDetector) {
        this.profiles = profiles;
        this.clockSource = clockSource;
        this.leapSecondGuard = leapSecondGuard;
        this.driftDetector = driftDetector;
        this.entries = new ConcurrentHashMap<>();
        this.lastLoggedStatus = new ConcurrentHashMap<>();
        for (var profileName : profiles.keySet()) {
            entries.put(profileName, new AtomicReference<>());
        }
    }

    public void update(TimeQualityResult result) {
        var ref = entries.get(result.profile());
        if (ref == null) {
            throw new IllegalArgumentException("Unknown profile: " + result.profile());
        }
        ref.set(result);

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

    public TimeQualityStatus getStatus(String profile) {
        var ref = entries.get(profile);
        if (ref == null) {
            throw new IllegalArgumentException("Unknown profile: " + profile);
        }

        var result = ref.get();
        if (result == null) {
            return degraded(profile, "no result received yet");
        }

        var timeConfig = profiles.get(profile).getTimeQualityConfiguration();

        // Staleness check
        var expiresAt = result.timestamp().plus(timeConfig.getAccuracy());
        if (clockSource.wallTimeInstant().isAfter(expiresAt)) {
            return degraded(profile, "result is stale (received at %s, max age %s)"
                    .formatted(result.timestamp(), timeConfig.getAccuracy()));
        }

        // Reported status from TimeMonitor
        if (result.status() == TimeQualityStatus.DEGRADED) {
            return degraded(profile, "TimeMonitor reported degraded status");
        }

        // Leap second guard
        if (timeConfig.isLeapSecondGuard() && leapSecondGuard.isLeapSecondRisk(result.leapSecondWarning())) {
            return degraded(profile, "leap second guard active");
        }

        // Monotonic drift detection
        if (driftDetector.isDriftExceeded(profile, timeConfig.getMaxClockDrift())) {
            return degraded(profile, "clock drift exceeded threshold");
        }

        return ok(profile);
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
