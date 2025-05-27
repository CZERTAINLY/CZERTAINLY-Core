package com.czertainly.core.messaging.listeners;

import com.czertainly.api.clients.NotificationInstanceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.notification.RecipientDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.events.data.*;
import com.czertainly.api.model.connector.notification.NotificationProviderNotifyRequestDto;
import com.czertainly.api.model.connector.notification.NotificationRecipientDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.auth.RoleDetailDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.notifications.*;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.notifications.NotificationInstanceReferenceRepository;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.core.dao.repository.notifications.NotificationProfileVersionRepository;
import com.czertainly.core.dao.repository.notifications.PendingNotificationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.NotificationService;
import com.czertainly.core.service.ResourceObjectAssociationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@Transactional
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);
    private static final String EMAIL_NOTIFICATION_PROVIDER_KIND = "EMAIL";

    private ObjectMapper mapper;
    private AttributeEngine attributeEngine;

    private NotificationService notificationService;
    private NotificationInstanceApiClient notificationInstanceApiClient;
    private PendingNotificationRepository pendingNotificationRepository;
    private NotificationProfileVersionRepository notificationProfileVersionRepository;
    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;

    private GroupRepository groupRepository;
    private UserManagementApiClient userManagementApiClient;
    private RoleManagementApiClient roleManagementApiClient;
    private ResourceObjectAssociationService resourceObjectAssociationService;

    private static final Map<ResourceEvent, String> eventToLegacyNotificationTypeMapping = new EnumMap<>(ResourceEvent.class);

    static {
        eventToLegacyNotificationTypeMapping.put(ResourceEvent.CERTIFICATE_STATUS_CHANGED, "certificate_status_changed");
        eventToLegacyNotificationTypeMapping.put(ResourceEvent.CERTIFICATE_ACTION_PERFORMED, "certificate_action_performed");
        eventToLegacyNotificationTypeMapping.put(ResourceEvent.APPROVAL_REQUESTED, "approval_requested");
        eventToLegacyNotificationTypeMapping.put(ResourceEvent.APPROVAL_CLOSED, "approval_closed");
        eventToLegacyNotificationTypeMapping.put(ResourceEvent.SCHEDULED_JOB_FINISHED, "scheduled_job_completed");
    }

    @Autowired
    public void setObjectMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired
    public void setNotificationInstanceApiClient(NotificationInstanceApiClient notificationInstanceApiClient) {
        this.notificationInstanceApiClient = notificationInstanceApiClient;
    }

    @Autowired
    public void setPendingNotificationRepository(PendingNotificationRepository pendingNotificationRepository) {
        this.pendingNotificationRepository = pendingNotificationRepository;
    }

    @Autowired
    public void setNotificationProfileVersionRepository(NotificationProfileVersionRepository notificationProfileVersionRepository) {
        this.notificationProfileVersionRepository = notificationProfileVersionRepository;
    }

    @Autowired
    public void setNotificationInstanceReferenceRepository(NotificationInstanceReferenceRepository notificationInstanceReferenceRepository) {
        this.notificationInstanceReferenceRepository = notificationInstanceReferenceRepository;
    }

    @Autowired
    public void setGroupRepository(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Autowired
    public void setUserManagementApiClient(UserManagementApiClient userManagementApiClient) {
        this.userManagementApiClient = userManagementApiClient;
    }

    @Autowired
    public void setRoleManagementApiClient(RoleManagementApiClient roleManagementApiClient) {
        this.roleManagementApiClient = roleManagementApiClient;
    }

    @Autowired
    public void setResourceObjectAssociationService(ResourceObjectAssociationService resourceObjectAssociationService) {
        this.resourceObjectAssociationService = resourceObjectAssociationService;
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_NOTIFICATIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(NotificationMessage message) {
        logger.debug("Received notification message: {}", message);

        if (message.getNotificationProfileUuids() == null) {
            try {
                sendInternalNotifications(message.getRecipients(), getInternalNotificationData(message), message.getResource(), message.getObjectUuid());
            } catch (ValidationException e) {
                logger.error("Error in internal notification: {}", e.toString());
            }
        } else {
            for (UUID notificationProfileUuid : message.getNotificationProfileUuids()) {
                try {
                    sendByNotificationProfile(notificationProfileUuid, message);
                } catch (Exception e) {
                    logger.error("Error in sending notifications based on notification profile {}: {}", notificationProfileUuid, e.getMessage());
                }
            }

        }

        logger.debug("Notification message handled");
    }

    private void sendByNotificationProfile(UUID notificationProfileUuid, NotificationMessage message) throws NotFoundException {
        NotificationProfileVersion notificationProfileVersion;
        PendingNotification pendingNotification = pendingNotificationRepository.findByNotificationProfileUuidAndResourceAndObjectUuidAndEvent(notificationProfileUuid, message.getResource(), message.getObjectUuid(), message.getEvent());
        if (pendingNotification == null) {
            notificationProfileVersion = notificationProfileVersionRepository.findTopByNotificationProfileUuidOrderByVersionDesc(notificationProfileUuid).orElseThrow(() -> new NotFoundException(NotificationProfile.class, notificationProfileUuid));
            if (notificationProfileVersion.getFrequency() != null || notificationProfileVersion.getRepetitions() != null) {
                pendingNotification = new PendingNotification();
                pendingNotification.setNotificationProfileUuid(notificationProfileVersion.getNotificationProfileUuid());
                pendingNotification.setVersion(notificationProfileVersion.getVersion());
                pendingNotification.setEvent(message.getEvent());
                pendingNotification.setResource(message.getResource());
                pendingNotification.setObjectUuid(message.getObjectUuid());
            }
        } else {
            notificationProfileVersion = notificationProfileVersionRepository.findByNotificationProfileUuidAndVersion(notificationProfileUuid, pendingNotification.getVersion()).orElseThrow(() -> new NotFoundException(NotificationProfile.class, notificationProfileUuid));
        }

        if (!proceedWithNotifying(notificationProfileVersion, pendingNotification)) {
            logger.debug("Notification suppressed by configuration of notification profile {} for event {}. Notification sent last time at {} and was repeated {} times.", notificationProfileVersion.getNotificationProfile().getName(), message.getEvent(), pendingNotification.getLastSentAt(), pendingNotification.getRepetitions());
            return;
        }

        List<NotificationRecipient> recipients = getRecipients(notificationProfileVersion.getRecipientType(), notificationProfileVersion.getRecipientUuids(), message.getEvent(), message.getData(), message.getResource(), message.getObjectUuid());

        // send external notification
        boolean notificationSent = false;
        if (notificationProfileVersion.getNotificationInstanceRefUuid() != null) {
            UUID notificationInstanceUUID = notificationProfileVersion.getNotificationInstanceRefUuid();
            logger.debug("Sending notification message externally. Notification instance UUID: {}", notificationInstanceUUID);
            try {
                sendExternalNotifications(notificationInstanceUUID, recipients, message.getData(), message.getEvent(), message.getResource());
                logger.debug("Sending notification message externally successful.");
                notificationSent = true;
            } catch (ConnectorEntityNotFoundException e) {
                logger.warn("Notification instance {} configured for notification profile {} in event {} was not found.", notificationInstanceUUID, notificationProfileVersion.getNotificationProfile().getName(), message.getEvent());
            } catch (ValidationException e) {
                logger.warn("Validation error in sending notification to connector of notification instance {} configured for notification profile {} in event {}: {}", notificationInstanceUUID, notificationProfileVersion.getNotificationProfile().getName(), message.getEvent(), e.getMessage());
            } catch (Exception e) {
                logger.error("Error in external notification with notification instance {} configured for notification profile {} in event {}: {}", notificationInstanceUUID, notificationProfileVersion.getNotificationProfile().getName(), message.getEvent(), e.toString());
            }
        }

        // send internal notification when not Default recipient type
        if (notificationProfileVersion.isInternalNotification() && notificationProfileVersion.getRecipientType() != RecipientType.DEFAULT) {
            try {
                sendInternalNotifications(recipients, getInternalNotificationData(message), message.getResource(), message.getObjectUuid());
                notificationSent = true;
            } catch (ValidationException e) {
                logger.error("Error in internal notification: {}", e.toString());
            }
        }

        if (pendingNotification != null && notificationSent) {
            pendingNotification.setRepetitions(pendingNotification.getRepetitions() + 1);
            pendingNotificationRepository.save(pendingNotification);
        }
    }

    private boolean proceedWithNotifying(NotificationProfileVersion notificationProfileVersion, PendingNotification pendingNotification) {
        if (pendingNotification == null) {
            return true;
        }

        OffsetDateTime now = OffsetDateTime.now();
        return (notificationProfileVersion.getFrequency() == null || pendingNotification.getLastSentAt() == null || Duration.between(pendingNotification.getLastSentAt(), now).getNano() > notificationProfileVersion.getFrequency().getNano())
                && (notificationProfileVersion.getRepetitions() == null || pendingNotification.getRepetitions() < notificationProfileVersion.getRepetitions());
    }

    private List<NotificationRecipient> getRecipients(RecipientType recipientType, List<UUID> recipientUuids, ResourceEvent event, Object data, Resource resource, UUID objectUuid) {
        if (recipientType != RecipientType.OWNER && recipientType != RecipientType.DEFAULT) {
            return recipientUuids.stream().map(recipientUuid -> new NotificationRecipient(recipientType, recipientUuid)).toList();
        }

        if (recipientType == RecipientType.OWNER) {
            NameAndUuidDto ownerInfo = resourceObjectAssociationService.getOwner(resource, objectUuid);
            if (ownerInfo == null) return List.of();
            return List.of(new NotificationRecipient(RecipientType.USER, UUID.fromString(ownerInfo.getUuid())));
        }

        return getDefaultRecipients(event, data, resource, objectUuid);
    }

    private List<NotificationRecipient> getDefaultRecipients(ResourceEvent event, Object data, Resource resource, UUID objectUuid) {
        List<NotificationRecipient> recipients = new ArrayList<>();
        switch (event) {
            case CERTIFICATE_STATUS_CHANGED, CERTIFICATE_ACTION_PERFORMED -> {
                NameAndUuidDto ownerInfo = resourceObjectAssociationService.getOwner(resource, objectUuid);
                if (ownerInfo != null) {
                    recipients.add(new NotificationRecipient(RecipientType.USER, UUID.fromString(ownerInfo.getUuid())));
                }

                for (UUID groupUuid : resourceObjectAssociationService.getGroupUuids(resource, objectUuid)) {
                    recipients.add(new NotificationRecipient(RecipientType.GROUP, groupUuid));
                }
            }
            case CERTIFICATE_DISCOVERED -> {
                CertificateDiscoveredEventData eventData = (CertificateDiscoveredEventData) getEventData(event, data);
                if (eventData.getDiscoveryUserUuid() != null) {
                    recipients.add(new NotificationRecipient(RecipientType.USER, eventData.getDiscoveryUserUuid()));
                }
            }
            case DISCOVERY_FINISHED -> {
                DiscoveryFinishedEventData eventData = (DiscoveryFinishedEventData) getEventData(event, data);
                if (eventData.getDiscoveryUserUuid() != null) {
                    recipients.add(new NotificationRecipient(RecipientType.USER, eventData.getDiscoveryUserUuid()));
                }
            }
            case APPROVAL_REQUESTED -> {
                ApprovalEventData eventData = (ApprovalEventData) getEventData(event, data);
                recipients.add(new NotificationRecipient(eventData.getRecipientType(), eventData.getRecipientUuid()));
            }
            case APPROVAL_CLOSED -> {
                ApprovalEventData eventData = (ApprovalEventData) getEventData(event, data);
                recipients.add(new NotificationRecipient(RecipientType.USER, eventData.getCreatorUuid()));
            }
            case SCHEDULED_JOB_FINISHED -> {
                ScheduledJobFinishedEventData eventData = (ScheduledJobFinishedEventData) getEventData(event, data);
                if (eventData.getUserUuid() != null) {
                    recipients.add(new NotificationRecipient(RecipientType.USER, eventData.getUserUuid()));
                }
            }
        }

        return recipients;
    }

    private void sendExternalNotifications(UUID notificationInstanceUUID, List<NotificationRecipient> recipients, Object notificationData, ResourceEvent
            event, Resource resource) throws ConnectorException, ValidationException, NotFoundException {
        NotificationInstanceReference notificationInstanceReference = notificationInstanceReferenceRepository.findByUuid(notificationInstanceUUID).orElseThrow(() -> new NotFoundException(NotificationInstanceReference.class, notificationInstanceUUID));
        if (notificationInstanceReference.getConnectorUuid() == null) {
            throw new ValidationException("Notification instance does not have assigned connector");
        }

        List<DataAttribute> mappingAttributes;
        ConnectorDto connector = notificationInstanceReference.getConnector().mapToDto();
        try {
            mappingAttributes = notificationInstanceApiClient.listMappingAttributes(connector, notificationInstanceReference.getKind());
        } catch (ConnectorException e) {
            logger.error("Cannot retrieve mapping attributes from connector: {}", e.getMessage());
            throw e;
        }

        List<NotificationRecipientDto> recipientsDto = new ArrayList<>();
        for (NotificationRecipient recipient : recipients) {
            logger.debug("Processing recipient {} of type {}.", recipient.getRecipientUuid(), recipient.getRecipientType());
            try {
                // construct recipient DTO
                NotificationRecipientDto recipientDto = constructNotificationRecipientDto(recipient, notificationInstanceReference.getKind());
                if (recipientDto == null) {
                    // this should happen only in case of recipient type NONE
                    continue;
                }

                List<ResponseAttributeDto> recipientCustomAttributes = attributeEngine.getObjectCustomAttributesContent(recipient.getRecipientType().getRecipientResource(), recipient.getRecipientUuid());

                // prepare mapped attributes
                recipientDto.setMappedAttributes(getMappedAttributes(notificationInstanceReference, mappingAttributes, recipientCustomAttributes));
                recipientsDto.add(recipientDto);
            } catch (Exception e) {
                logger.warn("{} with UUID {} was not found or retrieval of its attributes failed: {}. Notification was not sent for this recipient.", recipient.getRecipientType().getLabel(), recipient.getRecipientUuid(), e.getMessage());
            }
        }

        NotificationProviderNotifyRequestDto notificationProviderNotifyRequestDto = new NotificationProviderNotifyRequestDto();
        notificationProviderNotifyRequestDto.setNotificationData(notificationData);
        notificationProviderNotifyRequestDto.setResource(resource);
        notificationProviderNotifyRequestDto.setEvent(event);
        notificationProviderNotifyRequestDto.setEventType(eventToLegacyNotificationTypeMapping.getOrDefault(event, "other")); // legacy
        notificationProviderNotifyRequestDto.setRecipients(recipientsDto);

        try {
            notificationInstanceApiClient.sendNotification(connector, notificationInstanceReference.getNotificationInstanceUuid().toString(), notificationProviderNotifyRequestDto);
        } catch (ConnectorException e) {
            logger.error("Cannot send notification to connector: {}", e.getMessage());
            throw e;
        }
    }

    private NotificationRecipientDto constructNotificationRecipientDto(NotificationRecipient recipient, String
            notificationProviderKind) {
        NotificationRecipientDto recipientDto;
        switch (recipient.getRecipientType()) {
            case USER -> {
                UserDetailDto userDetailDto = userManagementApiClient.getUserDetail(recipient.getRecipientUuid().toString());
                recipientDto = new NotificationRecipientDto();
                recipientDto.setEmail(userDetailDto.getEmail());
                recipientDto.setName(userDetailDto.getUsername());
            }
            case ROLE -> {
                RoleDetailDto roleDetailDto = roleManagementApiClient.getRoleDetail(recipient.getRecipientUuid().toString());
                String email = roleDetailDto.getEmail();
                if (notificationProviderKind.equals(EMAIL_NOTIFICATION_PROVIDER_KIND)
                        && (email == null || email.isBlank())) {
                    throw new NotSupportedException("Role does not have specified email");
                }
                recipientDto = new NotificationRecipientDto();
                recipientDto.setEmail(email);
                recipientDto.setName(roleDetailDto.getName());
            }
            case GROUP -> {
                Group group = groupRepository.findByUuid(recipient.getRecipientUuid()).orElseThrow();
                String email = group.getEmail();
                if (notificationProviderKind.equals(EMAIL_NOTIFICATION_PROVIDER_KIND)
                        && (email == null || email.isBlank())) {
                    throw new NotSupportedException("Group does not have specified email");
                }
                recipientDto = new NotificationRecipientDto();
                recipientDto.setName(group.getName());
                recipientDto.setEmail(email);
            }
            case NONE -> {
                if (notificationProviderKind.equals(EMAIL_NOTIFICATION_PROVIDER_KIND)) {
                    throw new NotSupportedException("Notification recipient type None is not supported for kind " + EMAIL_NOTIFICATION_PROVIDER_KIND);
                }

                recipientDto = null;
            }
            default ->
                    throw new NotSupportedException("Notification recipient type %s is not supported".formatted(recipient.getRecipientType().getLabel()));
        }
        return recipientDto;
    }

    private List<RequestAttributeDto> getMappedAttributes(NotificationInstanceReference
                                                                  notificationInstanceReference, List<DataAttribute> mappingAttributes, List<ResponseAttributeDto> recipientCustomAttributes) throws
            ValidationException {
        List<RequestAttributeDto> mappedAttributes = new ArrayList<>();
        HashMap<String, ResponseAttributeDto> mappedContent = new HashMap<>();
        for (NotificationInstanceMappedAttributes mappedAttribute : notificationInstanceReference.getMappedAttributes()) {
            Optional<ResponseAttributeDto> recipientCustomAttribute = recipientCustomAttributes.stream().filter(c -> c.getUuid().equals(mappedAttribute.getAttributeDefinitionUuid().toString())).findFirst();
            recipientCustomAttribute.ifPresent(responseAttributeDto -> mappedContent.put(mappedAttribute.getMappingAttributeUuid().toString(), responseAttributeDto));
        }

        for (DataAttribute mappingAttribute : mappingAttributes) {
            ResponseAttributeDto recipientCustomAttribute = mappedContent.get(mappingAttribute.getUuid());

            if (recipientCustomAttribute == null) {
                if (mappingAttribute.getProperties().isRequired()) {
                    throw new ValidationException(String.format("Missing mapping attribute %s with UUID %s in recipient custom attributes.", mappingAttribute.getName(), mappingAttribute.getUuid()));
                }
                continue;
            }

            if (!mappingAttribute.getContentType().equals(recipientCustomAttribute.getContentType())) {
                throw new ValidationException(String.format("Mapped custom attribute %s with UUID %s has different content type (%s) as mapping attribute %s with UUID %s (%s).",
                        recipientCustomAttribute.getName(), recipientCustomAttribute.getUuid(), recipientCustomAttribute.getContentType().getLabel(),
                        mappingAttribute.getName(), mappingAttribute.getUuid(), mappingAttribute.getContentType().getLabel()));
            }

            RequestAttributeDto requestAttributeDto = new RequestAttributeDto();
            requestAttributeDto.setUuid(mappingAttribute.getUuid());
            requestAttributeDto.setName(mappingAttribute.getName());
            requestAttributeDto.setContentType(mappingAttribute.getContentType());
            requestAttributeDto.setContent(recipientCustomAttribute.getContent());
            mappedAttributes.add(requestAttributeDto);
        }

        return mappedAttributes;
    }

    private void sendInternalNotifications(List<NotificationRecipient> recipients, InternalNotificationEventData
            notificationData, Resource resource, UUID objectUuid) {
        logger.debug("Sending internal notification. Message: {}. Detail: {}", notificationData.getText(), notificationData.getDetail());
        for (NotificationRecipient recipient : recipients) {
            switch (recipient.getRecipientType()) {
                case USER ->
                        notificationService.createNotificationForUser(notificationData.getText(), notificationData.getDetail(),
                                recipient.getRecipientUuid().toString(),
                                resource, objectUuid != null ? objectUuid.toString() : null);
                case ROLE ->
                        notificationService.createNotificationForRole(notificationData.getText(), notificationData.getDetail(),
                                recipient.getRecipientUuid().toString(),
                                resource, objectUuid != null ? objectUuid.toString() : null);
                case GROUP ->
                        notificationService.createNotificationForGroup(notificationData.getText(), notificationData.getDetail(),
                                recipient.getRecipientUuid().toString(),
                                resource, objectUuid != null ? objectUuid.toString() : null);
                default ->
                        throw new ValidationException("Unhandled recipient type for internal notification: " + recipient.getRecipientType());
            }
        }
    }

    private InternalNotificationEventData getInternalNotificationData(NotificationMessage message) throws
            ValidationException {
        EventData eventData = getEventData(message.getEvent(), message.getData());
        if (message.getEvent() == null) {
            return (InternalNotificationEventData) eventData;
        }

        return switch (message.getEvent()) {
            case CERTIFICATE_STATUS_CHANGED -> {
                CertificateStatusChangedEventData data = (CertificateStatusChangedEventData) eventData;
                yield new InternalNotificationEventData("Certificate validation status changed from %s to %s for certificate identified as '%s' with serial number '%s' issued by '%s'"
                        .formatted(data.getOldStatus(), data.getNewStatus(), data.getSubjectDn(), data.getSerialNumber(), data.getIssuerDn()), null);
            }
            case CERTIFICATE_ACTION_PERFORMED -> {
                CertificateActionPerformedEventData data = (CertificateActionPerformedEventData) eventData;
                boolean failed = data.getErrorMessage() != null;
                yield new InternalNotificationEventData("Certificate action %s %s for certificate identified as '%s'".formatted(data.getAction(), failed ? "failed" : "successful", data.getSubjectDn()),
                        failed ? "Error message: " + data.getErrorMessage() : "Certificate serial number '%s' issued by '%s'".formatted(data.getSerialNumber(), data.getIssuerDn()));
            }
            case CERTIFICATE_DISCOVERED -> {
                CertificateDiscoveredEventData data = (CertificateDiscoveredEventData) eventData;
                yield new InternalNotificationEventData("Certificate identified as '%s' with serial number '%s' issued by '%s' discovered by '%s' discovery".formatted(data.getSubjectDn(), data.getSerialNumber(), data.getIssuerDn(), data.getDiscoveryName()),
                        "Discovery Connector: %s".formatted(data.getDiscoveryConnectorName() == null ? data.getDiscoveryConnectorUuid() : data.getDiscoveryConnectorName()));
            }
            case DISCOVERY_FINISHED -> {
                DiscoveryFinishedEventData data = (DiscoveryFinishedEventData) eventData;
                yield new InternalNotificationEventData("Discovery %s has finished with status %s and discovered %d certificates".formatted(data.getDiscoveryName(), data.getDiscoveryStatus().getLabel(), data.getTotalCertificateDiscovered()), data.getDiscoveryMessage());
            }
            case APPROVAL_REQUESTED -> {
                ApprovalEventData data = (ApprovalEventData) eventData;
                yield new InternalNotificationEventData("Request %s for %s from %s is waiting to be approved until %s".formatted(data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getExpiryAt()),
                        getApprovalNotificationDetail(data));
            }
            case APPROVAL_CLOSED -> {
                ApprovalEventData data = (ApprovalEventData) eventData;
                yield new InternalNotificationEventData("Request %s for %s from %s is %s".formatted(data.getApprovalUuid(), data.getObjectUuid(), data.getCreatorUsername(), data.getStatus().getLabel()),
                        getApprovalNotificationDetail(data));
            }
            case SCHEDULED_JOB_FINISHED -> {
                ScheduledJobFinishedEventData data = (ScheduledJobFinishedEventData) eventData;
                yield new InternalNotificationEventData("%s scheduled task has finished for %s with result %s".formatted(data.getJobType(), data.getJobName(), data.getStatus()), null);
            }
        };
    }

    private EventData getEventData(ResourceEvent event, Object data) {
        Class<? extends EventData> dataClazz = event == null ? InternalNotificationEventData.class : event.getEventData();

        EventData eventData;
        try {
            eventData = mapper.convertValue(data, dataClazz);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("NotificationMessage for internal notification contains invalid data. Expected: " + dataClazz.getName());
        }

        return eventData;
    }

    private String getApprovalNotificationDetail(ApprovalEventData approvalData) {
        return String.format("Approval profile name: %s, Resource: %s, Resource action: %s, Object UUID: %s",
                approvalData.getApprovalProfileName(), approvalData.getResource().getLabel(), approvalData.getResourceAction(), approvalData.getObjectUuid());
    }

}
