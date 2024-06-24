package com.czertainly.core.messaging.producers;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approval.ApprovalDto;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.connector.notification.data.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.util.AuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class NotificationProducer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProducer.class);

    private RabbitTemplate rabbitTemplate;

    private UserManagementApiClient userManagementApiClient;

    @Autowired
    public void setRabbitTemplate(final RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Autowired
    public void setUserManagementApiClient(UserManagementApiClient userManagementApiClient) {
        this.userManagementApiClient = userManagementApiClient;
    }

    protected void produceMessage(final NotificationMessage notificationMessage) {
        if (notificationMessage.getRecipients() == null || notificationMessage.getRecipients().isEmpty()) {
            logger.warn("Recipients for notification {} is empty. Message: {}", notificationMessage.getType(), notificationMessage);
        } else {
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.NOTIFICATION_ROUTING_KEY, notificationMessage);
        }
    }

    public void produceNotification(NotificationType type, Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, Object data) {
        produceMessage(new NotificationMessage(type, resource, resourceUUID, recipients, data));
    }

    public void produceNotificationCertificateStatusChanged(CertificateValidationStatus oldStatus, CertificateValidationStatus newStatus, CertificateDto certificateDto) {
        if (certificateDto.getOwnerUuid() == null && certificateDto.getGroups() == null) {
            return;
        }

        List<NotificationRecipient> recipients = NotificationRecipient.buildUsersAndGroupsNotificationRecipients(certificateDto.getOwnerUuid() == null ? null : List.of(UUID.fromString(certificateDto.getOwnerUuid())), certificateDto.getGroups() == null ? null : certificateDto.getGroups().stream().map(g -> UUID.fromString(g.getUuid())).toList());

        logger.debug("Sending notification of certificate status change. Certificate: {}", certificateDto.getUuid());
        produceMessage(new NotificationMessage(NotificationType.CERTIFICATE_STATUS_CHANGED,
                Resource.CERTIFICATE, UUID.fromString(certificateDto.getUuid()), recipients,
                certificateDto.getRaProfile() == null ? new NotificationDataCertificateStatusChanged(oldStatus.getLabel(), newStatus.getLabel(), certificateDto.getUuid(), certificateDto.getFingerprint(), certificateDto.getSerialNumber(), certificateDto.getSubjectDn(), certificateDto.getIssuerDn(), certificateDto.getCommonName(), certificateDto.getNotBefore().toString(), certificateDto.getNotAfter().toString())
                        : new NotificationDataCertificateStatusChanged(oldStatus.getLabel(), newStatus.getLabel(), certificateDto.getUuid(), certificateDto.getFingerprint(), certificateDto.getSerialNumber(), certificateDto.getSubjectDn(), certificateDto.getIssuerDn(), certificateDto.getRaProfile().getAuthorityInstanceUuid(), certificateDto.getRaProfile().getUuid(), certificateDto.getRaProfile().getName(), certificateDto.getCommonName(), certificateDto.getNotBefore().toString(), certificateDto.getNotAfter().toString())));
    }

    public void produceNotificationCertificateActionPerformed(CertificateDto certificateDto, ResourceAction action, String errorMessage) {
        if (certificateDto.getOwnerUuid() == null && certificateDto.getGroups() == null) {
            return;
        }

        List<NotificationRecipient> recipients = NotificationRecipient.buildUsersAndGroupsNotificationRecipients(certificateDto.getOwnerUuid() == null ? null : List.of(UUID.fromString(certificateDto.getOwnerUuid())), certificateDto.getGroups() == null ? null : certificateDto.getGroups().stream().map(g -> UUID.fromString(g.getUuid())).toList());

        logger.debug("Sending notification of certificate action {} performed. Certificate: {}", action.getCode(), certificateDto.getUuid());
        produceMessage(new NotificationMessage(NotificationType.CERTIFICATE_ACTION_PERFORMED,
                Resource.CERTIFICATE, UUID.fromString(certificateDto.getUuid()), recipients,
                new NotificationDataCertificateActionPerformed(action.getCode(), certificateDto.getUuid(), certificateDto.getFingerprint(), certificateDto.getSerialNumber(), certificateDto.getSubjectDn(), certificateDto.getIssuerDn(), certificateDto.getRaProfile() != null ? certificateDto.getRaProfile().getAuthorityInstanceUuid() : null, certificateDto.getRaProfile() != null ? certificateDto.getRaProfile().getUuid() : null, certificateDto.getRaProfile() != null ? certificateDto.getRaProfile().getName() : null,
                        certificateDto.getNotAfter().toString(), errorMessage)));
    }

    public void produceNotificationScheduledJobCompleted(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, String jobName, String jobType, String status) {
        produceMessage(new NotificationMessage(NotificationType.SCHEDULED_JOB_COMPLETED,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataScheduledJobCompleted(jobName, jobType, status)));
    }

    public void produceNotificationText(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, String text, String detail) {
        produceMessage(new NotificationMessage(NotificationType.OTHER,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataText(text, detail)));
    }

    public void produceNotificationApprovalRequested(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, ApprovalDto approvalDto, String creatorUuid) {
        produceMessage(new NotificationMessage(NotificationType.APPROVAL_REQUESTED,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataApproval(approvalDto.getApprovalUuid(), approvalDto.getApprovalProfileUuid(), approvalDto.getApprovalProfileName(), approvalDto.getVersion(), approvalDto.getStatus(), approvalDto.getExpiryAt(),
                        approvalDto.getClosedAt(), approvalDto.getResource(), approvalDto.getResourceAction(), approvalDto.getObjectUuid(), creatorUuid, getCreatorUsername(creatorUuid))));
    }

    public void produceNotificationApprovalClosed(Resource resource, UUID resourceUUID, List<NotificationRecipient> recipients, ApprovalDto approvalDto, String creatorUuid) {
        produceMessage(new NotificationMessage(NotificationType.APPROVAL_CLOSED,
                resource,
                resourceUUID,
                recipients,
                new NotificationDataApproval(approvalDto.getApprovalUuid(), approvalDto.getApprovalProfileUuid(), approvalDto.getApprovalProfileName(), approvalDto.getVersion(), approvalDto.getStatus(), approvalDto.getExpiryAt(),
                        approvalDto.getClosedAt(), approvalDto.getResource(), approvalDto.getResourceAction(), approvalDto.getObjectUuid(), creatorUuid, getCreatorUsername(creatorUuid))));
    }

    private String getCreatorUsername(String creatorUuid) {
        // check first if creator is not logged in now
        try {
            UserProfileDto userProfileDto = AuthHelper.getUserProfile();
            if (userProfileDto.getUser().getUuid().equals(creatorUuid)) return userProfileDto.getUser().getUsername();
        } catch (ValidationException e) {
            // anonymous user, retrieve user details
        }

        try {
            UserDetailDto userDetailDto = userManagementApiClient.getUserDetail(creatorUuid);
            return userDetailDto.getUsername();
        } catch (Exception e) {
            // in case Auth service call fails, return just creator UUID
            // TODO: mostly problem in tests, need mock of Auth service in tests scope
            return creatorUuid;
        }
    }

}
