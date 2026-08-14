package com.otilm.core.model.discovery;

/**
 * The three kinds of pending work a discovery v2 run can owe, one row each in {@code discovery_work}: polling the
 * connector's run status, draining staged result pages, and processing drained items into inventory. A run holds at
 * most one row per type ({@code uq_discovery_work}); which types coexist is the tick workers' contract, not the
 * table's.
 */
public enum DiscoveryWorkType {
    STATUS,
    DRAIN,
    PROCESS
}
