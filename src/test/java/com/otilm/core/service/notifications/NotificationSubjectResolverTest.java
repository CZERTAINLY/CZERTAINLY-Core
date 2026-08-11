package com.otilm.core.service.notifications;

import com.otilm.api.model.common.events.data.ApprovalEventData;
import com.otilm.api.model.common.events.data.CertificateExpiringEventData;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.service.notifications.NotificationSubjectResolver.SubjectRef;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationSubjectResolverTest {

    @Test
    void ordinaryEventsResolveToTheEventObject() {
        UUID certificateUuid = UUID.randomUUID();

        SubjectRef subject = NotificationSubjectResolver
                .resolveSubject(Resource.CERTIFICATE, certificateUuid, new CertificateExpiringEventData());

        assertEquals(new SubjectRef(Resource.CERTIFICATE, certificateUuid), subject);
    }

    @Test
    void approvalEventsResolveToTheApprovalTarget() {
        UUID approvalUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        ApprovalEventData approval = new ApprovalEventData();
        approval.setResource(Resource.CERTIFICATE);
        approval.setObjectUuid(targetUuid);

        SubjectRef subject = NotificationSubjectResolver.resolveSubject(Resource.APPROVAL, approvalUuid, approval);

        assertEquals(new SubjectRef(Resource.CERTIFICATE, targetUuid), subject);
    }

    @Test
    void approvalWithoutTargetCoordinatesFallsBackToTheEventObject() {
        UUID approvalUuid = UUID.randomUUID();
        ApprovalEventData approval = new ApprovalEventData();
        approval.setResource(null);
        approval.setObjectUuid(UUID.randomUUID());

        SubjectRef subject = NotificationSubjectResolver.resolveSubject(Resource.APPROVAL, approvalUuid, approval);

        assertEquals(new SubjectRef(Resource.APPROVAL, approvalUuid), subject);

        approval.setResource(Resource.CERTIFICATE);
        approval.setObjectUuid(null);
        subject = NotificationSubjectResolver.resolveSubject(Resource.APPROVAL, approvalUuid, approval);

        assertEquals(new SubjectRef(Resource.APPROVAL, approvalUuid), subject);
    }

    @Test
    void nullEventDataResolvesToTheEventObject() {
        UUID objectUuid = UUID.randomUUID();

        SubjectRef subject = NotificationSubjectResolver.resolveSubject(Resource.DISCOVERY, objectUuid, null);

        assertEquals(new SubjectRef(Resource.DISCOVERY, objectUuid), subject);
    }
}
