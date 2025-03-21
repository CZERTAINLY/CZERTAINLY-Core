package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

class AcmeProfileServiceTest extends BaseSpringBootTest {

    @Autowired
    private AcmeProfileService acmeProfileService;

    @Autowired
    private AcmeProfileRepository acmeProfileRepository;

    private AcmeProfile acmeProfile;

    @BeforeEach
    public void setUp() {
        acmeProfile = new AcmeProfile();
        acmeProfile.setWebsite("sample website");
        acmeProfile.setTermsOfServiceUrl("sample terms");
        acmeProfile.setValidity(30);
        acmeProfile.setRetryInterval(30);
        acmeProfile.setDescription("sample description");
        acmeProfile.setName("sameName");
        acmeProfile.setDnsResolverPort("53");
        acmeProfile.setDnsResolverIp("localhost");
        acmeProfile.setTermsOfServiceChangeUrl("change url");
        acmeProfile.setEnabled(false);
        acmeProfileRepository.save(acmeProfile);
    }

    @Test
    void testListAcmeProfiles() {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        List<AcmeProfileListDto> acmeProfiles = acmeProfileService.listAcmeProfile(SecurityFilter.create());
        Assertions.assertNotNull(acmeProfiles);
        Assertions.assertFalse(acmeProfiles.isEmpty());
        Assertions.assertEquals(1, acmeProfiles.size());
        Assertions.assertEquals(acmeProfile.getUuid().toString(), acmeProfiles.get(0).getUuid());
    }

    @Test
    void testGetAcmeProfileByUuid() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        AcmeProfileDto dto = acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(acmeProfile.getUuid().toString(), dto.getUuid());
    }

    @Test
    void testGetAcmeProfileByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testAddAcmeProfile() throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException {

        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        request.setName("Test");
        request.setDescription("sample");
        request.setDnsResolverIp("localhost");
        request.setDnsResolverPort("53");

        AcmeProfileDto dto = acmeProfileService.createAcmeProfile(request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getName(), dto.getName());
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
    }

    @Test
    void testAddAcmeProfile_validationFail() {
        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> acmeProfileService.createAcmeProfile(request));
    }

    @Test
    void testAddAcmeProfile_alreadyExist() {
        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        request.setName("sameName");

        Assertions.assertThrows(AlreadyExistException.class, () -> acmeProfileService.createAcmeProfile(request));
    }

    @Test
    void testEditAcmeProfile() throws ConnectorException, AttributeException, NotFoundException {

        acmeProfile.setEnabled(false);
        acmeProfileRepository.save(acmeProfile);

        AcmeProfileEditRequestDto request = new AcmeProfileEditRequestDto();
        request.setDescription("sample");
        request.setDnsResolverIp("sample");
        request.setDnsResolverPort("32");

        AcmeProfileDto dto = acmeProfileService.editAcmeProfile(acmeProfile.getSecuredUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
        Assertions.assertEquals(request.getDnsResolverIp(), dto.getDnsResolverIp());
    }

    @Test
    void testEditAcmeProfile_validationFail() {
        AcmeProfileEditRequestDto request = new AcmeProfileEditRequestDto();
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.editAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"), request));
    }

    @Test
    void testRemoveAcmeProfile() throws NotFoundException {
        acmeProfileService.deleteAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()));
    }

    @Test
    void testRemoveAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testEnableAcmeProfile() throws NotFoundException {
        acmeProfileService.enableAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertEquals(true, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testEnableAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.enableAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testDisableAcmeProfile() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        acmeProfileService.disableAcmeProfile(acmeProfile.getSecuredUuid());
        Assertions.assertEquals(false, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testDisableAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.disableAcmeProfile(SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    void testBulkRemove() {
        acmeProfileService.bulkDeleteAcmeProfile(List.of(acmeProfile.getSecuredUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()));
    }

    @Test
    void testBulkEnable() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        acmeProfileService.bulkEnableAcmeProfile(List.of(acmeProfile.getSecuredUuid()));
        Assertions.assertEquals(true, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testBulkDisable() throws NotFoundException {
        acmeProfileService.bulkDisableAcmeProfile(List.of(acmeProfile.getSecuredUuid()));
        Assertions.assertEquals(false, acmeProfileService.getAcmeProfile(acmeProfile.getSecuredUuid()).isEnabled());
    }

    @Test
    void testGetObjectsForResource() {
        List<NameAndUuidDto> dtos = acmeProfileService.listResourceObjects(SecurityFilter.create());
        Assertions.assertEquals(1, dtos.size());
    }
}
