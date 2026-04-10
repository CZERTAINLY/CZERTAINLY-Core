package com.czertainly.core.service.tsa.timequality;


import java.time.Instant;
import java.util.List;

public record TimeQualityResult(
        String profile,
        Instant timestamp,
        TimeQualityStatus status,
        Double measuredDriftMs,
        int reachableServers,
        String reason,
        LeapSecondWarning leapSecondWarning,
        List<NtpServerResult> servers) {
}
