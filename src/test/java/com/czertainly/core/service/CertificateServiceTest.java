package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.CertificateResponseDto;
import com.czertainly.api.model.client.certificate.CertificateUpdateObjectsDto;
import com.czertainly.api.model.client.certificate.RemoveCertificateDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.certificate.UploadCertificateRequestDto;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

public class CertificateServiceTest extends BaseSpringBootTest {

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

    private Certificate certificate;
    private CertificateContent certificateContent;
    private RaProfile raProfile;
    private Group group;

    private X509Certificate x509Cert;

    @BeforeEach
    public void setUp() throws GeneralSecurityException, IOException {
        certificateContent = new CertificateContent();
        certificateContent.setContent("123456");
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setSubjectDn("testCertificate");
        certificate.setIssuerDn("testCertificate");
        certificate.setSerialNumber("123456789");
        certificate.setStatus(CertificateStatus.VALID);
        certificate.setCertificateContent(certificateContent);
        certificate.setCertificateContentId(certificateContent.getId());
        certificate = certificateRepository.save(certificate);

        raProfile = new RaProfile();
        raProfile = raProfileRepository.save(raProfile);

        group = new Group();
        group = groupRepository.save(group);

        InputStream keyStoreStream = CertificateServiceTest.class.getClassLoader().getResourceAsStream("client1.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(keyStoreStream, "123456".toCharArray());

        x509Cert = (X509Certificate) keyStore.getCertificate("1");
    }

    @Test
    public void testListCertificates() {
        CertificateResponseDto certificateEntities = certificateService.listCertificates(SecurityFilter.create(), new SearchRequestDto());
        Assertions.assertNotNull(certificateEntities);
        Assertions.assertFalse(certificateEntities.getCertificates().isEmpty());
        Assertions.assertEquals(1, certificateEntities.getCertificates().size());
        Assertions.assertEquals(certificate.getUuid().toString(), certificateEntities.getCertificates().get(0).getUuid());
    }

    @Test
    public void testGetCertificate() throws NotFoundException, CertificateException, IOException {
        CertificateDetailDto dto = certificateService.getCertificate(certificate.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificate.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(certificate.getSerialNumber(), dto.getSerialNumber());
    }

    @Test
    public void testGetCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.getCertificate(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testCreateCertificateEntity() {
        Certificate cert = certificateService.createCertificateEntity(x509Cert);

        Assertions.assertNotNull(cert);
        Assertions.assertEquals("CLIENT1", cert.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", cert.getSerialNumber());
    }

    @Test
    public void testCheckCreateCertificate() throws CertificateException, AlreadyExistException, NoSuchAlgorithmException {
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
    public void testRemoveCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.deleteCertificate(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testRevokeCertificate() throws NotFoundException, CertificateException, IOException {
        certificateService.revokeCertificate(certificate.getSerialNumber());

        CertificateDetailDto dto = certificateService.getCertificate(certificate.getSecuredUuid());

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificate.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(certificate.getSerialNumber(), dto.getSerialNumber());
        Assertions.assertEquals(CertificateStatus.REVOKED, dto.getStatus());
    }

    @Test
    @Disabled("Revoke doesn't throw NotFoundException")
    public void testRevokeCertificate_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.revokeCertificate("abfbc322-29e1-11ed-a261-0242ac120002"));
    }

    @Test
    public void testUploadCertificate() throws CertificateException, AlreadyExistException, NoSuchAlgorithmException {
        UploadCertificateRequestDto request = new UploadCertificateRequestDto();
        request.setCertificate(Base64.getEncoder().encodeToString(x509Cert.getEncoded()));

        CertificateDetailDto dto = certificateService.upload(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("CLIENT1", dto.getCommonName());
        Assertions.assertEquals("177e75f42e95ecb98f831eb57de27b0bc8c47643", dto.getSerialNumber());
    }

    @Test
    public void testUpdateRaProfile() throws NotFoundException {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setRaProfileUuid(raProfile.getUuid().toString());

        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto);

        Assertions.assertEquals(raProfile, certificate.getRaProfile());
    }

    @Test
    public void testUpdateRaProfile_certificateNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setRaProfileUuid(raProfile.getUuid().toString());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), uuidDto));
    }

    @Test
    public void testUpdateRaProfile_raProfileNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setRaProfileUuid("abfbc322-29e1-11ed-a261-0242ac120002");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto));
    }

    @Test
    public void testUpdateCertificateGroup() throws NotFoundException {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setGroupUuid(group.getUuid().toString());

        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto);

        Assertions.assertEquals(group, certificate.getGroup());
    }

    @Test
    public void testUpdateCertificateGroup_certificateNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setGroupUuid(group.getUuid().toString());
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), uuidDto));
    }

    @Test
    public void testUpdateCertificateGroup_groupNotFound() {
        CertificateUpdateObjectsDto uuidDto = new CertificateUpdateObjectsDto();
        uuidDto.setGroupUuid("abfbc322-29e1-11ed-a261-0242ac120002");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(certificate.getSecuredUuid(), uuidDto));
    }


    @Test
    public void testUpdateOwner() throws NotFoundException {
        CertificateUpdateObjectsDto request = new CertificateUpdateObjectsDto();
        request.setOwner("newOwner");
        certificateService.updateCertificateObjects(certificate.getSecuredUuid(), request);

        Assertions.assertEquals(request.getOwner(), certificate.getOwner());
    }

    @Test
    public void testUpdateCertificateOwner_certificateNotFound() {
        CertificateUpdateObjectsDto dto = new CertificateUpdateObjectsDto();
        dto.setOwner("testOwner");
        Assertions.assertThrows(NotFoundException.class, () -> certificateService.updateCertificateObjects(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), dto));
    }

    //TODO lukas.rejha need to be fixed
    @Disabled
    @Test
    public void testSearchableFields() {
        final List<SearchFieldDataByGroupDto> response = certificateService.getSearchableFieldInformationByGroup();
        Assertions.assertNotNull(response);
        Assertions.assertFalse(response.isEmpty());
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        RemoveCertificateDto request = new RemoveCertificateDto();
        request.setUuids(List.of(certificate.getUuid().toString()));

        certificateService.bulkDeleteCertificate(SecurityFilter.create(), request);

        Assertions.assertAll(() -> certificateService.getCertificate(certificate.getSecuredUuid()));
    }
}
