package com.otilm.core.events.handlers.discovery;

/**
 * Carries an already-shaped, exposure-safe reason out of a group import that has to roll back.
 *
 * <p>Throwing is what rolls the transaction back, so the reason has to survive the throw. Without a type
 * {@link DiscoveryFailureReason} recognises, the caller's own shaping would see an unclassified wrapper and replace
 * the reason it carries with generic text.
 */
public class DiscoveryImportRollbackException extends RuntimeException {

    public DiscoveryImportRollbackException(String shapedReason, Throwable cause) {
        super(shapedReason, cause);
    }
}
