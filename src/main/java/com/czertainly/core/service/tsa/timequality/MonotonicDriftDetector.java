package com.czertainly.core.service.tsa.timequality;

import com.czertainly.core.service.tsa.clocksource.ClockSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MonotonicDriftDetector {

    private static final Logger logger = LoggerFactory.getLogger(MonotonicDriftDetector.class);

    private final ClockSource clockSource;
    private final ConcurrentHashMap<String, AtomicReference<TimeReferencePair>> referencePairs = new ConcurrentHashMap<>();

    public MonotonicDriftDetector(ClockSource clockSource) {
        this.clockSource = clockSource;
    }

    public void captureReference(String profile, double measuredDriftMs) {
        refFor(profile).set(
                new TimeReferencePair(clockSource.wallTimeMillis(), clockSource.monotonicNanos(), measuredDriftMs));
    }

    public void clearReference(String profile) {
        refFor(profile).set(null);
    }

    public boolean isDriftExceeded(String profile, Duration maxClockDrift) {
        var pair = refFor(profile).get();
        if (pair == null) {
            return true;
        }

        long elapsedNanos = clockSource.monotonicNanos() - pair.monotonicNanos();
        long expectedWallMillis = pair.wallTimeMillis() + (elapsedNanos / 1_000_000);
        long actualWallMillis = clockSource.wallTimeMillis();
        long driftMillis = (actualWallMillis - expectedWallMillis) + (long) pair.measuredDriftMs();
        long maxDriftMillis = maxClockDrift.toMillis();

        if (Math.abs(driftMillis) > maxDriftMillis) {
            logger.warn("Clock drift detected: {}ms (max allowed: {}ms)", driftMillis, maxDriftMillis);
            return true;
        }

        return false;
    }

    private AtomicReference<TimeReferencePair> refFor(String profile) {
        return referencePairs.computeIfAbsent(profile, ignored -> new AtomicReference<>());
    }
}