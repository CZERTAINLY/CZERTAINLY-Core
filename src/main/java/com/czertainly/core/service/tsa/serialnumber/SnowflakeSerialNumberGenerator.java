package com.czertainly.core.service.tsa.serialnumber;


import com.czertainly.core.service.tsa.clocksource.ClockSource;

import java.math.BigInteger;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

final class SnowflakeSerialNumberGenerator implements SerialNumberGenerator {

    static final long EPOCH_MILLIS = Instant.parse("2026-02-01T00:00:00Z").toEpochMilli();
    static final int TICK_MS = 10;

    private final ClockSource clockSource;
    private final int instanceId;
    private final long maxClockDriftMs;
    private final ReentrantLock lock = new ReentrantLock();

    private long lastTick = -1;
    private int sequence = 0;

    SnowflakeSerialNumberGenerator(ClockSource clockSource, int instanceId) {
        if (instanceId < 0 || instanceId > 0xFFFF) {
            throw new IllegalArgumentException("instanceId must be 0–65535, got: " + instanceId);
        }
        this.clockSource = clockSource;
        this.instanceId = instanceId;
        this.maxClockDriftMs = 100;
    }

    @Override
    public BigInteger generate() {
        lock.lock();
        try {
            long currentTick = computeTick();

            if (currentTick < lastTick) {
                currentTick = waitForClockCatchUp(currentTick);
            }

            if (currentTick == lastTick) {
                sequence++;
                if (sequence > 255) {
                    currentTick = waitForNextTick(currentTick);
                    sequence = 0;
                }
            } else {
                sequence = 0;
            }

            lastTick = currentTick;
            return BigInteger.valueOf((currentTick << 24) | ((long) instanceId << 8) | sequence);
        } finally {
            lock.unlock();
        }
    }

    private long computeTick() {
        return (clockSource.wallTimeMillis() - EPOCH_MILLIS) / TICK_MS;
    }

    private long waitForClockCatchUp(long currentTick) {
        long driftMs = (lastTick - currentTick) * TICK_MS;
        if (driftMs > maxClockDriftMs) {
            throw new ClockDriftException(
                    "Clock moved backward by " + driftMs + " ms, exceeding max drift of " + maxClockDriftMs + " ms");
        }

        while (currentTick < lastTick) {
            Thread.onSpinWait();
            currentTick = computeTick();
        }
        return currentTick;
    }

    private long waitForNextTick(long currentTick) {
        long nextTick = currentTick + 1;
        while (computeTick() < nextTick) {
            Thread.onSpinWait();
        }
        return nextTick;
    }
}
