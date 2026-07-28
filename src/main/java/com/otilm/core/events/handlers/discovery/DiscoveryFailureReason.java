package com.otilm.core.events.handlers.discovery;

import com.otilm.api.exception.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.security.cert.CertificateException;

/**
 * Turns an exception into text that is safe to expose.
 *
 * <p>{@code processedError} is returned to API clients through {@code DiscoveryCertificate.mapToDto}, so a raw
 * {@code getMessage()} would put SQL fragments, table and column names, and provider internals on the wire. Only
 * {@link ValidationException}, whose messages the platform authors itself, passes through; every other failure is
 * classified.
 */
public final class DiscoveryFailureReason {

    private static final String GENERIC = "an unexpected error occurred";
    private static final int MAX_CAUSE_DEPTH = 10;

    private DiscoveryFailureReason() {
    }

    public static String shape(Throwable throwable) {
        // Walk the causes: a data-integrity failure usually arrives wrapped, and if the wrapper's own message
        // embeds the driver text, passing that through would leak exactly what this class exists to withhold.
        Throwable cause = throwable;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; cause = cause.getCause(), depth++) {
            String classified = classify(cause);
            if (classified != null) {
                return classified;
            }
        }
        // Blank-checked, not just null-checked: ValidationException stringifies a null message, so a
        // message-less one yields the literal "null", which would read as a bug in the certificate list.
        if (throwable instanceof ValidationException && isUsable(throwable.getMessage())) {
            return throwable.getMessage();
        }
        return GENERIC;
    }

    private static boolean isUsable(String message) {
        return message != null && !message.isBlank() && !"null".equals(message.trim());
    }

    private static String classify(Throwable throwable) {
        if (throwable instanceof DataIntegrityViolationException) {
            return "a concurrent import committed the same certificate";
        }
        if (throwable instanceof UnexpectedRollbackException) {
            return "the import transaction was rolled back";
        }
        // Parser messages come from the JDK and BouncyCastle, not from the platform, so they are classified
        // rather than forwarded.
        if (throwable instanceof CertificateException) {
            return "the discovered certificate could not be parsed";
        }
        return null;
    }
}
