package com.otilm.core.model.discovery;

import com.otilm.api.model.core.discovery.DiscoveryMessageSeverity;

/**
 * One problem a producer wants recorded against a run, before the writer bounds and aggregates it.
 *
 * <p>
 * <b>{@code message} names the kind of problem and nothing about the occurrence</b> — not a count, not a certificate,
 * not a host, not a reference. Anything run-specific makes every message distinct, which turns aggregation back into
 * accumulation and buries a run's first and most useful entry under its ten thousandth. The count in particular belongs
 * in {@code occurrences}, which adds across every batch reporting the same thing.
 *
 * <p>
 * The rule loses no detail, it puts it where a reader can act on it: why one certificate failed is on that
 * certificate's own row ({@code discovery_certificate.processed_error}), served by the certificate list a run's
 * terminal message points at. Two surfaces, two questions — this one answers what went wrong with the run, the row
 * answers what went wrong with the certificate.
 *
 * @param occurrences how many times this producer saw the problem in the work it is reporting on
 */
public record DiscoveryMessageDraft(DiscoveryMessageSeverity severity, String code, String message, long occurrences) {

    public DiscoveryMessageDraft {
        if (occurrences < 1) {
            throw new IllegalArgumentException("A discovery run message records at least one occurrence");
        }
    }

    public DiscoveryMessageDraft(DiscoveryMessageSeverity severity, DiscoveryMessageCode code, String message,
            long occurrences) {
        this(severity, code.code(), message, occurrences);
    }
}
