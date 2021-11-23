package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.discovery.CertificateGroupDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateGroupRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
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
public class CertificateGroupServiceTest {

    private static final String CERTIFICATE_GROUP_NAME = "testCertificateGroup1";

    @Autowired
    private CertificateGroupService certificateGroupService;

    @Autowired
    private CertificateGroupRepository certificateGroupRepository;

    private CertificateGroup certificateGroup;

    @BeforeEach
    public void setUp() {
        certificateGroup = new CertificateGroup();
        certificateGroup.setName(CERTIFICATE_GROUP_NAME);
        certificateGroup = certificateGroupRepository.save(certificateGroup);
    }

    @Test
    public void testListCertificateGroups() {
        List<CertificateGroupDto> certificateGroups = certificateGroupService.listCertificateGroups();
        Assertions.assertNotNull(certificateGroups);
        Assertions.assertFalse(certificateGroups.isEmpty());
        Assertions.assertEquals(1, certificateGroups.size());
        Assertions.assertEquals(certificateGroup.getUuid(), certificateGroups.get(0).getUuid());
    }

    @Test
    public void testGetCertificateGroup() throws NotFoundException {
        CertificateGroupDto dto = certificateGroupService.getCertificateGroup(certificateGroup.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificateGroup.getUuid(), dto.getUuid());
        Assertions.assertEquals(certificateGroup.getName(), dto.getName());
    }

    @Test
    public void testGetCertificateGroup_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateGroupService.getCertificateGroup("wrong-uuid"));
    }

    @Test
    public void testAddCertificateGroup() throws ValidationException, AlreadyExistException {
        CertificateGroupDto request = new CertificateGroupDto();
        request.setName("test");

        CertificateGroupDto dto = certificateGroupService.createCertificateGroup(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddCertificateGroup_validationFail() {
        CertificateGroupDto request = new CertificateGroupDto();
        Assertions.assertThrows(ValidationException.class, () -> certificateGroupService.createCertificateGroup(request));
    }

    @Test
    public void testAddCertificateGroup_alreadyExist() {
        CertificateGroupDto request = new CertificateGroupDto();
        request.setName(CERTIFICATE_GROUP_NAME); // certificateGroup with same name exist

        Assertions.assertThrows(AlreadyExistException.class, () -> certificateGroupService.createCertificateGroup(request));
    }

    @Test
    public void testEditCertificateGroup() throws NotFoundException {
        CertificateGroupDto request = new CertificateGroupDto();
        request.setName("Test");
        request.setDescription("some description");

        CertificateGroupDto dto = certificateGroupService.updateCertificateGroup(certificateGroup.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditCertificateGroup_notFound() {
        CertificateGroupDto request = new CertificateGroupDto();
        Assertions.assertThrows(NotFoundException.class, () -> certificateGroupService.updateCertificateGroup("wrong-uuid", request));
    }

    @Test
    public void testRemoveCertificateGroup() throws NotFoundException {
        certificateGroupService.removeCertificateGroup(certificateGroup.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> certificateGroupService.getCertificateGroup(certificateGroup.getUuid()));
    }

    @Test
    public void testRemoveCertificateGroup_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> certificateGroupService.removeCertificateGroup("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() {
        certificateGroupService.bulkRemoveCertificateGroup(List.of(certificateGroup.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> certificateGroupService.getCertificateGroup(certificateGroup.getUuid()));
    }
}
