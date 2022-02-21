package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerRequestDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class CertificateServiceTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private EntityRepository entityRepository;

    private Certificate certificate;
    private CertificateContent certificateContent;
    private RaProfile raProfile;
    private CertificateGroup certificateGroup;
    private CertificateEntity certificateEntity;

    private X509Certificate x509Cert;

    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException {
        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setCertificateContent(certificateContent);
        certificate = certificateRepository.save(certificate);

        raProfile = new RaProfile();
        raProfile = raProfileRepository.save(raProfile);

        certificateGroup = new CertificateGroup();
        certificateGroup = groupRepository.save(certificateGroup);

        certificateEntity = new CertificateEntity();
        certificateEntity = entityRepository.save(certificateEntity);

        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyStoreStream, "123456".toCharArray());

        x509Cert = (X509Certificate) keyStore.getCertificate("1");
    }

    @Test
    public void testListCertificates() {
        CertificateResponseDto certificateEntities = certificateService.listCertificates(new CertificateSearchRequestDto());
        Assertions.assertNotNull(certificateEntities);
        Assertions.assertFalse(certificateEntities.getCertificates().isEmpty());
        Assertions.assertEquals(1, certificateEntities.getCertificates().size());
        Assertions.assertEquals(certificate.getUuid(), certificateEntities.getCertificates().get(0).getUuid());
    }

    @Test
    public void testGetCertificate() throws NotFoundException {
        CertificateDto dto = certificateService.getCertificate(certificate.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificate.getUuid(), dto.getUuid());
        Assertions.assertEquals(certificate.getSerialNumber(), dto.getSerialNumber());
    }

    @Test
    public void testGetCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.getCertificate("wrong-uuid"));
    }

    @Test
    public void testCreateCertificateEntity() {
        Certificate cert = certificateService.createCertificateEntity(x509Cert);

        Assertions.assertNotNull(cert);
        Assertions.assertEquals("CLIENT1", cert.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", cert.getSerialNumber());
    }

    @Test
    public void testCheckCreateCertificate() throws CertificateException, AlreadyExistException {
        Certificate cert = certificateService.checkCreateCertificate(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));

        Assertions.assertNotNull(cert);
        Assertions.assertEquals("CLIENT1", cert.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", cert.getSerialNumber());
    }

    @Test
    public void testAddCertificate_certificateException() {
        Assertions.assertThrows(CertificateException.class, () -> certificateService.checkCreateCertificate("certificate"));
    }

    @Test
    public void testRemoveCertificate() throws NotFoundException {
        certificateService.removeCertificate(certificate.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.getCertificate(certificate.getUuid()));
    }

    @Test
    public void testRemoveCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.removeCertificate("wrong-uuid"));
    }

    @Test
    public void testRevokeCertificate() throws NotFoundException {
        certificateService.revokeCertificate(certificate.getSerialNumber());

        CertificateDto dto = certificateService.getCertificate(certificate.getUuid());

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificate.getUuid(), dto.getUuid());
        Assertions.assertEquals(certificate.getSerialNumber(), dto.getSerialNumber());
        Assertions.assertEquals(CertificateStatus.REVOKED, dto.getStatus());
    }

    @Test
    @Disabled("Revoke doesn't throw NotFoundException")
    public void testRevokeCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.revokeCertificate("wrong-uuid"));
    }

    @Test
    public void testUploadCertificate() throws CertificateException, AlreadyExistException {
        UploadCertificateRequestDto request = new UploadCertificateRequestDto();
        request.setCertificate(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));

        CertificateDto dto = certificateService.upload(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("CLIENT1", dto.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", dto.getSerialNumber());
    }

    @Test
    public void testUpdateIssuer() {
        // TODO: improve test for non self-signed certificates
        certificateService.updateIssuer();
    }

    @Test
    public void testUpdateRaProfile() throws NotFoundException {
        CertificateUpdateRAProfileDto uuidDto = new CertificateUpdateRAProfileDto();
        uuidDto.setRaProfileUuid(raProfile.getUuid());

        certificateService.updateRaProfile(certificate.getUuid(), uuidDto);

        Assertions.assertEquals(raProfile, certificate.getRaProfile());
    }

    @Test
    public void testUpdateRaProfile_certificateNotFound() {
        CertificateUpdateRAProfileDto uuidDto = new CertificateUpdateRAProfileDto();
        uuidDto.setRaProfileUuid(raProfile.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateRaProfile("wrong-uuid", uuidDto));
    }

    @Test
    public void testUpdateRaProfile_raProfileNotFound() {
        CertificateUpdateRAProfileDto uuidDto = new CertificateUpdateRAProfileDto();
        uuidDto.setRaProfileUuid("wrong-uuid");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateRaProfile(certificate.getUuid(), uuidDto));
    }

    @Test
    public void testUpdateCertificateGroup() throws NotFoundException {
        CertificateUpdateGroupDto uuidDto = new CertificateUpdateGroupDto();
        uuidDto.setGroupUuid(certificateGroup.getUuid());

        certificateService.updateCertificateGroup(certificate.getUuid(), uuidDto);

        Assertions.assertEquals(certificateGroup, certificate.getGroup());
    }

    @Test
    public void testUpdateCertificateGroup_certificateNotFound() {
        CertificateUpdateGroupDto uuidDto = new CertificateUpdateGroupDto();
        uuidDto.setGroupUuid(certificateGroup.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateGroup("wrong-uuid", uuidDto));
    }

    @Test
    public void testUpdateCertificateGroup_groupNotFound() {
        CertificateUpdateGroupDto uuidDto = new CertificateUpdateGroupDto();
        uuidDto.setGroupUuid("wrong-uuid");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateGroup(certificate.getUuid(), uuidDto));
    }

    @Test
    public void testUpdateCertificateEntity() throws NotFoundException {
        CertificateUpdateEntityDto uuidDto = new CertificateUpdateEntityDto();
        uuidDto.setEntityUuid(certificateEntity.getUuid());

        certificateService.updateEntity(certificate.getUuid(), uuidDto);

        Assertions.assertEquals(certificateEntity, certificate.getEntity());
    }

    @Test
    public void testUpdateCertificateEntity_certificateNotFound() {
        CertificateUpdateEntityDto uuidDto = new CertificateUpdateEntityDto();
        uuidDto.setEntityUuid(certificateEntity.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateEntity("wrong-uuid", uuidDto));
    }

    @Test
    public void testUpdateCertificateEntity_entityNotFound() {
        CertificateUpdateEntityDto uuidDto = new CertificateUpdateEntityDto();
        uuidDto.setEntityUuid("wrong-uuid");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateEntity(certificate.getUuid(), uuidDto));
    }

    @Test
    public void testUpdateOwner() throws NotFoundException {
        CertificateOwnerRequestDto request = new CertificateOwnerRequestDto();
        request.setOwner("newOwner");

        certificateService.updateOwner(certificate.getUuid(), request);

        Assertions.assertEquals(request.getOwner(), certificate.getOwner());
    }

    @Test
    public void testUpdateCertificateOwner_certificateNotFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateOwner("wrong-uuid", null));
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        RemoveCertificateDto request = new RemoveCertificateDto();
        request.setUuids(List.of(certificate.getUuid()));

        certificateService.bulkRemoveCertificate(request);

        Assertions.assertThrows(NotFoundException.class, () -> certificateService.getCertificate(certificate.getUuid()));
    }
}
