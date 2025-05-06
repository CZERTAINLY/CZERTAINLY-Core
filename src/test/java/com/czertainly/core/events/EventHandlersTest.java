package com.czertainly.core.events;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.EventException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileRequestDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.ApprovalRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.events.handlers.ApprovalClosedEventHandler;
import com.czertainly.core.events.handlers.CertificateStatusChangedEventHandler;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.service.ApprovalProfileService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

class EventHandlersTest extends BaseSpringBootTest {

    @Autowired
    private CertificateService certificateService;
    @Autowired
    private CertificateEventHistoryService certificateEventHistoryService;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private CertificateStatusChangedEventHandler certificateStatusChangedEventHandler;

    @Autowired
    private ApprovalRepository approvalRepository;
    @Autowired
    private ApprovalProfileService approvalProfileService;
    @Autowired
    private ApprovalClosedEventHandler approvalClosedEventHandler;

    @Test
    void testCertificateStatusChangedAndApprovalClosedEvent() throws EventException, NotFoundException, AlreadyExistException {
        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        Certificate certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCercertificatetificate");
        certificate.setSerialNumber("123456789");
        certificate.setNotBefore(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        certificate.setNotAfter(Date.from(Instant.now().plus(100, ChronoUnit.DAYS)));
        certificate.setCertificateType(CertificateType.X509);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.INACTIVE);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate = certificateRepository.save(certificate);

        certificateService.validate(certificate);
        certificateStatusChangedEventHandler.handleEvent(CertificateStatusChangedEventHandler.constructEventMessage(certificate.getUuid(), CertificateValidationStatus.INACTIVE, certificate.getValidationStatus()));
        List<CertificateEventHistoryDto> historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(1, historyList.size());
        Assertions.assertEquals(CertificateEvent.UPDATE_VALIDATION_STATUS, historyList.getFirst().getEvent());

        ApprovalProfileRequestDto approvalProfileRequestDto = new ApprovalProfileRequestDto();
        approvalProfileRequestDto.setName("TestApprovalProfile");
        approvalProfileRequestDto.setExpiry(24);
        approvalProfileRequestDto.setEnabled(true);

        ApprovalStepRequestDto approvalStepRequestDto = new ApprovalStepRequestDto();
        approvalStepRequestDto.setUserUuid(UUID.randomUUID());
        approvalStepRequestDto.setRequiredApprovals(1);
        approvalStepRequestDto.setOrder(1);
        approvalProfileRequestDto.getApprovalSteps().add(approvalStepRequestDto);
        ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);

        Approval approval = new Approval();
        approval.setApprovalProfileVersionUuid(approvalProfile.getTheLatestApprovalProfileVersion().getUuid());
        approval.setStatus(ApprovalStatusEnum.PENDING);
        approval.setAction(ResourceAction.REVOKE);
        approval.setResource(Resource.CERTIFICATE);
        approval.setObjectUuid(certificate.getUuid());
        approval.setCreatorUuid(UUID.randomUUID());
        approval.setCreatedAt(new Date());
        approval.setExpiryAt(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        approval = approvalRepository.save(approval);

        approvalClosedEventHandler.handleEvent(ApprovalClosedEventHandler.constructEventMessage(approval.getUuid()));
        historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(2, historyList.size());
        Assertions.assertEquals(CertificateEvent.APPROVAL_CLOSE, historyList.getFirst().getEvent());
    }

}
