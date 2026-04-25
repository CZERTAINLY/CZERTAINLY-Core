package com.czertainly.core.service.tsa.timequality;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimeQualityResult(
        UUID profileUuid,
        String profileName,
        Instant timestamp,
        TimeQualityStatus status,
        Double measuredDriftMs,
        int reachableServers,
        String reason,
        LeapSecondWarning leapSecondWarning,
        List<NtpServerResult> servers) {
}
