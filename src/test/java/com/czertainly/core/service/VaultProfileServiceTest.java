package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.AttributeContent;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.connector.secrets.SecretType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.secret.SecretState;
import com.czertainly.api.model.core.vaultprofile.*;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;


class VaultProfileServiceTest extends BaseSpringBootTest {

    public static final String TEST_CUSTOM_ATTRIBUTE = "testCustomAttribute";

    @Autowired
    private VaultProfileService vaultProfileService;

    @Autowired
    private VaultProfileRepository vaultProfileRepository;
    @Autowired
    private VaultInstanceRepository vaultInstanceRepository;
    @Autowired
    private AttributeService attributeService;
    @Autowired
    private SecretRepository secretRepository;
    @Autowired
    private SecretVersionRepository secretVersionRepository;
    @Autowired
    private Secret2SyncVaultProfileRepository secret2SyncVaultProfileRepository;

    private VaultProfile vaultProfile;
    private VaultInstance vaultInstance;

    @BeforeEach
    void setUp() throws AlreadyExistException, AttributeException {
        vaultInstance = new VaultInstance();
        vaultInstance.setName("testInstance");
        vaultInstanceRepository.save(vaultInstance);

        vaultProfile = new VaultProfile();
        vaultProfile.setName("testProfile");
        vaultProfile.setVaultInstance(vaultInstance);
        vaultProfile.setVaultInstanceUuid(vaultInstance.getUuid());
        vaultProfileRepository.save(vaultProfile);

        CustomAttributeCreateRequestDto dto = new CustomAttributeCreateRequestDto();
        dto.setName(TEST_CUSTOM_ATTRIBUTE);
        dto.setLabel("Test Attribute");
        dto.setContentType(AttributeContentType.STRING);
        dto.setResources(List.of(Resource.VAULT_PROFILE));
        attributeService.createCustomAttribute(dto);
    }

    @Test
    void testCreateVaultProfile() throws NotFoundException, AttributeException, AlreadyExistException {
        VaultProfileRequestDto requestDto = new VaultProfileRequestDto();
        requestDto.setName(vaultProfile.getName());
        Assertions.assertThrows(AlreadyExistException.class, () -> vaultProfileService.createVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), requestDto));
        requestDto.setName("testProfile2");
        requestDto.setDescription("test description");
        RequestAttributeV3 attribute = new RequestAttributeV3();
        attribute.setName(TEST_CUSTOM_ATTRIBUTE);
        attribute.setContent(List.of(new StringAttributeContentV3("ref", "data")));
        requestDto.setCustomAttributes(List.of(attribute));
        Assertions.assertThrows(NotFoundException.class, () -> vaultProfileService.createVaultProfile(SecuredParentUUID.fromUUID(UUID.randomUUID()), requestDto));
        VaultProfileDetailDto createdProfile = vaultProfileService.createVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), requestDto);
        Assertions.assertNotNull(createdProfile);
        Assertions.assertEquals(requestDto.getName(), createdProfile.getName());
        Assertions.assertNotNull(createdProfile.getUuid());
        Assertions.assertEquals(requestDto.getDescription(), createdProfile.getDescription());
        Assertions.assertEquals(vaultInstance.getUuid().toString(), createdProfile.getVaultInstance().getUuid());
        Assertions.assertNotNull(createdProfile.getCustomAttributes());
        Assertions.assertEquals(1, createdProfile.getCustomAttributes().size());
        Assertions.assertEquals(attribute.getName(), createdProfile.getCustomAttributes().getFirst().getName());
        Assertions.assertEquals("data", ((List<AttributeContent>) createdProfile.getCustomAttributes().getFirst().getContent()).getFirst().getData());
    }

    @Test
    void testUpdateVaultProfile() throws NotFoundException, AttributeException {
        Assertions.assertThrows(NotFoundException.class, () -> vaultProfileService.updateVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(UUID.randomUUID()), new VaultProfileUpdateRequestDto()));
        VaultProfileUpdateRequestDto requestDto = new VaultProfileUpdateRequestDto();
        requestDto.setDescription("new description");
        RequestAttributeV3 attribute = new RequestAttributeV3();
        attribute.setName(TEST_CUSTOM_ATTRIBUTE);
        attribute.setContent(List.of(new StringAttributeContentV3("ref", "data")));
        requestDto.setCustomAttributes(List.of(attribute));
        VaultProfileDetailDto detailDto = vaultProfileService.updateVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(vaultProfile.getUuid()), requestDto);

        Assertions.assertNotNull(detailDto);
        Assertions.assertEquals(requestDto.getDescription(), detailDto.getDescription());
        Assertions.assertEquals(vaultInstance.getUuid().toString(), detailDto.getVaultInstance().getUuid());
        Assertions.assertNotNull(detailDto.getCustomAttributes());
        Assertions.assertEquals(1, detailDto.getCustomAttributes().size());
        Assertions.assertEquals(attribute.getName(), detailDto.getCustomAttributes().getFirst().getName());
        Assertions.assertEquals("data", ((List<AttributeContent>) detailDto.getCustomAttributes().getFirst().getContent()).getFirst().getData());
    }

    @Test
    void testDeleteVaultProfile() throws NotFoundException {
        SecuredParentUUID vaultUuid = SecuredParentUUID.fromUUID(vaultInstance.getUuid());
        Assertions.assertThrows(NotFoundException.class, () -> vaultProfileService.deleteVaultProfile(vaultUuid, SecuredUUID.fromUUID(UUID.randomUUID())));

        Secret secret = new Secret();
        secret.setName("testSecret");
        secret.setType(SecretType.BASIC_AUTH);
        secret.setState(SecretState.ACTIVE);
        secret.setSourceVaultProfile(vaultProfile);
        secret.setSourceVaultProfileUuid(vaultProfile.getUuid());

        SecretVersion secretVersion = new SecretVersion();
        secretVersion.setVaultInstance(vaultInstance);
        secretVersion.setVaultInstanceUuid(vaultInstance.getUuid());
        secretVersion.setFingerprint("testFingerprint");
        secretVersionRepository.save(secretVersion);

        secret.setLatestVersionUuid(secretVersion.getUuid());
        secretRepository.save(secret);

        secretVersion.setSecret(secret);
        secretVersion.setSecretUuid(secret.getUuid());
        secretVersionRepository.save(secretVersion);


        SecuredUUID profileUuid = SecuredUUID.fromUUID(vaultProfile.getUuid());
        Assertions.assertThrows(ValidationException.class, () -> vaultProfileService.deleteVaultProfile(vaultUuid, profileUuid));

        VaultProfile vaultProfile2 = new VaultProfile();
        vaultProfile2.setName("testProfile2");
        vaultProfile2.setVaultInstance(vaultInstance);
        vaultProfile2.setVaultInstanceUuid(vaultInstance.getUuid());
        vaultProfileRepository.save(vaultProfile2);
        secret.setSourceVaultProfile(vaultProfile2);
        secret.setSourceVaultProfileUuid(vaultProfile2.getUuid());
        secretRepository.save(secret);

        Secret2SyncVaultProfileId secret2SyncVaultProfileId = new Secret2SyncVaultProfileId();
        secret2SyncVaultProfileId.setSecretUuid(secret.getUuid());
        secret2SyncVaultProfileId.setVaultProfileUuid(vaultProfile.getUuid());
        Secret2SyncVaultProfile secret2SyncVaultProfile = new Secret2SyncVaultProfile();
        secret2SyncVaultProfile.setId(secret2SyncVaultProfileId);
        secret2SyncVaultProfile.setSecret(secret);
        secret2SyncVaultProfile.setVaultProfile(vaultProfile);
        secret2SyncVaultProfileRepository.save(secret2SyncVaultProfile);

        Assertions.assertThrows(ValidationException.class, () -> vaultProfileService.deleteVaultProfile(vaultUuid, profileUuid));

        secret2SyncVaultProfileRepository.delete(secret2SyncVaultProfile);

        vaultProfileService.deleteVaultProfile(vaultUuid, profileUuid);
        Assertions.assertNull(vaultProfileRepository.findByUuid(profileUuid).orElse(null));
    }


    @Test
    void testGetVaultProfile() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> vaultProfileService.getVaultProfileDetails(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(UUID.randomUUID())));
        VaultProfileDetailDto detailDto = vaultProfileService.getVaultProfileDetails(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(vaultProfile.getUuid()));
        Assertions.assertNotNull(detailDto);
        Assertions.assertEquals(vaultProfile.getName(), detailDto.getName());
        Assertions.assertEquals(vaultProfile.getDescription(), detailDto.getDescription());
        Assertions.assertEquals(vaultInstance.getUuid().toString(), detailDto.getVaultInstance().getUuid());
    }

    @Test
    void testListVaultProfiles() {
        SearchRequestDto searchRequestDto = new SearchRequestDto();
        searchRequestDto.setFilters(List.of(
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.VAULT_PROFILE_NAME.name(), FilterConditionOperator.CONTAINS, vaultProfile.getName()),
                new SearchFilterRequestDto(FilterFieldSource.PROPERTY, FilterField.VAULT_PROFILE_VAULT_INSTANCE.name(), FilterConditionOperator.EQUALS, (java.io.Serializable) List.of(vaultInstance.getName()))
        ));
        PaginationResponseDto<VaultProfileDto> profiles = vaultProfileService.listVaultProfiles(searchRequestDto, SecurityFilter.create());
        Assertions.assertEquals(1, profiles.getItems().size());
        Assertions.assertEquals(vaultProfile.getUuid().toString(), profiles.getItems().getFirst().getUuid());
    }

    @Test
    void testEnableVaultProfile() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> vaultProfileService.enableVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(UUID.randomUUID())));
        vaultProfileService.enableVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(vaultProfile.getUuid()));
        VaultProfile updatedProfile = vaultProfileRepository.findByUuid(SecuredUUID.fromUUID(vaultProfile.getUuid())).orElseThrow();
        Assertions.assertTrue(updatedProfile.isEnabled());
    }

    @Test
    void testDisableVaultProfile() throws NotFoundException {
        Assertions.assertThrows(NotFoundException.class, () -> vaultProfileService.disableVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(UUID.randomUUID())));
        vaultProfileService.disableVaultProfile(SecuredParentUUID.fromUUID(vaultInstance.getUuid()), SecuredUUID.fromUUID(vaultProfile.getUuid()));
        VaultProfile updatedProfile = vaultProfileRepository.findByUuid(SecuredUUID.fromUUID(vaultProfile.getUuid())).orElseThrow();
        Assertions.assertFalse(updatedProfile.isEnabled());
    }

}
