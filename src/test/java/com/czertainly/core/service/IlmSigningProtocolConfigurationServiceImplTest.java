package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.core.dao.entity.signing.IlmSigningProtocolConfiguration;
import com.czertainly.core.dao.entity.signing.SigningProfile;
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

    @Autowired
    private IlmSigningProtocolConfigurationService ilmService;

    @Autowired
    private IlmSigningProtocolConfigurationRepository ilmRepository;

    @Autowired
    private SigningProfileRepository signingProfileRepository;

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
    void testCreateIlmSigningProtocolConfiguration_invalidName_throwsValidationException() {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("invalid name with spaces!");

        Assertions.assertThrows(ValidationException.class,
                () -> ilmService.createIlmSigningProtocolConfiguration(request));
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

    @Test
    void testUpdateIlmSigningProtocolConfiguration_invalidName_throwsValidationException() {
        IlmSigningProtocolConfigurationRequestDto request = new IlmSigningProtocolConfigurationRequestDto();
        request.setName("invalid name!");

        Assertions.assertThrows(ValidationException.class,
                () -> ilmService.updateIlmSigningProtocolConfiguration(savedConfig.getSecuredUuid(), request));
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
}
