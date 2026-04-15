package com.czertainly.core.service.tsa.serialnumber;


import com.czertainly.core.service.tsa.clocksource.ClockSource;

import java.time.Instant;

public final class TestClockSource implements ClockSource {

    long wallTimeMillis;
    long monotonicNanos;

    public static TestClockSource aTestClock() {
        return new TestClockSource(1_709_546_700_000L, 1_000_000_000L);
    }

    public static TestClockSource ofWallTimeMillis(long wallTimeMillis) {
        return new TestClockSource(wallTimeMillis, 1_000_000_000L);
    }

    public static TestClockSource ofWallTime(String instant) {
        return ofWallTimeMillis(Instant.parse(instant).toEpochMilli());
    }

    public static TestClockSource ofWallTime(Instant instant) {
        return ofWallTimeMillis(instant.toEpochMilli());
    }

    private TestClockSource(long wallTimeMillis, long monotonicNanos) {
        this.wallTimeMillis = wallTimeMillis;
        this.monotonicNanos = monotonicNanos;
    }

    public TestClockSource wallTimeMillis(long wallTimeMillis) {
        this.wallTimeMillis = wallTimeMillis;
        return this;
    }

    public TestClockSource monotonicNanos(long monotonicNanos) {
        this.monotonicNanos = monotonicNanos;
        return this;
    }

    public TestClockSource advanceWallMillis(long millis) {
        this.wallTimeMillis += millis;
        return this;
    }

    public TestClockSource advanceMonoNanos(long nanos) {
        this.monotonicNanos += nanos;
        return this;
    }

    @Override
    public long wallTimeMillis() {
        return wallTimeMillis;
    }

    @Override
    public long monotonicNanos() {
        return monotonicNanos;
    }

    @Override
    public Instant wallTimeInstant() {
        return Instant.ofEpochMilli(wallTimeMillis);
    }
}
