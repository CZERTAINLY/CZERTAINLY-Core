package com.czertainly.core.tasks;

import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.raprofile.RaProfileValidationUpdateDto;
import com.czertainly.api.model.core.settings.CertificateSettingsDto;
import com.czertainly.api.model.core.settings.CertificateValidationSettingsDto;
import com.czertainly.api.model.core.settings.PlatformSettingsDto;
import com.czertainly.api.model.core.settings.SettingsSection;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.settings.SettingsCache;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.List;

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
    private SettingsCache settingsCache;

    @Autowired
    private UpdateCertificateStatusTask updateCertificateStatusTask;

    @MockitoSpyBean
    private CertificateService mockedCertificateService;

    private ScheduledJobInfo scheduledJobInfo;

    LocalDateTime timeNow;

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
        alreadyValidatedCert.setStatusValidationTimestamp(LocalDateTime.now());
        setCertificateContent(alreadyValidatedCert);
        alreadyValidatedCert.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(alreadyValidatedCert);

        // A certificate which has been validated later than before one day
        certToRevalidate = new Certificate();
        certToRevalidate.setStatusValidationTimestamp(LocalDateTime.now().minusHours(25));
        setCertificateContent(certToRevalidate);
        certToRevalidate.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certToRevalidate);


        // A certificate which has been validated later than before two days
        certToRevalidate2 = new Certificate();
        certToRevalidate2.setStatusValidationTimestamp(LocalDateTime.now().minusDays(3));
        setCertificateContent(certToRevalidate2);
        certToRevalidate2.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certToRevalidate2);

        Mockito.doAnswer(execution -> {
            Certificate certificate = execution.getArgument(0);
            certificate.setStatusValidationTimestamp(LocalDateTime.now());
            certificateRepository.save(certificate);
            return null;
        }).when(mockedCertificateService).validate(any());

        timeNow = LocalDateTime.now();
    }

    @Test
    void testCertificatesValidationDefaultSettings() {
        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should validate notValidatedCert, certToRevalidate and certToRevalidate2
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("3"));
        assertCorrectCertificatesHaveBeenValidated(List.of(notValidatedCert, certToRevalidate2, certToRevalidate2));
    }

    @Test
    void testCertificateValidationCustomSettings() {
        PlatformSettingsDto platformSettingsDto = new PlatformSettingsDto();
        CertificateSettingsDto certificateSettingsDto = new CertificateSettingsDto();
        CertificateValidationSettingsDto certificateValidationSettingsDto = new CertificateValidationSettingsDto();
        certificateValidationSettingsDto.setValidationEnabled(true);
        certificateValidationSettingsDto.setValidationFrequency(2);
        certificateSettingsDto.setCertificateValidationSettingsDto(certificateValidationSettingsDto);
        platformSettingsDto.setCertificates(certificateSettingsDto);
        settingsCache.cacheSettings(SettingsSection.PLATFORM, platformSettingsDto);

        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should validate notValidatedCert and certToRevalidate2
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("2"));
        settingsCache.cacheSettings(SettingsSection.PLATFORM, new PlatformSettingsDto());
        assertCorrectCertificatesHaveBeenValidated(List.of(notValidatedCert, certToRevalidate2));
    }

    @Test
    void testValidationWithCertificatesWithRaProfile() {
        RaProfileValidationUpdateDto validationUpdateDto = new RaProfileValidationUpdateDto();

        // Certificate which has RA Profile with validation disabled
        Certificate certificateWithRaProfileValidationDisabled = new Certificate();
        certificateWithRaProfileValidationDisabled.setStatusValidationTimestamp(LocalDateTime.now().minusDays(2));
        validationUpdateDto.setValidationEnabled(false);
        certificateWithRaProfileValidationDisabled.setRaProfile(getRaProfile(validationUpdateDto));
        setCertificateContent(certificateWithRaProfileValidationDisabled);
        certificateWithRaProfileValidationDisabled.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certificateWithRaProfileValidationDisabled);

        // Certificate which has RA Profile with validation enabled and default settings
        Certificate certificateWithRaProfileValidationEnabledDefault = new Certificate();
        certificateWithRaProfileValidationEnabledDefault.setStatusValidationTimestamp(null);
        validationUpdateDto.setValidationEnabled(true);
        certificateWithRaProfileValidationEnabledDefault.setRaProfile(getRaProfile(validationUpdateDto));
        setCertificateContent(certificateWithRaProfileValidationEnabledDefault);
        certificateWithRaProfileValidationEnabledDefault.setValidationStatus(CertificateValidationStatus.INVALID);
        certificateRepository.save(certificateWithRaProfileValidationEnabledDefault);

        // Certificate which has RA Profile with validation enabled and custom settings
        Certificate certificateWithRaProfileValidationEnabledCustom = new Certificate();
        certificateWithRaProfileValidationEnabledCustom.setStatusValidationTimestamp(null);
        validationUpdateDto.setValidationFrequency(2);
        certificateWithRaProfileValidationEnabledCustom.setRaProfile(getRaProfile(validationUpdateDto));
        setCertificateContent(certificateWithRaProfileValidationEnabledCustom);
        certificateWithRaProfileValidationEnabledCustom.setValidationStatus(CertificateValidationStatus.VALID);
        certificateRepository.save(certificateWithRaProfileValidationEnabledCustom);

        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should validate notValidatedCert, certToRevalidate, certToRevalidate2, certificateWithRaProfileValidationEnabledDefault and certificateWithRaProfileValidationEnabledCustom
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("5"));
        assertCorrectCertificatesHaveBeenValidated(List.of(notValidatedCert, certToRevalidate2, certToRevalidate, certificateWithRaProfileValidationEnabledDefault, certificateWithRaProfileValidationEnabledCustom));
    }

    @Test
    void testExceptionThrown() {
        Mockito.doThrow(MatchException.class).when(mockedCertificateService).validate(any());
        ScheduledTaskResult scheduledTaskResult = updateCertificateStatusTask.performJob(scheduledJobInfo, null);
        // Should not validate any, since exceptions are thrown
        Assertions.assertTrue(scheduledTaskResult.getResultMessage().contains("0"));
        assertCorrectCertificatesHaveBeenValidated(List.of());
    }


    private void setCertificateContent(Certificate certificate) {
        CertificateContent certificateContent = new CertificateContent();
        certificateContentRepository.save(certificateContent);
        certificate.setCertificateContent(certificateContent);
    }

    private RaProfile getRaProfile(RaProfileValidationUpdateDto validationUpdateDto) {
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
        raProfile.setValidationFrequency(validationUpdateDto.getValidationFrequency());
        raProfile.setValidationEnabled(validationUpdateDto.getValidationEnabled());
        raProfileRepository.save(raProfile);
        return raProfile;
    }

    private void assertCorrectCertificatesHaveBeenValidated(List<Certificate> correctCertificates) {
        List<Certificate> validatedCertificates = certificateRepository.findAll().stream()
                .filter(certificate -> certificate.getStatusValidationTimestamp() != null && certificate.getStatusValidationTimestamp().isAfter(timeNow))
                .toList();
        Assertions.assertEquals(correctCertificates.size(), validatedCertificates.size());
        Assertions.assertTrue(validatedCertificates.containsAll(correctCertificates));
    }
}