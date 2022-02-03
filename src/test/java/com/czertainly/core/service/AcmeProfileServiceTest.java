package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;
import java.util.List;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="SUPERADMINISTRATOR")
public class AcmeProfileServiceTest {

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
        acmeProfile.setUuid("1757e43e-7d12-11ec-90d6-0242ac120003");
        acmeProfileRepository.save(acmeProfile);
    }

    @Test
    public void testListAcmeProfiles() {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        List<AcmeProfileListDto> acmeProfiles = acmeProfileService.listAcmeProfile();
        Assertions.assertNotNull(acmeProfiles);
        Assertions.assertFalse(acmeProfiles.isEmpty());
        Assertions.assertEquals(1, acmeProfiles.size());
        Assertions.assertEquals(acmeProfile.getUuid(), acmeProfiles.get(0).getUuid());
    }

    @Test
    public void testGetAcmeProfileByUuid() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        AcmeProfileDto dto = acmeProfileService.getAcmeProfile(acmeProfile.getUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(acmeProfile.getUuid(), dto.getUuid());
        Assertions.assertNotNull(acmeProfile.getId());
    }

    @Test
    public void testGetAcmeProfileByUuid_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile("wrong-uuid"));
    }

    @Test
    public void testAddAcmeProfile() throws ConnectorException, AlreadyExistException {

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
    public void testAddAcmeProfile_validationFail() {
        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        Assertions.assertThrows(ValidationException.class, () -> acmeProfileService.createAcmeProfile(request));
    }

    @Test
    public void testAddAcmeProfile_alreadyExist() {
        AcmeProfileRequestDto request = new AcmeProfileRequestDto();
        request.setName("sameName");

        Assertions.assertThrows(AlreadyExistException.class, () -> acmeProfileService.createAcmeProfile(request));
    }

    @Test
    public void testEditAcmeProfile() throws ConnectorException {

        acmeProfile.setEnabled(false);
        acmeProfileRepository.save(acmeProfile);

        AcmeProfileEditRequestDto request = new AcmeProfileEditRequestDto();
        request.setDescription("sample");
        request.setDnsResolverIp("sample");
        request.setDnsResolverPort("32");

        AcmeProfileDto dto = acmeProfileService.updateAcmeProfile(acmeProfile.getUuid(), request);
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(request.getDescription(), dto.getDescription());
        Assertions.assertEquals(request.getDnsResolverIp(), dto.getDnsResolverIp());
    }

    @Test
    public void testEditAcmeProfile_validationFail() {
        AcmeProfileEditRequestDto request = new AcmeProfileEditRequestDto();
        Assertions.assertThrows(NullPointerException.class, () -> acmeProfileService.updateAcmeProfile(acmeProfile.getUuid(), request));
    }

    @Test
    public void testRemoveAcmeProfile() throws NotFoundException {
        acmeProfileService.deleteAcmeProfile(acmeProfile.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(acmeProfile.getUuid()));
    }

    @Test
    public void testRemoveAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile("some-id"));
    }

    @Test
    public void testEnableAcmeProfile() throws NotFoundException, CertificateException {
        acmeProfileService.enableAcmeProfile(acmeProfile.getUuid());
        Assertions.assertEquals(true, acmeProfileService.getAcmeProfile(acmeProfile.getUuid()).isEnabled());
    }

    @Test
    public void testEnableAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.enableAcmeProfile("wrong-uuid"));
    }

    @Test
    public void testDisableAcmeProfile() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        acmeProfileService.disableAcmeProfile(acmeProfile.getUuid());
        Assertions.assertEquals(false, acmeProfileService.getAcmeProfile(acmeProfile.getUuid()).isEnabled());
    }

    @Test
    public void testDisableAcmeProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.disableAcmeProfile("wrong-uuid"));
    }

    @Test
    public void testBulkRemove() {
        acmeProfileService.bulkDeleteAcmeProfile(List.of(acmeProfile.getUuid()));
        Assertions.assertThrows(NotFoundException.class, () -> acmeProfileService.getAcmeProfile(acmeProfile.getUuid()));
    }

    @Test
    public void testBulkEnable() throws NotFoundException {
        acmeProfile.setEnabled(true);
        acmeProfileRepository.save(acmeProfile);
        acmeProfileService.bulkEnableAcmeProfile(List.of(acmeProfile.getUuid()));
        Assertions.assertEquals(true, acmeProfileService.getAcmeProfile(acmeProfile.getUuid()).isEnabled());
    }

    @Test
    public void testBulkDisable() throws NotFoundException {
        acmeProfileService.bulkDisableAcmeProfile(List.of(acmeProfile.getUuid()));
        Assertions.assertEquals(false, acmeProfileService.getAcmeProfile(acmeProfile.getUuid()).isEnabled());
    }
}
