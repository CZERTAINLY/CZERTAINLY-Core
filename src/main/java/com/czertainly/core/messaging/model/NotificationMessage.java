package com.czertainly.core.messaging.model;

import com.czertainly.api.model.connector.notification.data.NotificationDataApproval;
import com.czertainly.api.model.connector.notification.data.NotificationDataScheduledJobCompleted;
import com.czertainly.api.model.connector.notification.data.NotificationDataCertificateStatusChanged;
import com.czertainly.api.model.connector.notification.data.NotificationDataText;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.core.auth.Resource;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class NotificationMessage {

    private NotificationType type;

    private Resource resource;

    private UUID resourceUUID;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NotificationDataText.class, name = "TEXT"),
            @JsonSubTypes.Type(value = NotificationDataCertificateStatusChanged.class, name = "CERTIFICATE_STATUS_CHANGED"),
            @JsonSubTypes.Type(value = NotificationDataScheduledJobCompleted.class, name = "SCHEDULED_JOB_COMPLETED"),
            @JsonSubTypes.Type(value = NotificationDataApproval.class, name = "APPROVAL_REQUESTED"),
            @JsonSubTypes.Type(value = NotificationDataApproval.class, name = "APPROVAL_CLOSED")
    })
    private Object data;

    private List<NotificationRecipient> recipients;

    public NotificationMessage(NotificationType type, Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, Object data) {
        this.type = type;
        this.resource = resource;
        this.resourceUUID = resourceUUID;
        this.recipients = recipients;
        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("NotificationMessage (%s, %s, %s, %s, %s)", type, resource, resourceUUID, recipients, data);
    }
}
