package com.czertainly.core.service.tsa.timequality;

import com.czertainly.core.service.tsa.clocksource.ClockSource;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class MonotonicDriftDetector {
    private final ClockSource clockSource;
    private final ConcurrentHashMap<UUID, AtomicReference<TimeReferencePair>> referencePairs = new ConcurrentHashMap<>();

    public MonotonicDriftDetector(ClockSource clockSource) {
        this.clockSource = clockSource;
    }

    public void captureReference(UUID profileUuid, double measuredDriftMs) {
        refFor(profileUuid).set(
                new TimeReferencePair(clockSource.wallTimeMillis(), clockSource.monotonicNanos(), measuredDriftMs));
    }

    public void clearReference(UUID profileUuid ) {
        refFor(profileUuid).set(null);
    }

    public boolean isDriftExceeded(UUID profileUuid, Duration maxClockDrift) {
        var pair = refFor(profileUuid).get();
        if (pair == null) {
            return true;
        }

        long elapsedNanos = clockSource.monotonicNanos() - pair.monotonicNanos();
        long expectedWallMillis = pair.wallTimeMillis() + (elapsedNanos / 1_000_000);
        long actualWallMillis = clockSource.wallTimeMillis();
        long driftMillis = (actualWallMillis - expectedWallMillis) + (long) pair.measuredDriftMs();
        long maxDriftMillis = maxClockDrift.toMillis();

        if (Math.abs(driftMillis) > maxDriftMillis) {
            log.warn("Clock drift detected: {}ms (max allowed: {}ms)", driftMillis, maxDriftMillis);
            return true;
        }

        return false;
    }

    private AtomicReference<TimeReferencePair> refFor(UUID profileUuid) {
        return referencePairs.computeIfAbsent(profileUuid, ignored -> new AtomicReference<>());
    }
}