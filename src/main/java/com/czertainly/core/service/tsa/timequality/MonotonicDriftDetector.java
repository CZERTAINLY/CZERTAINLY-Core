package com.czertainly.core.service.tsa.timequality;

import com.czertainly.core.service.tsa.clocksource.ClockSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MonotonicDriftDetector {

    private static final Logger logger = LoggerFactory.getLogger(MonotonicDriftDetector.class);

    private final ClockSource clockSource;
    private final ConcurrentHashMap<String, AtomicReference<TimeReferencePair>> referencePairs;

    public MonotonicDriftDetector(ClockSource clockSource, Set<String> profileNames) {
        this.clockSource = clockSource;
        this.referencePairs = new ConcurrentHashMap<>();
        for (var name : profileNames) {
            referencePairs.put(name, new AtomicReference<>());
        }
    }

    public void captureReference(String profile, double measuredDriftMs) {
        referencePairs.get(profile).set(
                new TimeReferencePair(clockSource.wallTimeMillis(), clockSource.monotonicNanos(), measuredDriftMs));
    }

    public void clearReference(String profile) {
        referencePairs.get(profile).set(null);
    }

    public boolean isDriftExceeded(String profile, Duration maxClockDrift) {
        var pair = referencePairs.get(profile).get();
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
}
