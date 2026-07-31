package com.otilm.core.util;

/**
 * Notification provider kinds the platform has to reason about by name. Delivery and profile validation both need
 * to know which providers cannot deliver without recipients, so the kind lives in one place rather than as a
 * literal repeated across the layers that agree on it.
 */
public final class NotificationProviderKinds {

    /** Delivers to e-mail addresses carried by the recipients, so it cannot deliver to none of them. */
    public static final String EMAIL = "EMAIL";

    private NotificationProviderKinds() {
    }

    /**
     * Whether a provider of this kind needs recipients to deliver at all. A webhook posts to the URL configured on
     * the notification instance itself and ignores recipients entirely, so an empty recipient list is its normal
     * input rather than a failure to resolve anyone.
     */
    public static boolean requiresRecipients(String kind) {
        return EMAIL.equals(kind);
    }
}
