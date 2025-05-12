package com.czertainly.core.events;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.EventException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileRequestDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalStepRequestDto;
import com.czertainly.api.model.connector.notification.NotificationType;
import com.czertainly.api.model.common.events.data.ScheduledJobFinishedEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.settings.NotificationSettingsDto;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.entity.notifications.NotificationInstanceReference;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.dao.repository.notifications.NotificationInstanceReferenceRepository;
import com.czertainly.core.events.data.DiscoveryResult;
import com.czertainly.core.events.handlers.*;
import com.czertainly.core.messaging.listeners.NotificationListener;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.service.ApprovalProfileService;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.tasks.DiscoveryCertificateTask;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
    private CertificateActionPerformedEventHandler certificateActionPerformedEventHandler;

    @Autowired
    private ApprovalRepository approvalRepository;
    @Autowired
    private ApprovalProfileService approvalProfileService;
    @Autowired
    private ApprovalClosedEventHandler approvalClosedEventHandler;
    @Autowired
    private ApprovalRequestedEventHandler approvalRequestedEventHandler;

    @Autowired
    private DiscoveryRepository discoveryRepository;
    @Autowired
    private DiscoveryFinishedEventHandler discoveryFinishedEventHandler;

    @Autowired
    private ScheduledJobsRepository scheduledJobsRepository;
    @Autowired
    private ScheduledJobFinishedEventHandler scheduledJobFinishedEventHandler;

    @Autowired
    private SettingService settingService;
    @Autowired
    private NotificationListener notificationListener;
    @Autowired
    private NotificationInstanceReferenceRepository notificationInstanceReferenceRepository;

    @Test
    void testCertificateStatusChangedAndApprovalEvents() throws EventException, NotFoundException, AlreadyExistException {
        CertificateContent certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        final Certificate certificate = new Certificate();
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
        certificateRepository.save(certificate);

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
        approvalStepRequestDto.setRoleUuid(UUID.randomUUID());
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

        ApprovalStepDto approvalStepDto = approvalProfile.getTheLatestApprovalProfileVersion().getApprovalSteps().getFirst().mapToDto();
        approvalRequestedEventHandler.handleEvent(ApprovalRequestedEventHandler.constructEventMessage(approval.getUuid(), approvalStepDto));
        historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(2, historyList.size());
        Assertions.assertEquals(CertificateEvent.APPROVAL_REQUEST, historyList.getFirst().getEvent());

        Assertions.assertDoesNotThrow(() -> certificateActionPerformedEventHandler.handleEvent(CertificateActionPerformedEventHandler.constructEventMessage(certificate.getUuid(), ResourceAction.REVOKE)));

        approvalClosedEventHandler.handleEvent(ApprovalClosedEventHandler.constructEventMessage(approval.getUuid()));
        historyList = certificateEventHistoryService.getCertificateEventHistory(certificate.getUuid());
        Assertions.assertEquals(3, historyList.size());
        Assertions.assertEquals(CertificateEvent.APPROVAL_CLOSE, historyList.getFirst().getEvent());
    }

    @Test
    void testDiscoveryFinishedEvent() throws EventException {
        DiscoveryHistory discovery = new DiscoveryHistory();
        discovery.setName("TestDiscovery");
        discovery.setStatus(DiscoveryStatus.IN_PROGRESS);
        discovery.setConnectorUuid(UUID.randomUUID());
        discovery.setConnectorStatus(DiscoveryStatus.COMPLETED);
        discovery = discoveryRepository.save(discovery);

        discoveryFinishedEventHandler.handleEvent(DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), null,null, new DiscoveryResult(DiscoveryStatus.COMPLETED, "Test")));
        discovery = discoveryRepository.findByUuid(discovery.getUuid()).orElseThrow();
        Assertions.assertEquals(DiscoveryStatus.IN_PROGRESS, discovery.getStatus());

        discoveryFinishedEventHandler.handleEvent(DiscoveryFinishedEventHandler.constructEventMessage(discovery.getUuid(), null,null, new DiscoveryResult(DiscoveryStatus.PROCESSING, "Test finalize")));
        discovery = discoveryRepository.findByUuid(discovery.getUuid()).orElseThrow();
        Assertions.assertEquals(DiscoveryStatus.COMPLETED, discovery.getStatus());
    }

    @Test
    void testScheduledJobFinishedEvent() {
        final ScheduledJob scheduledJob = new ScheduledJob();
        scheduledJob.setJobName("TestJob");
        scheduledJob.setCronExpression("0 0/3 * * * ? *");
        scheduledJob.setEnabled(true);
        scheduledJob.setSystem(false);
        scheduledJob.setOneTime(false);
        scheduledJob.setJobClassName(DiscoveryCertificateTask.class.getName());
        scheduledJobsRepository.save(scheduledJob);

        Assertions.assertDoesNotThrow(() -> scheduledJobFinishedEventHandler.handleEvent(ScheduledJobFinishedEventHandler.constructEventMessage(scheduledJob.getUuid(), new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, "Test"))));
    }

    @Test
    void testNotificationListener() {
        NotificationInstanceReference notificationInstance = new NotificationInstanceReference();
        notificationInstance.setName("TestNotifInstance");
        notificationInstance.setNotificationInstanceUuid(UUID.randomUUID());
        notificationInstanceReferenceRepository.save(notificationInstance);

        NotificationSettingsDto notificationSettingsDto = new NotificationSettingsDto();
        notificationSettingsDto.setNotificationsMapping(Map.of(NotificationType.SCHEDULED_JOB_COMPLETED, notificationInstance.getUuid().toString()));
        settingService.updateNotificationSettings(notificationSettingsDto);

        Assertions.assertDoesNotThrow(() -> notificationListener.processMessage(new NotificationMessage(NotificationType.SCHEDULED_JOB_COMPLETED, Resource.SCHEDULED_JOB, UUID.randomUUID(), List.of(), new ScheduledJobFinishedEventData("TestJob", "JobType", "Finished"))));
    }

}
