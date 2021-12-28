package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.entity.CertificateEntityDto;
import com.czertainly.api.model.core.certificate.entity.CertificateEntityRequestDto;
import com.czertainly.core.dao.entity.CertificateEntity;
import com.czertainly.core.dao.repository.CertificateEntityRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class CertificateEntityServiceTest {

    private static final String CERTIFICATE_NAME = "testCertificateEntity1";

    @Autowired
    private CertificateEntityService certificateEntityService;

    @Autowired
    private CertificateEntityRepository certificateEntityRepository;

    private CertificateEntity certificateEntity;

    @BeforeEach
    public void setUp() {
        certificateEntity = new CertificateEntity();
        certificateEntity.setName(CERTIFICATE_NAME);
        certificateEntity = certificateEntityRepository.save(certificateEntity);
    }

    @Test
    public void testListCertificateEntitys() {
        List<CertificateEntityDto> certificateEntities = certificateEntityService.listCertificateEntity();
        Assertions.assertNotNull(certificateEntities);
        Assertions.assertFalse(certificateEntities.isEmpty());
        Assertions.assertEquals(1, certificateEntities.size());
        Assertions.assertEquals(certificateEntity.getUuid(), certificateEntities.get(0).getUuid());
    }

    @Test
    public void testGetCertificateEntity() throws NotFoundException {
        CertificateEntityDto dto = certificateEntityService.getCertificateEntity(certificateEntity.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificateEntity.getUuid(), dto.getUuid());
        Assertions.assertEquals(certificateEntity.getName(), dto.getName());
    }

    @Test
    public void testGetCertificateEntity_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateEntityService.getCertificateEntity("wrong-uuid"));
    }

    @Test
    public void testAddCertificateEntity() throws ValidationException, AlreadyExistException {
        CertificateEntityRequestDto request = new CertificateEntityRequestDto();
        request.setName("test");

        CertificateEntityDto dto = certificateEntityService.createCertificateEntity(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddCertificateEntity_validationFail() {
        CertificateEntityRequestDto request = new CertificateEntityRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> certificateEntityService.createCertificateEntity(request));
    }

    @Test
    public void testAddCertificateEntity_alreadyExist() {
        CertificateEntityRequestDto request = new CertificateEntityRequestDto();
        request.setName(CERTIFICATE_NAME); // certificateEntity with same name exist

        Assertions.assertThrows(AlreadyExistException.class, () -> certificateEntityService.createCertificateEntity(request));
    }

    @Test
    public void testEditCertificateEntity() throws NotFoundException {
        CertificateEntityRequestDto request = new CertificateEntityRequestDto();
        request.setName("Test");
        request.setDescription("some description");

        CertificateEntityDto dto = certificateEntityService.updateCertificateEntity(certificateEntity.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditCertificateEntity_notFound() {
        CertificateEntityRequestDto request = new CertificateEntityRequestDto();
        Assertions.assertThrows(NotFoundException.class, () -> certificateEntityService.updateCertificateEntity("wrong-uuid", request));
    }

    @Test
    public void testRemoveCertificateEntity() throws NotFoundException {
        certificateEntityService.removeCertificateEntity(certificateEntity.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> certificateEntityService.getCertificateEntity(certificateEntity.getUuid()));
    }

    @Test
    public void testRemoveCertificateEntity_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateEntityService.removeCertificateEntity("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() {
        certificateEntityService.bulkRemoveCertificateEntity(List.of(certificateEntity.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> certificateEntityService.getCertificateEntity(certificateEntity.getUuid()));
    }
}
