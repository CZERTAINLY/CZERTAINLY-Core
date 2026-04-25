package com.czertainly.core.service.tsa.timequality.builders;

import com.czertainly.core.model.signing.timequality.ExplicitTimeQualityConfiguration;
import com.czertainly.core.service.tsa.timequality.LeapSecondWarning;
import com.czertainly.core.service.tsa.timequality.NtpServerResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


public final class TimeQualityResultBuilder {

    private UUID profileUuid;
    private String profileName;
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
        profileUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        profileName = "rfc3161";
        timestamp = Instant.parse("2026-03-04T10:00:00Z");
        status = TimeQualityStatus.OK;
        measuredDriftMs = 0.;
        reachableServers = 3;
        leapSecondWarning = LeapSecondWarning.NONE;
        servers = List.of();
        return this;
    }

    public TimeQualityResultBuilder profileUuid(UUID profileUuid) {
        this.profileUuid = profileUuid;
        return this;
    }

    public TimeQualityResultBuilder profile(ExplicitTimeQualityConfiguration config) {
        this.profileUuid = config.getUuid();
        this.profileName = config.getName();
        return this;
    }

    public TimeQualityResultBuilder profileName(String profileName) {
        this.profileName = profileName;
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
        return new TimeQualityResult(profileUuid, profileName, timestamp, status, measuredDriftMs,
                reachableServers, reason, leapSecondWarning, servers);
    }
}
