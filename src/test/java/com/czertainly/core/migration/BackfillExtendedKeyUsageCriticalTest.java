package com.czertainly.core.migration;

import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateTestUtil;
import db.migration.V202604011901__BackfillExtendedKeyUsageCritical;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.Base64;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BackfillExtendedKeyUsageCriticalTest extends BaseSpringBootTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    CertificateRepository certificateRepository;

    @Autowired
    CertificateContentRepository certificateContentRepository;

    @Test
    void testMigration() throws Exception {
        // Certificate with a critical EKU extension
        CertificateContent contentCritical = new CertificateContent();
        contentCritical.setContent(Base64.getEncoder().encodeToString(CertificateTestUtil.createCertificateWithEku(true).getEncoded()));
        certificateContentRepository.save(contentCritical);
        Certificate certWithCriticalEku = new Certificate();
        certWithCriticalEku.setCertificateContent(contentCritical);
        certificateRepository.save(certWithCriticalEku);

        // Certificate with a non-critical EKU extension
        CertificateContent contentNonCritical = new CertificateContent();
        contentNonCritical.setContent(Base64.getEncoder().encodeToString(CertificateTestUtil.createCertificateWithEku(false).getEncoded()));
        certificateContentRepository.save(contentNonCritical);
        Certificate certWithNonCriticalEku = new Certificate();
        certWithNonCriticalEku.setCertificateContent(contentNonCritical);
        certificateRepository.save(certWithNonCriticalEku);

        // Certificate without an EKU extension
        CertificateContent contentNoEku = new CertificateContent();
        contentNoEku.setContent(Base64.getEncoder().encodeToString(CertificateTestUtil.createCertificateWithoutEku().getEncoded()));
        certificateContentRepository.save(contentNoEku);
        Certificate certWithoutEku = new Certificate();
        certWithoutEku.setCertificateContent(contentNoEku);
        certificateRepository.save(certWithoutEku);

        // Certificate where the column is already populated — migration must leave it unchanged
        CertificateContent contentAlreadySet = new CertificateContent();
        contentAlreadySet.setContent(Base64.getEncoder().encodeToString(CertificateTestUtil.createCertificateWithEku(true).getEncoded()));
        certificateContentRepository.save(contentAlreadySet);
        Certificate certAlreadySet = new Certificate();
        certAlreadySet.setCertificateContent(contentAlreadySet);
        certAlreadySet.setExtendedKeyUsageCritical(false);
        certificateRepository.save(certAlreadySet);

        Context context = Mockito.mock(Context.class);
        when(context.getConnection()).thenReturn(dataSource.getConnection());

        // Simulate pre-migration state: NULL out the column for the three certificates that should be back-filled
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute("""
                    UPDATE certificate SET extended_key_usage_critical = NULL WHERE uuid IN ('%s', '%s', '%s')
                    """.formatted(certWithCriticalEku.getUuid(), certWithNonCriticalEku.getUuid(), certWithoutEku.getUuid()));
        }

        new V202604011901__BackfillExtendedKeyUsageCritical().migrate(context);

        certWithCriticalEku = certificateRepository.findByUuid(certWithCriticalEku.getUuid()).orElseThrow();
        Assertions.assertTrue(certWithCriticalEku.getExtendedKeyUsageCritical(),
                "EKU critical flag should be TRUE for certificate with critical EKU extension");

        certWithNonCriticalEku = certificateRepository.findByUuid(certWithNonCriticalEku.getUuid()).orElseThrow();
        Assertions.assertFalse(certWithNonCriticalEku.getExtendedKeyUsageCritical(),
                "EKU critical flag should be FALSE for certificate with non-critical EKU extension");

        certWithoutEku = certificateRepository.findByUuid(certWithoutEku.getUuid()).orElseThrow();
        Assertions.assertFalse(certWithoutEku.getExtendedKeyUsageCritical(),
                "EKU critical flag should be FALSE for certificate without EKU extension");

        certAlreadySet = certificateRepository.findByUuid(certAlreadySet.getUuid()).orElseThrow();
        Assertions.assertFalse(certAlreadySet.getExtendedKeyUsageCritical(),
                "Already-populated EKU critical flag should remain unchanged");
    }
}
