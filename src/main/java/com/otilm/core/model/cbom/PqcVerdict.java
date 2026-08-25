package com.otilm.core.model.cbom;

/**
 * A cryptographic asset's post-quantum readiness, as decided by the versioned rule set that produced it.
 *
 * <p>
 * {@link #NOT_APPLICABLE} and {@link #UNDETERMINED} are distinct on purpose: the first says the rules apply to this
 * asset and find nothing to judge, the second says the asset does not carry the fields the rules need. Collapsing them
 * would make an evidence gap look like a clean bill of health.
 *
 * <p>
 * Core-local placeholder for interfaces#874. The constant names are the persisted values.
 */
public enum PqcVerdict {
    READY,
    NOT_READY,
    NOT_APPLICABLE,
    UNDETERMINED
}
