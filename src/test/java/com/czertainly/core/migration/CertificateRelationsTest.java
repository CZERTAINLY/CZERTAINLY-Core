package com.czertainly.core.migration;

import com.czertainly.api.model.core.certificate.CertificateRelationType;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateRelation;
import com.czertainly.core.dao.entity.CertificateRelationId;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.repository.CertificateRelationRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.util.BaseSpringBootTest;
import db.migration.V202508130940__CertificateRelations;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateRelationsTest extends BaseSpringBootTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    CertificateRepository certificateRepository;
    @Autowired
    CertificateRelationRepository certificateRelationRepository;
    @Autowired
    CryptographicKeyRepository cryptographicKeyRepository;

    @Test
    void testMigration() throws Exception {
        CryptographicKey cryptographicKey = new CryptographicKey();
        cryptographicKeyRepository.save(cryptographicKey);
        Certificate sourceCertificate = new Certificate();
        sourceCertificate.setSubjectDnNormalized("subjectDn");
        sourceCertificate.setIssuerDnNormalized("issuerDn");
        sourceCertificate.setKeyUuid(cryptographicKey.getUuid());
        certificateRepository.save(sourceCertificate);
        Certificate certificate1 = new Certificate();
        certificate1.setIssuerDnNormalized(sourceCertificate.getIssuerDnNormalized());
        certificate1.setSubjectDnNormalized(sourceCertificate.getSubjectDnNormalized());
        certificate1.setKeyUuid(sourceCertificate.getKeyUuid());
        certificate1.setSourceCertificateUuid(sourceCertificate.getUuid());
        Certificate certificate2 = new Certificate();
        certificate2.setIssuerDnNormalized(sourceCertificate.getIssuerDnNormalized());
        certificate2.setSubjectDnNormalized(sourceCertificate.getSubjectDnNormalized());
        certificate2.setSourceCertificateUuid(sourceCertificate.getUuid());
        Certificate certificate3 = new Certificate();
        certificate3.setSourceCertificateUuid(sourceCertificate.getUuid());
        certificateRepository.saveAll(List.of(certificate1, certificate2, certificate3));

        Context context = Mockito.mock(Context.class);
        when(context.getConnection()).thenReturn(dataSource.getConnection());

        V202508130940__CertificateRelations certificateRelationsMigration = new V202508130940__CertificateRelations();
        certificateRelationsMigration.migrate(context);

        Assertions.assertEquals(3, certificateRelationRepository.findAll().size());
        CertificateRelation relation1 = certificateRelationRepository.findById(new CertificateRelationId(certificate1.getUuid(), sourceCertificate.getUuid())).orElseThrow();
        Assertions.assertEquals(CertificateRelationType.RENEWAL, relation1.getRelationType());
        CertificateRelation relation2 = certificateRelationRepository.findById(new CertificateRelationId(certificate2.getUuid(), sourceCertificate.getUuid())).orElseThrow();
        Assertions.assertEquals(CertificateRelationType.REKEY, relation2.getRelationType());
        CertificateRelation relation3 = certificateRelationRepository.findById(new CertificateRelationId(certificate3.getUuid(), sourceCertificate.getUuid())).orElseThrow();
        Assertions.assertEquals(CertificateRelationType.REPLACEMENT, relation3.getRelationType());
    }
}
