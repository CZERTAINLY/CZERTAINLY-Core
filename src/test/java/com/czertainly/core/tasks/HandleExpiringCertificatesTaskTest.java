package com.czertainly.core.tasks;

import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

public class HandleExpiringCertificatesTaskTest extends BaseSpringBootTest {
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private HandleExpiringCertificatesTask handleExpiringCertificatesTask;


    @Test
    void testHandleExpiringCertificatesEvent() {
        // Certificate is expiring and is renewed by some certificate
        Certificate expiringRenewedCert = createCertificateForExpiringEventTest(CertificateValidationStatus.EXPIRING, null);
        // Certificate is expiring, but is not renewed by any certificate -> this is the only certificate that should be returned
        createCertificateForExpiringEventTest(CertificateValidationStatus.EXPIRING, expiringRenewedCert.getUuid());
        // Not expiring and is renewed by some certificate
        Certificate renewedCert = createCertificateForExpiringEventTest(CertificateValidationStatus.VALID, null);
        // Not expiring and is not renewed by some certificate
        createCertificateForExpiringEventTest(CertificateValidationStatus.VALID, renewedCert.getUuid());

        ScheduledTaskResult result = handleExpiringCertificatesTask.performJob(null, null);

        Assertions.assertEquals(SchedulerJobExecutionStatus.SUCCESS, result.getStatus());
        Assertions.assertTrue(result.getResultMessage().startsWith("1 "));
    }

    private Certificate createCertificateForExpiringEventTest(CertificateValidationStatus status, UUID sourceUuid) {
        Certificate certificateEntity = new Certificate();
        CertificateContent content = new CertificateContent();
        content.setContent(String.valueOf(Math.random()));
        certificateContentRepository.save(content);
        certificateEntity.setCertificateContent(content);
        certificateEntity.setValidationStatus(status);
        certificateEntity.setSourceCertificateUuid(sourceUuid);
        certificateRepository.save(certificateEntity);
        return certificateEntity;
    }
}
