package com.otilm.core.service.notifications;

import com.otilm.api.model.core.auth.Resource;

import java.util.Optional;
import java.util.UUID;

/**
 * Exports a resource's canonical content representation for the notification OBJECT_CONTENT
 * category. The set of registered exporters is the fail-closed whitelist: no resource's content
 * is exportable unless an exporter is deliberately registered for it. A new exporter requires
 * its own design covering what is exported (public material only), which permissions join the
 * config-time gate in {@link NotificationDataCategoryGate}, and the activation policy for
 * profiles that already have the category enabled.
 */
public interface NotificationObjectContentExporter {

    /** The resource type this exporter serves. */
    Resource resource();

    /** Wire format identifier of the exported content, e.g. {@code X509_DER_BASE64}. */
    String format();

    /**
     * The subject's content, or empty when the object does not exist or carries no content yet
     * -- absence is best-effort, never an error.
     */
    Optional<String> export(UUID objectUuid);
}
