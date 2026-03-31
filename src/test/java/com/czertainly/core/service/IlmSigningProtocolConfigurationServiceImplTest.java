package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttributeV3;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v3.CustomAttributeV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceObjectDto;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.AttributeRelation;
import com.czertainly.core.dao.entity.signing.IlmSigningProtocolConfiguration;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.core.dao.repository.signing.IlmSigningProtocolConfigurationRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class IlmSigningProtocolConfigurationServiceImplTest extends BaseSpringBootTest {

    private static final String CUSTOM_ATTR_UUID = "a1b2c3d4-0001-0002-0003-000000000001";
    private static final String CUSTOM_ATTR_NAME = "ilmTestAttribute";

    @Autowired
    private IlmSigningProtocolConfigurationService ilmService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private IlmSigningProtocolConfigurationRepository ilmRepository;

    @Autowired
    private SigningProfileRepository signingProfileRepository;

    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;

    @Autowired
    private AttributeRelationRepository attributeRelationRepository;

    private IlmSigningProtocolConfiguration savedConfig;
    private SigningProfile savedSigningProfile;

    @BeforeEach
    void setUp() {
        // Create a minimal SigningProfile to use as a default signing profile reference
        savedSigningProfile = new SigningProfile();
        savedSigningProfile.setName("test-signing-profile");
        savedSigningProfile.setDescription("Test signing profile");
        savedSigningProfile.setEnabled(false);
        savedSigningProfile.setSigningScheme(SigningScheme.DELEGATED);
        savedSigningProfile.setWorkflowType(SigningWorkflowType.RAW_SIGNING);
        savedSigningProfile.setLatestVersion(1);
        savedSigningProfile = signingProfileRepository.save(savedSigningProfile);

        // Create an ILM Signing Protocol configuration entity directly for tests that need pre-existing data
        savedConfig = new IlmSigningProtocolConfiguration();
        savedConfig.setName("existing-ilm-config");
        savedConfig.setDescription("Existing ILM config description");
        savedConfig = ilmRepository.save(savedConfig);

        // Register a custom attribute available for ILM Signing Protocol Configuration resources
        CustomAttributeV3 attrDef = new CustomAttributeV3();
        attrDef.setUuid(CUSTOM_ATTR_UUID);
        attrDef.setName(CUSTOM_ATTR_NAME);
        attrDef.setDescription("test custom attribute for ILM config");
        attrDef.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties props = new CustomAttributeProperties();
        props.setReadOnly(false);
        props.setRequired(false);
        attrDef.setProperties(props);

        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setUuid(UUID.fromString(CUSTOM_ATTR_UUID));
        attributeDefinition.setName(CUSTOM_ATTR_NAME);
        attributeDefinition.setAttributeUuid(UUID.fromString(CUSTOM_ATTR_UUID));
        attributeDefinition.setContentType(AttributeContentType.STRING);
        attributeDefinition.setLabel(CUSTOM_ATTR_NAME);
        attributeDefinition.setType(AttributeType.CUSTOM);
        attributeDefinition.setDefinition(attrDef);
        attributeDefinition.setEnabled(true);
        attributeDefinition.setVersion(3);
        attributeDefinitionRepository.save(attributeDefinition);

        AttributeRelation attributeRelation = new AttributeRelation();
        attributeRelation.setResource(Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION);
        attributeRelation.setAttributeDefinitionUuid(attributeDefinition.getUuid());
        attributeRelationRepository.save(attributeRelation);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // List
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testListIlmSigningProtocolConfigurations_returnsExistingEntries() {
        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<IlmSigningProtocolConfigurationListDto> response =
                ilmService.listIlmSigningProtocolConfigurations(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getItems());
        Assertions.assertEquals(1, response.getTotalItems());
        Assertions.assertEquals(savedConfig.getUuid().toString(), response.getItems().getFirst().getUuid());
        Assertions.assertEquals(savedConfig.getName(), response.getItems().getFirst().getName());
    }

    @Test
    void testListIlmSigningProtocolConfigurations_emptyWhenNoneExist() {
        ilmRepository.delete(savedConfig);

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<IlmSigningProtocolConfigurationListDto> response =
                ilmService.listIlmSigningProtocolConfigurations(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertEquals(0, response.getTotalItems());
        Assertions.assertTrue(response.getItems().isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Get
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testGetIlmSigningProtocolConfiguration_returnsCorrectDto() throws NotFoundException {
        IlmSigningProtocolConfigurationDto dto = ilmService.getIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid());

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(savedConfig.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(savedConfig.getName(), dto.getName());
        Assertions.assertEquals(savedConfig.getDescription(), dto.getDescription());
    }

    @Test
    void testGetIlmSigningProtocolConfiguration_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> ilmService.getIlmSigningProtocolConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateIlmSigningProtocolConfiguration_assertDtoAndDbEntity()
            throws AttributeException, NotFoundException {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("new-ilm-signing-protocol-config");
        request.setDescription("New ILM Signing Protocol config description");

        IlmSigningProtocolConfigurationDto dto = ilmService.createIlmSigningProtocolConfiguration(request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertEquals("new-ilm-signing-protocol-config", dto.getName());
        Assertions.assertEquals("New ILM Signing Protocol config description", dto.getDescription());

        // Assert entity reloaded from the database
        Optional<IlmSigningProtocolConfiguration> fromDb = ilmRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        IlmSigningProtocolConfiguration entity = fromDb.get();
        Assertions.assertEquals("new-ilm-signing-protocol-config", entity.getName());
        Assertions.assertEquals("New ILM Signing Protocol config description", entity.getDescription());
        Assertions.assertNull(entity.getDefaultSigningProfileUuid());
    }

    @Test
    void testCreateIlmSigningProtocolConfiguration_withDefaultSigningProfile_assertDtoAndDbEntity()
            throws AttributeException, NotFoundException {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("ilm-with-default-profile");
        request.setDescription("ILM with default signing profile");
        request.setDefaultSigningProfileUuid(savedSigningProfile.getUuid());

        IlmSigningProtocolConfigurationDto dto = ilmService.createIlmSigningProtocolConfiguration(request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("ilm-with-default-profile", dto.getName());
        Assertions.assertEquals("ILM with default signing profile", dto.getDescription());

        // Assert entity reloaded from the database
        Optional<IlmSigningProtocolConfiguration> fromDb = ilmRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        IlmSigningProtocolConfiguration entity = fromDb.get();
        Assertions.assertEquals("ilm-with-default-profile", entity.getName());
        Assertions.assertEquals(savedSigningProfile.getUuid(), entity.getDefaultSigningProfileUuid());
    }

    @Test
    void testCreateIlmSigningProtocolConfiguration_defaultSigningProfileNotFound_throwsNotFoundException() {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("ilm-nonexistent-profile");
        request.setDefaultSigningProfileUuid(UUID.fromString("00000000-0000-0000-0000-000000000099"));

        Assertions.assertThrows(NotFoundException.class,
                () -> ilmService.createIlmSigningProtocolConfiguration(request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testUpdateIlmSigningProtocolConfiguration_assertDtoAndDbEntity()
            throws NotFoundException, AttributeException {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("updated-ilm-config");
        request.setDescription("Updated description");

        IlmSigningProtocolConfigurationDto dto = ilmService.updateIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid(), request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(savedConfig.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals("updated-ilm-config", dto.getName());
        Assertions.assertEquals("Updated description", dto.getDescription());

        // Assert entity reloaded from the database
        Optional<IlmSigningProtocolConfiguration> fromDb = ilmRepository.findById(savedConfig.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        IlmSigningProtocolConfiguration entity = fromDb.get();
        Assertions.assertEquals("updated-ilm-config", entity.getName());
        Assertions.assertEquals("Updated description", entity.getDescription());
        Assertions.assertNull(entity.getDefaultSigningProfileUuid());
    }

    @Test
    void testUpdateIlmSigningProtocolConfiguration_withDefaultSigningProfile_assertDtoAndDbEntity() throws NotFoundException, AttributeException {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("updated-ilm-with-profile");
        request.setDescription("Updated with default profile");
        request.setDefaultSigningProfileUuid(savedSigningProfile.getUuid());

        IlmSigningProtocolConfigurationDto dto = ilmService.updateIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid(), request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("updated-ilm-with-profile", dto.getName());

        // Assert entity reloaded from the database
        Optional<IlmSigningProtocolConfiguration> fromDb =
                ilmRepository.findById(savedConfig.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(savedSigningProfile.getUuid(), fromDb.get().getDefaultSigningProfileUuid());
    }

    @Test
    void testUpdateIlmSigningProtocolConfiguration_notFound_throwsNotFoundException() {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("does-not-matter");

        Assertions.assertThrows(NotFoundException.class,
                () -> ilmService.updateIlmSigningProtocolConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001"), request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testDeleteIlmSigningProtocolConfiguration_removesEntityFromDatabase() throws NotFoundException {
        ilmService.deleteIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid());

        Assertions.assertFalse(ilmRepository.findById(savedConfig.getUuid()).isPresent());
        Assertions.assertThrows(NotFoundException.class,
                () -> ilmService.getIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid()));
    }

    @Test
    void testDeleteIlmSigningProtocolConfiguration_notFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> ilmService.deleteIlmSigningProtocolConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDeleteIlmSigningProtocolConfiguration_withDependentSigningProfile_throwsValidationException() {
        // Link the signing profile to this ILM config
        savedSigningProfile.setIlmSigningProtocolConfiguration(savedConfig);
        signingProfileRepository.save(savedSigningProfile);

        Assertions.assertThrows(ValidationException.class,
                () -> ilmService.deleteIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid()));

        // Entity must still exist in the database after failed delete
        Assertions.assertTrue(ilmRepository.findById(savedConfig.getUuid()).isPresent());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bulk delete
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testBulkDeleteIlmSigningProtocolConfigurations_removesAllEntities() {
        // Create a second config
        IlmSigningProtocolConfiguration second = new IlmSigningProtocolConfiguration();
        second.setName("second-ilm-config");
        second = ilmRepository.save(second);

        List<BulkActionMessageDto> messages = ilmService.bulkDeleteIlmSigningProtocolConfigurations(
                List.of(savedConfig.getSecuredUuid(), second.getSecuredUuid()));

        Assertions.assertNotNull(messages);
        Assertions.assertTrue(messages.isEmpty(), "Expected no errors but got: " + messages);
        Assertions.assertFalse(ilmRepository.findById(savedConfig.getUuid()).isPresent());
        Assertions.assertFalse(ilmRepository.findById(second.getUuid()).isPresent());
    }

    @Test
    void testBulkDeleteIlmSigningProtocolConfigurations_partialFailure_returnsErrorMessages() {
        // Link the signing profile to savedConfig to prevent its deletion
        savedSigningProfile.setIlmSigningProtocolConfiguration(savedConfig);
        signingProfileRepository.save(savedSigningProfile);

        // Create a second config with no dependencies
        IlmSigningProtocolConfiguration second = new IlmSigningProtocolConfiguration();
        second.setName("second-ilm-config");
        second = ilmRepository.save(second);

        List<BulkActionMessageDto> messages = ilmService.bulkDeleteIlmSigningProtocolConfigurations(
                List.of(savedConfig.getSecuredUuid(), second.getSecuredUuid()));

        Assertions.assertNotNull(messages);
        Assertions.assertEquals(1, messages.size(), "Expected exactly one error for the config with a dependent profile");
        Assertions.assertEquals(savedConfig.getUuid().toString(), messages.getFirst().getUuid());

        // The first config (with dependency) should still exist; the second should be gone
        Assertions.assertTrue(ilmRepository.findById(savedConfig.getUuid()).isPresent());
        Assertions.assertFalse(ilmRepository.findById(second.getUuid()).isPresent());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Enable / disable
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testEnableIlmSigningProtocolConfiguration_setsEnabledTrue() throws NotFoundException {
        Assertions.assertFalse(savedConfig.getEnabled(), "Config should start disabled");

        ilmService.enableIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid());

        IlmSigningProtocolConfiguration fromDb = ilmRepository.findById(savedConfig.getUuid()).orElseThrow();
        Assertions.assertTrue(fromDb.getEnabled());
    }

    @Test
    void testDisableIlmSigningProtocolConfiguration_setsEnabledFalse() throws NotFoundException {
        // Pre-enable the entity directly in the DB
        savedConfig.setEnabled(true);
        ilmRepository.save(savedConfig);

        ilmService.disableIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid());

        IlmSigningProtocolConfiguration fromDb = ilmRepository.findById(savedConfig.getUuid()).orElseThrow();
        Assertions.assertFalse(fromDb.getEnabled());
    }

    @Test
    void testEnableIlmSigningProtocolConfiguration_notFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> ilmService.enableIlmSigningProtocolConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDisableIlmSigningProtocolConfiguration_notFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> ilmService.disableIlmSigningProtocolConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testBulkEnableIlmSigningProtocolConfigurations_enablesAll() {
        IlmSigningProtocolConfiguration second = new IlmSigningProtocolConfiguration();
        second.setName("second-ilm-config");
        second = ilmRepository.save(second);

        List<BulkActionMessageDto> messages = ilmService.bulkEnableIlmSigningProtocolConfigurations(
                List.of(savedConfig.getSecuredUuid(), second.getSecuredUuid()));

        Assertions.assertNotNull(messages);
        Assertions.assertTrue(messages.isEmpty(), "Expected no errors but got: " + messages);
        Assertions.assertTrue(ilmRepository.findById(savedConfig.getUuid()).orElseThrow().getEnabled());
        Assertions.assertTrue(ilmRepository.findById(second.getUuid()).orElseThrow().getEnabled());
    }

    @Test
    void testBulkDisableIlmSigningProtocolConfigurations_disablesAll() {
        // Pre-enable both entities
        savedConfig.setEnabled(true);
        ilmRepository.save(savedConfig);

        IlmSigningProtocolConfiguration second = new IlmSigningProtocolConfiguration();
        second.setName("second-ilm-config");
        second.setEnabled(true);
        second = ilmRepository.save(second);

        List<BulkActionMessageDto> messages = ilmService.bulkDisableIlmSigningProtocolConfigurations(
                List.of(savedConfig.getSecuredUuid(), second.getSecuredUuid()));

        Assertions.assertNotNull(messages);
        Assertions.assertTrue(messages.isEmpty(), "Expected no errors but got: " + messages);
        Assertions.assertFalse(ilmRepository.findById(savedConfig.getUuid()).orElseThrow().getEnabled());
        Assertions.assertFalse(ilmRepository.findById(second.getUuid()).orElseThrow().getEnabled());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Custom attributes
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateIlmSigningProtocolConfiguration_withCustomAttributes_returnedInDto() throws AttributeException, NotFoundException {
        RequestAttributeV3 customAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                List.of(new StringAttributeContentV3("ilm-value-on-create")));

        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("ilm-with-custom-attr");
        request.setCustomAttributes(List.of(customAttr));

        IlmSigningProtocolConfigurationDto dto = ilmService.createIlmSigningProtocolConfiguration(request);

        Assertions.assertNotNull(dto.getCustomAttributes());
        Assertions.assertFalse(dto.getCustomAttributes().isEmpty(),
                "Custom attributes should be returned in the create DTO");
        Assertions.assertEquals("ilm-value-on-create",
                ((ResponseAttributeV3) dto.getCustomAttributes().getFirst()).getContent().getFirst().getData());
    }

    @Test
    void testUpdateIlmSigningProtocolConfiguration_withCustomAttributes_returnedInDto() throws AttributeException, NotFoundException {
        RequestAttributeV3 createAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                List.of(new StringAttributeContentV3("initial-value")));
        IlmSigningProtocolConfigurationRequestDto createRequest = new IlmSigningProtocolConfigurationRequestDto();
        createRequest.setName("ilm-update-custom-attr");
        createRequest.setCustomAttributes(List.of(createAttr));
        IlmSigningProtocolConfigurationDto created = ilmService.createIlmSigningProtocolConfiguration(createRequest);

        RequestAttributeV3 updateAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                List.of(new StringAttributeContentV3("updated-value")));
        IlmSigningProtocolConfigurationRequestDto updateRequest = new IlmSigningProtocolConfigurationRequestDto();
        updateRequest.setName("ilm-update-custom-attr");
        updateRequest.setCustomAttributes(List.of(updateAttr));
        IlmSigningProtocolConfigurationDto updated = ilmService.updateIlmSigningProtocolConfiguration(
                SecuredUUID.fromString(created.getUuid()), updateRequest);

        Assertions.assertNotNull(updated.getCustomAttributes());
        Assertions.assertFalse(updated.getCustomAttributes().isEmpty());
        Assertions.assertEquals("updated-value",
                ((ResponseAttributeV3) updated.getCustomAttributes().getFirst()).getContent().getFirst().getData());
    }
}
