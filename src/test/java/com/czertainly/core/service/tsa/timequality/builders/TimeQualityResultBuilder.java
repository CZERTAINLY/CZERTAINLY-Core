package com.czertainly.core.service.tsa.timequality.builders;

import com.czertainly.core.service.tsa.timequality.LeapSecondWarning;
import com.czertainly.core.service.tsa.timequality.NtpServerResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityStatus;

import java.time.Instant;
import java.util.List;


public final class TimeQualityResultBuilder {

    private String profile;
    private Instant timestamp;
    private TimeQualityStatus status;
    private Double measuredDriftMs;
    private String reason;
    private int reachableServers;
    private LeapSecondWarning leapSecondWarning;
    private List<NtpServerResult> servers;

    public static TimeQualityResultBuilder aTimeQualityResult() {
        return new TimeQualityResultBuilder();
    }

    public TimeQualityResultBuilder withDefaults() {
        profile = "rfc3161";
        timestamp = Instant.parse("2026-03-04T10:00:00Z");
        status = TimeQualityStatus.OK;
        measuredDriftMs = 0.;
        reachableServers = 3;
        leapSecondWarning = LeapSecondWarning.NONE;
        servers = List.of();
        return this;
    }

    public TimeQualityResultBuilder profile(String profile) {
        this.profile = profile;
        return this;
    }

    public TimeQualityResultBuilder timestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public TimeQualityResultBuilder status(TimeQualityStatus status) {
        this.status = status;
        return this;
    }

    public TimeQualityResultBuilder measuredDriftMs(Double measuredDriftMs) {
        this.measuredDriftMs = measuredDriftMs;
        return this;
    }

    public TimeQualityResultBuilder reason(String reason) {
        this.reason = reason;
        return this;
    }

    public TimeQualityResultBuilder reachableServers(int reachableServers) {
        this.reachableServers = reachableServers;
        return this;
    }

    public TimeQualityResultBuilder leapSecondWarning(LeapSecondWarning leapSecondWarning) {
        this.leapSecondWarning = leapSecondWarning;
        return this;
    }

    public TimeQualityResultBuilder servers(List<NtpServerResult> servers) {
        this.servers = servers;
        return this;
    }

    public TimeQualityResult build() {
        return new TimeQualityResult(profile, timestamp, status, measuredDriftMs,
                reachableServers, reason, leapSecondWarning, servers);
    }
}
