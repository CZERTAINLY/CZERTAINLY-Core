package com.czertainly.core.tasks;

import com.czertainly.api.model.core.certificate.CertificateRelationType;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.raprofile.RaProfileCertificateValidationSettingsUpdateDto;
import com.czertainly.api.model.core.settings.CertificateSettingsDto;
import com.czertainly.api.model.core.settings.CertificateValidationSettingsDto;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.SettingService;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

class UpdateCertificateStatusTaskTest extends BaseSpringBootTest {

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private CertificateRelationRepository certificateRelationRepository;

    @Autowired
    private SettingsCache settingsCache;
    @Autowired
    private SettingService settingService;

    @Autowired
    private UpdateCertificateStatusTask updateCertificateStatusTask;

    @MockitoSpyBean
    private CertificateService mockedCertificateService;

    private ScheduledJobInfo scheduledJobInfo;

    Certificate notValidatedCert;
    Certificate alreadyValidatedCert;
    Certificate certToRevalidate;
    Certificate certToRevalidate2;


    @BeforeEach
    void setUp() {
        scheduledJobInfo = new ScheduledJobInfo("updateCertificateStatusJob");

        // A certificate with status expired
        Certificate expiredStatusCert = new Certificate();
        expiredStatusCert.setStatusValidationTimestamp(null);
        setCertificateContent(expiredStatusCert);
        expiredStatusCert.setValidationStatus(CertificateValidationStatus.EXPIRED);
        certificateRepository.save(expiredStatusCert);

        // A certificate which has not been validated yet
        notValidatedCert = new Certificate();
        notValidatedCert.setStatusValidationTimestamp(null);
        setCertificateContent(notValidatedCert);
        notValidatedCert.setValidationStatus(CertificateValidationStatus.NOT_CHECKED);
        certificateRepository.save(notValidatedCert);

        // A certificate which has been validated in the last day
        alreadyValidatedCert = new Certificate();
        alreadyValidatedCert.setStatusValidationTimestamp(OffsetDateTime.now());
        setCertificateContent(alreadyValidatedCert);
        alreadyValidatedCert.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(alreadyValidatedCert);

        // A certificate which has been validated later than before one day
        certToRevalidate = new Certificate();
        certToRevalidate.setStatusValidationTimestamp(OffsetDateTime.now().minusHours(25));
        setCertificateContent(certToRevalidate);
        certToRevalidate.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certToRevalidate);


        // A certificate which has been validated later than before two days
        certToRevalidate2 = new Certificate();
        certToRevalidate2.setStatusValidationTimestamp(OffsetDateTime.now().minusDays(3));
        setCertificateContent(certToRevalidate2);
        certToRevalidate2.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certToRevalidate2);

        Certificate archivedCertificate = new Certificate();
        archivedCertificate.setArchived(true);
        setCertificateContent(archivedCertificate);
        archivedCertificate.setValidationStatus(CertificateValidationStatus.NOT_CHECKED);
        certificateRepository.save(archivedCertificate);

        Mockito.doAnswer(execution -> {
            Certificate certificate = execution.getArgument(0);
            certificate.setStatusValidationTimestamp(OffsetDateTime.now());
            certificateRepository.save(certificate);
            return null;
        }).when(mockedCertificateService).validate(any());

    }

    @Test
    void testCertificatesValidationDefaultSettings() {
        OffsetDateTime timeNow = OffsetDateTime.now();
        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should validate notValidatedCert, certToRevalidate and certToRevalidate2
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("3"));
        assertCorrectCertificatesHaveBeenValidated(List.of(notValidatedCert, certToRevalidate2, certToRevalidate2), timeNow);
    }

    @Test
    void testCertificateValidationCustomSettings() {
        PlatformSettingsDto platformSettingsDto = getPlatformSettingsDto(true);
        settingsCache.cacheSettings(SettingsSection.PLATFORM, platformSettingsDto);
        OffsetDateTime timeNow = OffsetDateTime.now();
        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should validate notValidatedCert and certToRevalidate2
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("2"));
        settingsCache.cacheSettings(SettingsSection.PLATFORM,settingService.getPlatformSettings());
        assertCorrectCertificatesHaveBeenValidated(List.of(notValidatedCert, certToRevalidate2), timeNow);
    }

    private PlatformSettingsDto getPlatformSettingsDto(boolean validationEnabled) {
        PlatformSettingsDto platformSettingsDto = new PlatformSettingsDto();
        CertificateSettingsDto certificateSettingsDto = new CertificateSettingsDto();
        CertificateValidationSettingsDto certificateValidationSettingsDto = new CertificateValidationSettingsDto();
        certificateValidationSettingsDto.setEnabled(validationEnabled);
        certificateValidationSettingsDto.setFrequency(2);
        certificateSettingsDto.setValidation(certificateValidationSettingsDto);
        platformSettingsDto.setCertificates(certificateSettingsDto);
        return platformSettingsDto;
    }

    @Test
    void testValidationWithCertificatesWithRaProfile() {
        RaProfileCertificateValidationSettingsUpdateDto validationUpdateDto = new RaProfileCertificateValidationSettingsUpdateDto();

        // Certificate which has RA Profile with validation disabled
        Certificate certificateWithRaProfileValidationDisabled = new Certificate();
        certificateWithRaProfileValidationDisabled.setStatusValidationTimestamp(OffsetDateTime.now().minusDays(2));
        validationUpdateDto.setEnabled(false);
        certificateWithRaProfileValidationDisabled.setRaProfile(getRaProfile(validationUpdateDto));
        setCertificateContent(certificateWithRaProfileValidationDisabled);
        certificateWithRaProfileValidationDisabled.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certificateWithRaProfileValidationDisabled);

        // Certificate which has RA Profile with validation enabled and default settings
        Certificate certificateWithRaProfileValidationEnabledDefault = new Certificate();
        certificateWithRaProfileValidationEnabledDefault.setStatusValidationTimestamp(null);
        validationUpdateDto.setEnabled(true);
        certificateWithRaProfileValidationEnabledDefault.setRaProfile(getRaProfile(validationUpdateDto));
        setCertificateContent(certificateWithRaProfileValidationEnabledDefault);
        certificateWithRaProfileValidationEnabledDefault.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certificateWithRaProfileValidationEnabledDefault);

        // Certificate which has RA Profile with validation enabled and custom settings
        Certificate certificateWithRaProfileValidationEnabledCustom = new Certificate();
        certificateWithRaProfileValidationEnabledCustom.setStatusValidationTimestamp(null);
        validationUpdateDto.setFrequency(2);
        certificateWithRaProfileValidationEnabledCustom.setRaProfile(getRaProfile(validationUpdateDto));
        setCertificateContent(certificateWithRaProfileValidationEnabledCustom);
        certificateWithRaProfileValidationEnabledCustom.setValidationStatus(CertificateValidationStatus.VALID);
        certificateRepository.save(certificateWithRaProfileValidationEnabledCustom);

        // Certificate which has RA Profile with enabled set to null
        Certificate certificateWithRaProfileValidationEnabledNull = new Certificate();
        certificateWithRaProfileValidationEnabledNull.setValidationStatus(null);
        validationUpdateDto.setEnabled(null);
        certificateWithRaProfileValidationEnabledNull.setRaProfile(getRaProfile(validationUpdateDto));
        setCertificateContent(certificateWithRaProfileValidationEnabledNull);
        certificateWithRaProfileValidationEnabledNull.setValidationStatus(CertificateValidationStatus.VALID);
        certificateRepository.save(certificateWithRaProfileValidationEnabledNull);

        OffsetDateTime timeNow = OffsetDateTime.now();
        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should validate notValidatedCert, certToRevalidate, certToRevalidate2, certificateWithRaProfileValidationEnabledDefault and certificateWithRaProfileValidationEnabledCustom
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("6"));
        assertCorrectCertificatesHaveBeenValidated(List.of(notValidatedCert, certToRevalidate2, certToRevalidate, certificateWithRaProfileValidationEnabledDefault, certificateWithRaProfileValidationEnabledCustom, certificateWithRaProfileValidationEnabledNull), timeNow);

        settingsCache.cacheSettings(SettingsSection.PLATFORM, getPlatformSettingsDto(false));
        certificateWithRaProfileValidationEnabledDefault.setStatusValidationTimestamp(OffsetDateTime.now().minusDays(10));
        certificateWithRaProfileValidationEnabledCustom.setStatusValidationTimestamp(OffsetDateTime.now().minusDays(10));
        certificateRepository.save(certificateWithRaProfileValidationEnabledCustom);
        certificateRepository.save(certificateWithRaProfileValidationEnabledDefault);

        timeNow = OffsetDateTime.now();
        scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should validate certificateWithRaProfileValidationEnabledDefault and certificateWithRaProfileValidationEnabledCustom
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("2"));
        assertCorrectCertificatesHaveBeenValidated(List.of(certificateWithRaProfileValidationEnabledDefault, certificateWithRaProfileValidationEnabledCustom), timeNow);
        settingsCache.cacheSettings(SettingsSection.PLATFORM, settingService.getPlatformSettings());
    }


    @Test
    void testExceptionThrown() {
        Mockito.doThrow(MatchException.class).when(mockedCertificateService).validate(any());
        OffsetDateTime timeNow = OffsetDateTime.now();
        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should not validate any, since exceptions are thrown
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("0"));
        assertCorrectCertificatesHaveBeenValidated(List.of(), timeNow);
    }

    @Test
    void testHandleExpiringCertificatesEvent() {
        // Certificate is expiring and is renewed by some certificate
        Certificate expiringRenewedCert = createCertificateForExpiringEventTest(CertificateValidationStatus.EXPIRING, null);
        // Certificate is expiring, but is not renewed by any certificate -> this is the only certificate that should be returned
        createCertificateForExpiringEventTest(CertificateValidationStatus.EXPIRING, expiringRenewedCert.getUuid());
        // Not expiring and is renewed by some certificate
        Certificate renewedCert = createCertificateForExpiringEventTest(CertificateValidationStatus.VALID, null);
        // Not expiring and is not renewed by some certificate and is renewing previous cert
        createCertificateForExpiringEventTest(CertificateValidationStatus.VALID, renewedCert.getUuid());
        // Expiring and is renewed by not yet issued certificate
        Certificate renewedByNotIssuedCert = createCertificateForExpiringEventTest(CertificateValidationStatus.EXPIRING, null);
        // certificate not issued renewing previous cert
        Certificate notIssued = createCertificateForExpiringEventTest(CertificateValidationStatus.VALID, renewedByNotIssuedCert.getUuid());
        notIssued.setState(CertificateState.PENDING_ISSUE);
        certificateRepository.save(notIssued);


        ScheduledTaskResult result = updateCertificateStatusTask.performJob(null, null);

        Assertions.assertEquals(SchedulerJobExecutionStatus.SUCCESS, result.getStatus());
        Assertions.assertTrue(result.getResultMessage().contains("Handled 2 expiring "));
    }

    private Certificate createCertificateForExpiringEventTest(CertificateValidationStatus status, UUID sourceUuid) {
        Certificate certificateEntity = new Certificate();
        CertificateContent content = new CertificateContent();
        content.setContent(String.valueOf(Math.random()));
        certificateContentRepository.save(content);
        certificateEntity.setCertificateContent(content);
        certificateEntity.setValidationStatus(status);
        certificateEntity.setState(CertificateState.ISSUED);
        certificateRepository.save(certificateEntity);
        if (sourceUuid != null) {
            CertificateRelation certificateRelation = new CertificateRelation();
            certificateRelation.setId(new CertificateRelationId(certificateEntity.getUuid(), sourceUuid));
            certificateRelation.setRelationType(CertificateRelationType.REPLACEMENT);
            certificateRelationRepository.save(certificateRelation);
        }
        return certificateEntity;
    }


    private void setCertificateContent(Certificate certificate) {
        CertificateContent certificateContent = new CertificateContent();
        certificateContentRepository.save(certificateContent);
        certificate.setCertificateContent(certificateContent);
    }

    private RaProfile getRaProfile(RaProfileCertificateValidationSettingsUpdateDto validationUpdateDto) {
        RaProfile raProfile = new RaProfile();
        raProfile.setEnabled(true);
        AuthorityInstanceReference authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setStatus("connected");
        Connector connector = new Connector();
        connector.setStatus(ConnectorStatus.CONNECTED);
        connectorRepository.save(connector);
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReferenceRepository.save(authorityInstanceReference);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile.setValidationFrequency(validationUpdateDto.getFrequency());
        raProfile.setValidationEnabled(validationUpdateDto.getEnabled());
        raProfileRepository.save(raProfile);
        return raProfile;
    }

    private void assertCorrectCertificatesHaveBeenValidated(List<Certificate> correctCertificates, OffsetDateTime timeNow) {
        List<Certificate> validatedCertificates = certificateRepository.findAll().stream()
                .filter(certificate -> certificate.getStatusValidationTimestamp() != null && certificate.getStatusValidationTimestamp().isAfter(timeNow))
                .toList();
        Assertions.assertEquals(correctCertificates.size(), validatedCertificates.size());
        Assertions.assertTrue(validatedCertificates.containsAll(correctCertificates));
    }
}
