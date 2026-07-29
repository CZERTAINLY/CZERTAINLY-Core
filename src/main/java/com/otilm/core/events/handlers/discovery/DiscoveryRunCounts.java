package com.otilm.core.events.handlers.discovery;

/**
 * The independent reasons a discovery reports a warning. Each contributes its own sentence to the status message, so
 * two simultaneous partial failures are both visible rather than the first hiding the rest.
 *
 * @param inventoryGaps        certificates the connector reported that are absent from the inventory
 * @param keyGaps              certificates imported without a public key association
 * @param notAttempted         rows that never reached a verdict
 * @param bookkeepingFailures  writes of per-certificate detail that themselves failed, leaving the persisted
 *                             detail knowingly incomplete
 * @param validationNotQueued  the certificates imported but validation of them was never requested -- its own flag,
 *                             since a whole run left unvalidated is nothing like an unrecorded per-certificate reason
 */
public record DiscoveryRunCounts(long inventoryGaps,
                                 long keyGaps,
                                 long notAttempted,
                                 long bookkeepingFailures,
                                 boolean validationNotQueued) {

    public boolean allClear() {
        return inventoryGaps == 0 && keyGaps == 0 && notAttempted == 0 && bookkeepingFailures == 0
                && !validationNotQueued;
    }
}
