package com.otilm.core.model.discovery;

import com.otilm.api.model.core.discovery.DiscoveryStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Run-level bookkeeping shared by the ingestor and the tick workers: which statuses are final, and how a run's
 * user-visible message log grows.
 */
public final class DiscoveryRunLifecycle {

    /**
     * A run in one of these has finished for good. Nothing may schedule work for it, and no later connector answer
     * moves it — the terminal transition already released the connector's run context.
     */
    private static final Set<DiscoveryStatus> TERMINAL = EnumSet
            .of(DiscoveryStatus.COMPLETED, DiscoveryStatus.WARNING, DiscoveryStatus.FAILED, DiscoveryStatus.CANCELLED);

    /**
     * Cap on {@code run_messages}. A run that fails per item can produce one line per item, and the column is a single
     * JSONB value rewritten on every append — unbounded growth would make each append quadratic in the failure count.
     */
    static final int MAX_RUN_MESSAGES = 200;

    private DiscoveryRunLifecycle() {
    }

    public static boolean isTerminal(DiscoveryStatus status) {
        return status != null && TERMINAL.contains(status);
    }

    /**
     * Whether the connector has stopped owning the run — it has either finished for good, or handed everything over and
     * been released at the swap into {@code PROCESSING}.
     *
     * <p>
     * The distinction from {@link #isTerminal} is load-bearing for the connector-driven ticks. A {@code PROCESSING} run
     * is very much alive, but its {@code run_meta} was nulled when the drain handed over, so a {@code STATUS} or
     * {@code DRAIN} tick still in flight from before the swap would call the connector with no handle — and read the
     * resulting 404 as "this run no longer exists", ending a healthy run mid-import. Anything that talks to the
     * connector, or schedules work that will, asks this rather than {@code isTerminal}.
     */
    public static boolean hasLeftTheConnector(DiscoveryStatus status) {
        return isTerminal(status) || status == DiscoveryStatus.PROCESSING;
    }

    /**
     * Returns the run's message log with {@code messages} appended, oldest lines dropped once the cap is reached.
     *
     * <p>
     * A new list rather than a mutation of {@code current}: the value is a JSONB column, so Hibernate detects the
     * change by comparing instances and an in-place edit can go unwritten.
     */
    public static List<String> append(List<String> current, List<String> messages) {
        List<String> merged = current == null ? new ArrayList<>() : new ArrayList<>(current);
        merged.addAll(messages);
        int overflow = merged.size() - MAX_RUN_MESSAGES;
        return overflow <= 0 ? merged : new ArrayList<>(merged.subList(overflow, merged.size()));
    }

    public static List<String> append(List<String> current, String message) {
        return append(current, List.of(message));
    }
}
