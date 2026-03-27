package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TspConfiguration;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.TspConfigurationRepository;
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

class TspConfigurationServiceImplTest extends BaseSpringBootTest {

    @Autowired
    private TspConfigurationService tspService;

    @Autowired
    private TspConfigurationRepository tspRepository;

    @Autowired
    private SigningProfileRepository signingProfileRepository;

    private TspConfiguration savedConfig;
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

        // Create a TSP configuration entity directly for tests that need pre-existing data
        savedConfig = new TspConfiguration();
        savedConfig.setName("existing-tsp-config");
        savedConfig.setDescription("Existing TSP config description");
        savedConfig = tspRepository.save(savedConfig);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // List
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testListTspConfigurations_returnsExistingEntries() {
        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<TspConfigurationListDto> response = tspService.listTspConfigurations(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getItems());
        Assertions.assertEquals(1, response.getTotalItems());
        Assertions.assertEquals(savedConfig.getUuid().toString(), response.getItems().getFirst().getUuid());
        Assertions.assertEquals(savedConfig.getName(), response.getItems().getFirst().getName());
    }

    @Test
    void testListTspConfigurations_emptyWhenNoneExist() {
        tspRepository.delete(savedConfig);

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<TspConfigurationListDto> response = tspService.listTspConfigurations(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertEquals(0, response.getTotalItems());
        Assertions.assertTrue(response.getItems().isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Get
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testGetTspConfiguration_returnsCorrectDto() throws NotFoundException {
        TspConfigurationDto dto = tspService.getTspConfiguration(savedConfig.getSecuredUuid());

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(savedConfig.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(savedConfig.getName(), dto.getName());
        Assertions.assertEquals(savedConfig.getDescription(), dto.getDescription());
    }

    @Test
    void testGetTspConfiguration_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> tspService.getTspConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateTspConfiguration_assertDtoAndDbEntity() throws AttributeException, NotFoundException {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("new-tsp-config");
        request.setDescription("New TSP config description");

        TspConfigurationDto dto = tspService.createTspConfiguration(request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertEquals("new-tsp-config", dto.getName());
        Assertions.assertEquals("New TSP config description", dto.getDescription());

        // Assert entity reloaded from the database
        Optional<TspConfiguration> fromDb = tspRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        TspConfiguration entity = fromDb.get();
        Assertions.assertEquals("new-tsp-config", entity.getName());
        Assertions.assertEquals("New TSP config description", entity.getDescription());
        Assertions.assertNull(entity.getDefaultSigningProfileUuid());
    }

    @Test
    void testCreateTspConfiguration_withDefaultSigningProfile_assertDtoAndDbEntity() throws AttributeException, NotFoundException {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("tsp-with-default-profile");
        request.setDescription("TSP with default signing profile");
        request.setDefaultSigningProfileUuid(savedSigningProfile.getUuid());

        TspConfigurationDto dto = tspService.createTspConfiguration(request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("tsp-with-default-profile", dto.getName());
        Assertions.assertEquals("TSP with default signing profile", dto.getDescription());

        // Assert entity reloaded from the database
        Optional<TspConfiguration> fromDb = tspRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        TspConfiguration entity = fromDb.get();
        Assertions.assertEquals("tsp-with-default-profile", entity.getName());
        Assertions.assertEquals(savedSigningProfile.getUuid(), entity.getDefaultSigningProfileUuid());
    }

    @Test
    void testCreateTspConfiguration_invalidName_throwsValidationException() {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("invalid name with spaces!");

        Assertions.assertThrows(ValidationException.class,
                () -> tspService.createTspConfiguration(request));
    }

    @Test
    void testCreateTspConfiguration_defaultSigningProfileNotFound_throwsNotFoundException() {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("tsp-nonexistent-profile");
        request.setDefaultSigningProfileUuid(UUID.fromString("00000000-0000-0000-0000-000000000099"));

        Assertions.assertThrows(NotFoundException.class,
                () -> tspService.createTspConfiguration(request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testUpdateTspConfiguration_assertDtoAndDbEntity() throws NotFoundException, AttributeException {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("updated-tsp-config");
        request.setDescription("Updated description");

        TspConfigurationDto dto = tspService.updateTspConfiguration(savedConfig.getSecuredUuid(), request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(savedConfig.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals("updated-tsp-config", dto.getName());
        Assertions.assertEquals("Updated description", dto.getDescription());

        // Assert entity reloaded from the database
        Optional<TspConfiguration> fromDb = tspRepository.findById(savedConfig.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        TspConfiguration entity = fromDb.get();
        Assertions.assertEquals("updated-tsp-config", entity.getName());
        Assertions.assertEquals("Updated description", entity.getDescription());
        Assertions.assertNull(entity.getDefaultSigningProfileUuid());
    }

    @Test
    void testUpdateTspConfiguration_withDefaultSigningProfile_assertDtoAndDbEntity() throws NotFoundException, AttributeException {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("updated-tsp-with-profile");
        request.setDescription("Updated with default profile");
        request.setDefaultSigningProfileUuid(savedSigningProfile.getUuid());

        TspConfigurationDto dto = tspService.updateTspConfiguration(savedConfig.getSecuredUuid(), request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals("updated-tsp-with-profile", dto.getName());

        // Assert entity reloaded from the database
        Optional<TspConfiguration> fromDb = tspRepository.findById(savedConfig.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(savedSigningProfile.getUuid(), fromDb.get().getDefaultSigningProfileUuid());
    }

    @Test
    void testUpdateTspConfiguration_notFound_throwsNotFoundException() {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("does-not-matter");

        Assertions.assertThrows(NotFoundException.class,
                () -> tspService.updateTspConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001"), request));
    }

    @Test
    void testUpdateTspConfiguration_invalidName_throwsValidationException() {
        TspConfigurationRequestDto request = new TspConfigurationRequestDto();
        request.setName("invalid name!");

        Assertions.assertThrows(ValidationException.class,
                () -> tspService.updateTspConfiguration(savedConfig.getSecuredUuid(), request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testDeleteTspConfiguration_removesEntityFromDatabase() throws NotFoundException {
        tspService.deleteTspConfiguration(savedConfig.getSecuredUuid());

        Assertions.assertFalse(tspRepository.findById(savedConfig.getUuid()).isPresent());
        Assertions.assertThrows(NotFoundException.class,
                () -> tspService.getTspConfiguration(savedConfig.getSecuredUuid()));
    }

    @Test
    void testDeleteTspConfiguration_notFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> tspService.deleteTspConfiguration(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDeleteTspConfiguration_withDependentSigningProfile_throwsValidationException() {
        // Link the signing profile to this TSP config
        savedSigningProfile.setTspConfiguration(savedConfig);
        signingProfileRepository.save(savedSigningProfile);

        Assertions.assertThrows(ValidationException.class,
                () -> tspService.deleteTspConfiguration(savedConfig.getSecuredUuid()));

        // Entity must still exist in the database after failed delete
        Assertions.assertTrue(tspRepository.findById(savedConfig.getUuid()).isPresent());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bulk delete
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testBulkDeleteTspConfigurations_removesAllEntities() {
        // Create a second config
        TspConfiguration second = new TspConfiguration();
        second.setName("second-tsp-config");
        second = tspRepository.save(second);

        List<BulkActionMessageDto> messages = tspService.bulkDeleteTspConfigurations(
                List.of(savedConfig.getSecuredUuid(), second.getSecuredUuid()));

        Assertions.assertNotNull(messages);
        Assertions.assertTrue(messages.isEmpty(), "Expected no errors but got: " + messages);
        Assertions.assertFalse(tspRepository.findById(savedConfig.getUuid()).isPresent());
        Assertions.assertFalse(tspRepository.findById(second.getUuid()).isPresent());
    }

    @Test
    void testBulkDeleteTspConfigurations_partialFailure_returnsErrorMessages() {
        // Link the signing profile to savedConfig to prevent its deletion
        savedSigningProfile.setTspConfiguration(savedConfig);
        signingProfileRepository.save(savedSigningProfile);

        // Create a second config with no dependencies
        TspConfiguration second = new TspConfiguration();
        second.setName("second-tsp-config");
        second = tspRepository.save(second);

        List<BulkActionMessageDto> messages = tspService.bulkDeleteTspConfigurations(
                List.of(savedConfig.getSecuredUuid(), second.getSecuredUuid()));

        Assertions.assertNotNull(messages);
        Assertions.assertEquals(1, messages.size(), "Expected exactly one error for the config with a dependent profile");
        Assertions.assertEquals(savedConfig.getUuid().toString(), messages.getFirst().getUuid());

        // The first config (with dependency) should still exist; the second should be gone
        Assertions.assertTrue(tspRepository.findById(savedConfig.getUuid()).isPresent());
        Assertions.assertFalse(tspRepository.findById(second.getUuid()).isPresent());
    }
}
