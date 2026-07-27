package com.otilm.core.events.handlers.discovery;

import com.otilm.api.exception.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.security.cert.CertificateException;

/**
 * Turns an exception into text that is safe to expose.
 *
 * <p>{@code processedError} is returned to API clients through {@code DiscoveryCertificate.mapToDto}, so a raw
 * {@code getMessage()} would put SQL fragments, table and column names, and upstream detail on the wire. Only
 * messages the platform itself authored pass through; everything else is classified.
 */
public final class DiscoveryFailureReason {

    private static final String GENERIC = "an unexpected error occurred";

    private DiscoveryFailureReason() {
    }

    public static String shape(Throwable throwable) {
        // Checked before the controlled types: a data-integrity failure carries driver-authored text even when it
        // reaches us wrapped in a platform exception.
        if (throwable instanceof DataIntegrityViolationException) {
            return "a concurrent import committed the same certificate";
        }
        if (throwable instanceof UnexpectedRollbackException) {
            return "the import transaction was rolled back";
        }
        if (isControlled(throwable) && throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return GENERIC;
    }

    private static boolean isControlled(Throwable throwable) {
        return throwable instanceof ValidationException || throwable instanceof CertificateException;
    }
}
