package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.entity.EntityDto;
import com.czertainly.api.model.core.certificate.entity.EntityRequestDto;
import com.czertainly.core.dao.entity.CertificateEntity;
import com.czertainly.core.dao.repository.EntityRepository;
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
    private EntityService entityService;

    @Autowired
    private EntityRepository entityRepository;

    private CertificateEntity certificateEntity;

    @BeforeEach
    public void setUp() {
        certificateEntity = new CertificateEntity();
        certificateEntity.setName(CERTIFICATE_NAME);
        certificateEntity = entityRepository.save(certificateEntity);
    }

    @Test
    public void testListCertificateEntitys() {
        List<EntityDto> certificateEntities = entityService.listEntity();
        Assertions.assertNotNull(certificateEntities);
        Assertions.assertFalse(certificateEntities.isEmpty());
        Assertions.assertEquals(1, certificateEntities.size());
        Assertions.assertEquals(certificateEntity.getUuid(), certificateEntities.get(0).getUuid());
    }

    @Test
    public void testGetCertificateEntity() throws NotFoundException {
        EntityDto dto = entityService.getCertificateEntity(certificateEntity.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificateEntity.getUuid(), dto.getUuid());
        Assertions.assertEquals(certificateEntity.getName(), dto.getName());
    }

    @Test
    public void testGetCertificateEntity_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> entityService.getCertificateEntity("wrong-uuid"));
    }

    @Test
    public void testAddCertificateEntity() throws ValidationException, AlreadyExistException {
        EntityRequestDto request = new EntityRequestDto();
        request.setName("test");

        EntityDto dto = entityService.createEntity(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddCertificateEntity_validationFail() {
        EntityRequestDto request = new EntityRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> entityService.createEntity(request));
    }

    @Test
    public void testAddCertificateEntity_alreadyExist() {
        EntityRequestDto request = new EntityRequestDto();
        request.setName(CERTIFICATE_NAME); // certificateEntity with same name exist

        Assertions.assertThrows(AlreadyExistException.class, () -> entityService.createEntity(request));
    }

    @Test
    public void testEditCertificateEntity() throws NotFoundException {
        EntityRequestDto request = new EntityRequestDto();
        request.setName("Test");
        request.setDescription("some description");

        EntityDto dto = entityService.updateEntity(certificateEntity.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditCertificateEntity_notFound() {
        EntityRequestDto request = new EntityRequestDto();
        Assertions.assertThrows(NotFoundException.class, () -> entityService.updateEntity("wrong-uuid", request));
    }

    @Test
    public void testRemoveCertificateEntity() throws NotFoundException {
        entityService.removeEntity(certificateEntity.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> entityService.getCertificateEntity(certificateEntity.getUuid()));
    }

    @Test
    public void testRemoveCertificateEntity_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> entityService.removeEntity("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() {
        entityService.bulkRemoveEntity(List.of(certificateEntity.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> entityService.getCertificateEntity(certificateEntity.getUuid()));
    }
}
