package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.core.v2.ClientCertificateRekeyRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfileRelation;
import com.czertainly.core.dao.entity.ApprovalProfileVersion;
import com.czertainly.core.dao.repository.ApprovalProfileRelationRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.ActionMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.messaging.producers.NotificationProducer;
import com.czertainly.core.service.ApprovalService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AuthHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class ActionListener {
    private static final Logger logger = LoggerFactory.getLogger(ActionListener.class);

    private ApprovalProfileRelationRepository approvalProfileRelationRepository;

    private ApprovalService approvalService;

    private ClientOperationService clientOperationService;

    private final ObjectMapper mapper = new ObjectMapper();

    private NotificationProducer notificationProducer;

    private AuthHelper authHelper;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_ACTIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(final ActionMessage actionMessage) throws MessageHandlingException {
        boolean hasApproval = actionMessage.getApprovalUuid() != null;
        boolean isApproved = hasApproval && actionMessage.getApprovalStatus().equals(ApprovalStatusEnum.APPROVED);

        if (!hasApproval) {
            final Optional<List<ApprovalProfileRelation>> approvalProfileRelationOptional = approvalProfileRelationRepository.findByResourceUuidAndResource(actionMessage.getApprovalProfileResourceUuid(), actionMessage.getApprovalProfileResource());
            if (approvalProfileRelationOptional.isPresent() && !approvalProfileRelationOptional.get().isEmpty()) {
                try {
                    final ApprovalProfileRelation approvalProfileRelation = approvalProfileRelationOptional.get().get(0);
                    final ApprovalProfileVersion approvalProfileVersion = approvalProfileRelation.getApprovalProfile().getTheLatestApprovalProfileVersion();
                    final Approval approval = approvalService.createApproval(approvalProfileVersion, actionMessage.getResource(), actionMessage.getResourceAction(), actionMessage.getResourceUuid(), actionMessage.getUserUuid(), actionMessage.getData());
                    logger.info("Created new Approval {} for object {}", approval.getUuid(), actionMessage.getResourceUuid());
                    processApprovalCreated(actionMessage);
                } catch (Exception e) {
                    String errorMessage = String.format("Cannot create new approval to approve %s %s action!", actionMessage.getResource().getLabel(), actionMessage.getResourceAction().getCode());
                    logger.error("{}: {}", errorMessage, e.getMessage());
                    notificationProducer.produceNotificationText(actionMessage.getResource(), actionMessage.getResourceUuid(),
                            NotificationRecipient.buildUserNotificationRecipient(actionMessage.getUserUuid()), errorMessage, e.getMessage());
                    throw new MessageHandlingException(RabbitMQConstants.QUEUE_ACTIONS_NAME, actionMessage, "Handling of action approval creation failed: " + e.getMessage());
                }
                return;
            }
        }

        try {
            authHelper.authenticateAsUser(actionMessage.getUserUuid());
            processAction(actionMessage, hasApproval, isApproved);
        } catch (Exception e) {
            String errorMessage = String.format("Failed to perform %s %s%s action!", actionMessage.getResource().getLabel(), actionMessage.getResourceAction().getCode(), isApproved ? "" : " rejected");
            logger.error("{}: {}", errorMessage, e.getMessage());
            notificationProducer.produceNotificationText(actionMessage.getResource(), actionMessage.getResourceUuid(),
                    NotificationRecipient.buildUserNotificationRecipient(actionMessage.getUserUuid()), errorMessage, e.getMessage());
            throw new MessageHandlingException(RabbitMQConstants.QUEUE_ACTIONS_NAME, actionMessage, "Unable to process action: " + e.getMessage());
        }
    }

    private void processApprovalCreated(final ActionMessage actionMessage) throws NotFoundException {
        switch (actionMessage.getResource()) {
            case CERTIFICATE -> clientOperationService.approvalCreatedAction(actionMessage.getResourceUuid());
            default ->
                    logger.error("Action listener does not support resource {}", actionMessage.getResource().getLabel());
        }
    }

    private void processAction(final ActionMessage actionMessage, boolean hasApproval, boolean isApproved) throws CertificateOperationException, ConnectorException, CertificateException, NoSuchAlgorithmException, AlreadyExistException {
        switch (actionMessage.getResource()) {
            case CERTIFICATE -> processCertificateAction(actionMessage, hasApproval, isApproved);
            default ->
                    logger.error("Action listener does not support resource {}", actionMessage.getResource().getLabel());
        }
    }

    private void processCertificateAction(final ActionMessage actionMessage, boolean hasApproval, boolean isApproved) throws ConnectorException, CertificateException, NoSuchAlgorithmException, AlreadyExistException, CertificateOperationException {
        // handle rejected actions
        if (hasApproval && !isApproved) {
            switch (actionMessage.getResourceAction()) {
                case ISSUE, RENEW, REKEY ->
                        clientOperationService.issueCertificateRejectedAction(actionMessage.getResourceUuid());
                default ->
                        logger.debug("Action listener does not handle reject of action {} for resource {}", actionMessage.getResourceAction().getCode(), actionMessage.getResource().getLabel());
            }
            return;
        }

        // handle
        switch (actionMessage.getResourceAction()) {
            case ISSUE -> clientOperationService.issueCertificateAction(actionMessage.getResourceUuid(), isApproved);
            case REKEY -> {
                final ClientCertificateRekeyRequestDto clientCertificateRekeyRequestDto = mapper.convertValue(actionMessage.getData(), ClientCertificateRekeyRequestDto.class);
                clientOperationService.rekeyCertificateAction(actionMessage.getResourceUuid(), clientCertificateRekeyRequestDto, isApproved);
            }
            case RENEW -> {
                final ClientCertificateRenewRequestDto clientCertificateRenewRequestDto = mapper.convertValue(actionMessage.getData(), ClientCertificateRenewRequestDto.class);
                clientOperationService.renewCertificateAction(actionMessage.getResourceUuid(), clientCertificateRenewRequestDto, isApproved);
            }
            case REVOKE -> {
                final ClientCertificateRevocationDto clientCertificateRevocationDto = mapper.convertValue(actionMessage.getData(), ClientCertificateRevocationDto.class);
                clientOperationService.revokeCertificateAction(actionMessage.getResourceUuid(), clientCertificateRevocationDto, isApproved);
            }
            default ->
                    logger.error("Action listener does not support action {} for resource {}", actionMessage.getResourceAction().getCode(), actionMessage.getResource().getLabel());
        }
    }

    // SETTERs

    @Autowired
    public void setApprovalProfileRelationRepository(ApprovalProfileRelationRepository approvalProfileRelationRepository) {
        this.approvalProfileRelationRepository = approvalProfileRelationRepository;
    }

    @Autowired
    public void setApprovalService(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) {
        this.clientOperationService = clientOperationService;
    }

    @Autowired
    public void setNotificationProducer(NotificationProducer notificationProducer) {
        this.notificationProducer = notificationProducer;
    }

    @Autowired
    public void setAuthHelper(AuthHelper authHelper) {
        this.authHelper = authHelper;
    }
}
