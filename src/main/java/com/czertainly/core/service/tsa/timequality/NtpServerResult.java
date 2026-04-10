package com.czertainly.core.service.tsa.timequality;

public record NtpServerResult(
        String host,
        boolean reachable,
        Double offsetMs,
        Double rttMs,
        Integer stratum,
        Double precisionMs) {
}
