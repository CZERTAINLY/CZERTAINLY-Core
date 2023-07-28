package com.czertainly.core.messaging.listeners;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.v2.ClientCertificateRekeyRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfileRelation;
import com.czertainly.core.dao.entity.ApprovalProfileVersion;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.repository.ApprovalProfileRelationRepository;
import com.czertainly.core.dao.repository.ApprovalRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.messaging.model.ActionMessage;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.ApprovalService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AuthHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ActionListener {

    private static final Logger logger = LoggerFactory.getLogger(ActionListener.class);


    private ApprovalProfileRelationRepository approvalProfileRelationRepository;

    private ApprovalService approvalService;

    private ApprovalRepository approvalRepository;

    private ClientOperationService clientOperationService;

    private CertificateRepository certificateRepository;

    private ObjectMapper mapper = new ObjectMapper();

    @RabbitListener(queues = RabbitMQConstants.QUEUE_ACTIONS_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(final ActionMessage actionMessage) throws NotFoundException {
//        boolean skipApprovals = false;
//        if (actionMessage.getApprovalUuid() != null) {
//            final Optional<Approval> approval = approvalRepository.findByUuid(SecuredUUID.fromUUID(actionMessage.getApprovalUuid()));
//            skipApprovals = approval.isPresent() && approval.get().getStatus().equals(ApprovalStatusEnum.APPROVED);
//        }

        if (actionMessage.getApprovalUuid() == null) {
            final Optional<List<ApprovalProfileRelation>> approvalProfileRelationOptional = approvalProfileRelationRepository.findByResourceUuidAndResource(actionMessage.getRaProfileUuid(), Resource.RA_PROFILE);
            if (approvalProfileRelationOptional.isPresent() && !approvalProfileRelationOptional.get().isEmpty()) {
                final ApprovalProfileRelation approvalProfileRelation = approvalProfileRelationOptional.get().get(0);
                final ApprovalProfileVersion approvalProfileVersion = approvalProfileRelation.getApprovalProfile().getTheLatestApprovalProfileVersion();
                final Approval approval = approvalService.createApproval(approvalProfileVersion, actionMessage.getResource(), actionMessage.getResourceAction(), actionMessage.getResourceUuid(), actionMessage.getUserUuid(), actionMessage.getData());
                logger.info("Created new Approval {} for object {}", approval.getUuid(), actionMessage.getResourceUuid());
                return;
            }
        }

        try {
            AuthHelper.authenticateAsUser(actionMessage.getUserUuid());
            processTheActivity(actionMessage);
        } catch (Exception e) {
            logger.error("Unable to perform activity for resource {} and action {}.", actionMessage.getResource(), actionMessage.getResourceAction(), e);
        }

    }

    private void processTheActivity(final ActionMessage actionMessage) throws CertificateOperationException, ConnectorException, CertificateException, NoSuchAlgorithmException, AlreadyExistException {

        switch (actionMessage.getResource()) {
            case CERTIFICATE -> {
                processCertificateActivity(actionMessage);
            }
            default -> logger.error("There is not allow other resources than CERTIFICATE (for now)");
        }
    }

    private void processCertificateActivity(final ActionMessage actionMessage) throws ConnectorException, CertificateException, NoSuchAlgorithmException, AlreadyExistException, CertificateOperationException {
        Certificate certificate = null;
        if (actionMessage.getResourceUuid() != null) {
            Optional<Certificate> certificateOptional = certificateRepository.findByUuid(actionMessage.getResourceUuid());
            if (certificateOptional.isPresent()) {
                certificate = certificateOptional.get();
            }
        }
        switch (actionMessage.getResourceAction()) {
            case ISSUE -> {
                final ClientCertificateSignRequestDto clientCertificateSignRequestDto = mapper.convertValue(actionMessage.getData(), ClientCertificateSignRequestDto.class);
                clientOperationService.issueCertificateAction(SecuredParentUUID.fromUUID(actionMessage.getAuthorityUuid()), SecuredUUID.fromUUID(actionMessage.getRaProfileUuid()), clientCertificateSignRequestDto);
            }
            case REKEY -> {
                final ClientCertificateRekeyRequestDto clientCertificateRekeyRequestDto = mapper.convertValue(actionMessage.getData(), ClientCertificateRekeyRequestDto.class);
                clientOperationService.rekeyCertificateAction(SecuredParentUUID.fromUUID(certificate.getRaProfile().getAuthorityInstanceReferenceUuid()), SecuredUUID.fromUUID(certificate.getRaProfileUuid()), actionMessage.getResourceUuid().toString(), clientCertificateRekeyRequestDto);
            }
            case RENEW -> {
                final ClientCertificateRenewRequestDto clientCertificateRenewRequestDto = mapper.convertValue(actionMessage.getData(), ClientCertificateRenewRequestDto.class);
                clientOperationService.renewCertificateAction(SecuredParentUUID.fromUUID(certificate.getRaProfile().getAuthorityInstanceReferenceUuid()), SecuredUUID.fromUUID(certificate.getRaProfileUuid()), actionMessage.getResourceUuid().toString(), clientCertificateRenewRequestDto);
            }
            case REVOKE -> {
                final ClientCertificateRevocationDto clientCertificateRevocationDto = mapper.convertValue(actionMessage.getData(), ClientCertificateRevocationDto.class);
                clientOperationService.revokeCertificateAction(SecuredParentUUID.fromUUID(certificate.getRaProfile().getAuthorityInstanceReferenceUuid()), SecuredUUID.fromUUID(certificate.getRaProfileUuid()), actionMessage.getResourceUuid().toString(), clientCertificateRevocationDto);
            }
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
    public void setApprovalRepository(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) {
        this.clientOperationService = clientOperationService;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }
}
