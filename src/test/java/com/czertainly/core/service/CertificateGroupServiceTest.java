package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class CertificateGroupServiceTest extends BaseSpringBootTest {

    private static final String CERTIFICATE_GROUP_NAME = "testCertificateGroup1";

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    private CertificateGroup certificateGroup;

    @BeforeEach
    public void setUp() {
        certificateGroup = new CertificateGroup();
        certificateGroup.setName(CERTIFICATE_GROUP_NAME);
        certificateGroup = groupRepository.save(certificateGroup);
    }

    @Test
    public void testListCertificateGroups() {
        List<GroupDto> certificateGroups = groupService.listGroups(SecurityFilter.create());
        Assertions.assertNotNull(certificateGroups);
        Assertions.assertFalse(certificateGroups.isEmpty());
        Assertions.assertEquals(1, certificateGroups.size());
        Assertions.assertEquals(certificateGroup.getUuid(), certificateGroups.get(0).getUuid());
    }

    @Test
    public void testGetCertificateGroup() throws NotFoundException {
        GroupDto dto = groupService.getCertificateGroup(certificateGroup.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(certificateGroup.getUuid(), dto.getUuid());
        Assertions.assertEquals(certificateGroup.getName(), dto.getName());
    }

    @Test
    public void testGetCertificateGroup_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> groupService.getCertificateGroup(SecuredUUID.fromString("wrong-uuid")));
    }

    @Test
    public void testAddCertificateGroup() throws ValidationException, AlreadyExistException {
        GroupRequestDto request = new GroupRequestDto();
        request.setName("test");

        GroupDto dto = groupService.createGroup(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
    }

    @Test
    public void testAddCertificateGroup_validationFail() {
        GroupRequestDto request = new GroupRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> groupService.createGroup(request));
    }

    @Test
    public void testAddCertificateGroup_alreadyExist() {
        GroupRequestDto request = new GroupRequestDto();
        request.setName(CERTIFICATE_GROUP_NAME); // certificateGroup with same name exist

        Assertions.assertThrows(AlreadyExistException.class, () -> groupService.createGroup(request));
    }

    @Test
    public void testEditCertificateGroup() throws NotFoundException {
        GroupRequestDto request = new GroupRequestDto();
        request.setName("Test");
        request.setDescription("some description");

        GroupDto dto = groupService.updateGroup(certificateGroup.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    public void testEditCertificateGroup_notFound() {
        GroupRequestDto request = new GroupRequestDto();
        Assertions.assertThrows(
                NotFoundException.class,
                () -> groupService.updateGroup(SecuredUUID.fromString("wrong-uuid"), request)
        );
    }

    @Test
    public void testRemoveCertificateGroup() throws NotFoundException {
        groupService.removeGroup(certificateGroup.getSecuredUuid());
        Assertions.assertThrows(
                NotFoundException.class,
                () -> groupService.getCertificateGroup(certificateGroup.getSecuredUuid())
        );
    }

    @Test
    public void testRemoveCertificateGroup_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> groupService.removeGroup(SecuredUUID.fromString("wrong-uuid"))
        );
    }

    @Test
    public void testBulkRemove() {
        groupService.bulkRemoveGroup(List.of(certificateGroup.getSecuredUuid()));
        Assertions.assertThrows(
                NotFoundException.class,
                () -> groupService.getCertificateGroup(certificateGroup.getSecuredUuid())
        );
    }
}
