package com.czertainly.core.service.tsa.clocksource;

import java.time.Instant;

public interface ClockSource {

    long wallTimeMillis();

    long monotonicNanos();

    Instant wallTimeInstant();
}
