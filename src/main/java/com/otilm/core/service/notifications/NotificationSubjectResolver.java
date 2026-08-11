package com.otilm.core.service.notifications;

import com.otilm.api.model.common.events.data.ApprovalEventData;
import com.otilm.api.model.common.events.data.EventData;
import com.otilm.api.model.core.auth.Resource;

import java.util.UUID;

/**
 * Resolves which object a notification's enrichment and subject-scoped recipients describe. Approval events reference a
 * target object in their payload; the approval record itself carries no attributes, owner, or groups, so the target is
 * the meaningful subject. Every other event's subject is the event object itself.
 */
public final class NotificationSubjectResolver {

    public record SubjectRef(Resource resource, UUID objectUuid) {
    }

    private NotificationSubjectResolver() {
    }

    public static SubjectRef resolveSubject(Resource resource, UUID objectUuid, EventData eventData) {
        if (eventData instanceof ApprovalEventData approval && approval.getResource() != null
                && approval.getObjectUuid() != null) {
            return new SubjectRef(approval.getResource(), approval.getObjectUuid());
        }
        return new SubjectRef(resource, objectUuid);
    }
}
