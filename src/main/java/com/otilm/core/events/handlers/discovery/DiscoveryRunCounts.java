package com.otilm.core.events.handlers.discovery;

import java.util.ArrayList;
import java.util.List;

/**
 * The independent reasons a discovery reports a warning. Each contributes its own sentence to the status message, so
 * two simultaneous partial failures are both visible rather than the first hiding the rest.
 *
 * @param inventoryGaps certificates the connector reported that are absent from the inventory
 * @param keyGaps certificates imported without a public key association
 * @param notAttempted rows that never reached a verdict
 * @param bookkeepingFailures writes of per-certificate detail that themselves failed, leaving the persisted detail
 * knowingly incomplete
 * @param validationNotQueued the certificates imported but validation of them was never requested -- its own flag,
 * since a whole run left unvalidated is nothing like an unrecorded per-certificate reason
 */
public record DiscoveryRunCounts(long inventoryGaps, long keyGaps, long notAttempted, long bookkeepingFailures,
        boolean validationNotQueued) {

    public boolean allClear() {
        return inventoryGaps == 0 && keyGaps == 0 && notAttempted == 0 && bookkeepingFailures == 0
                && !validationNotQueued;
    }

    /**
     * One sentence per reason this unit of work fell short, in a fixed order. Empty when nothing did.
     *
     * <p>
     * Shared by the two flows that report gaps at different granularities: the v1 pass counts a whole run and turns
     * these into its final status message, while a v2 {@code PROCESS} batch counts only itself and files them in the
     * run's message log as it goes. Both say the same thing about the same counts.
     */
    public List<String> describeGaps() {
        List<String> sentences = new ArrayList<>();
        if (inventoryGaps > 0) {
            sentences.add("%d certificate(s) could not be imported into the inventory.".formatted(inventoryGaps));
        }
        if (keyGaps > 0) {
            sentences
                    .add("%d certificate(s) were imported without all of their public keys associated."
                            .formatted(keyGaps));
        }
        if (notAttempted > 0) {
            sentences.add("%d certificate(s) could not be processed to a result.".formatted(notAttempted));
        }
        if (bookkeepingFailures > 0) {
            sentences.add("Some per-certificate detail could not be recorded.");
        }
        if (validationNotQueued) {
            sentences.add("Validation of the discovered certificates could not be requested.");
        }
        return sentences;
    }

    /**
     * Whether any gap points at rows that carry a reason of their own. A bookkeeping failure means the detail never got
     * written, and validation never being requested says nothing about any individual row — pointing a reader at the
     * certificate list for either would contradict the sentence before it.
     */
    public boolean hasPerCertificateDetail() {
        return inventoryGaps + keyGaps + notAttempted > 0;
    }
}
