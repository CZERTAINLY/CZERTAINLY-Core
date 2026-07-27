package com.otilm.core.events.handlers.discovery;

/**
 * The four independent reasons a discovery reports a warning. Each contributes its own sentence to the status
 * message, so two simultaneous partial failures are both visible rather than the first hiding the rest.
 *
 * @param inventoryGaps        certificates the connector reported that are absent from the inventory
 * @param keyGaps              certificates imported without a public key association
 * @param notAttempted         rows that never reached a verdict
 * @param bookkeepingFailures  writes of per-certificate detail that themselves failed, leaving the persisted
 *                             detail knowingly incomplete
 */
public record DiscoveryRunCounts(long inventoryGaps,
                                 long keyGaps,
                                 long notAttempted,
                                 long bookkeepingFailures) {

    public boolean allClear() {
        return inventoryGaps == 0 && keyGaps == 0 && notAttempted == 0 && bookkeepingFailures == 0;
    }
}
