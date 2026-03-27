package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.DelegatedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.api.model.client.signing.profile.scheme.OneTimeKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.CodeBinarySigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.DocumentSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.RawSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.core.dao.entity.signing.DigitalSignature;
import com.czertainly.core.dao.entity.signing.IlmSigningProtocolConfiguration;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TspConfiguration;
import com.czertainly.core.dao.repository.signing.DigitalSignatureRepository;
import com.czertainly.core.dao.repository.signing.IlmSigningProtocolConfigurationRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileVersionRepository;
import com.czertainly.core.dao.repository.signing.TspConfigurationRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SigningProfileServiceImplTest extends BaseSpringBootTest {

    @Autowired
    private SigningProfileService signingProfileService;

    @Autowired
    private SigningProfileRepository signingProfileRepository;

    @Autowired
    private SigningProfileVersionRepository signingProfileVersionRepository;

    @Autowired
    private DigitalSignatureRepository digitalSignatureRepository;

    @Autowired
    private IlmSigningProtocolConfigurationRepository ilmRepository;

    @Autowired
    private TspConfigurationRepository tspRepository;

    /**
     * A signing profile saved directly via repository, used as pre-existing data in tests.
     */
    private SigningProfile savedProfile;

    @BeforeEach
    void setUp() {
        savedProfile = new SigningProfile();
        savedProfile.setName("existing-signing-profile");
        savedProfile.setDescription("Existing profile description");
        savedProfile.setEnabled(false);
        savedProfile.setSigningScheme(SigningScheme.DELEGATED);
        savedProfile.setWorkflowType(SigningWorkflowType.RAW_SIGNING);
        savedProfile.setLatestVersion(1);
        savedProfile = signingProfileRepository.save(savedProfile);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a minimal valid SigningProfileRequestDto using a DELEGATED scheme and RAW_SIGNING workflow
     * (no foreign-key dependencies on connectors, token profiles, or keys).
     */
    private SigningProfileRequestDto buildDelegatedRawRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setEnabled(false);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and RAW_SIGNING workflow.
     * No FK UUIDs are set so the request is safe to use against any test database.
     */
    private SigningProfileRequestDto buildManagedStaticKeyRawRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setEnabled(false);
        request.setSigningScheme(new StaticKeyManagedSigningRequestDto());
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a MANAGED/ONE_TIME_KEY scheme and RAW_SIGNING workflow.
     * No FK UUIDs are set, so the request is safe to use against any test database.
     */
    private SigningProfileRequestDto buildManagedOneTimeKeyRawRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setEnabled(false);
        request.setSigningScheme(new OneTimeKeyManagedSigningRequestDto());
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a DELEGATED scheme and DOCUMENT_SIGNING workflow.
     */
    private SigningProfileRequestDto buildDelegatedDocumentRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setEnabled(false);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new DocumentSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a DELEGATED scheme and CODE_BINARY_SIGNING workflow.
     */
    private SigningProfileRequestDto buildDelegatedCodeBinaryRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setEnabled(false);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new CodeBinarySigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a DELEGATED scheme and TIMESTAMPING workflow.
     */
    private SigningProfileRequestDto buildDelegatedTimestampingRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setEnabled(false);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new TimestampingWorkflowRequestDto());
        return request;
    }

    /**
     * Persists a DigitalSignature that references the given signing profile and version,
     * simulating a signature that was produced using that profile version.
     */
    private void createDigitalSignatureFor(SigningProfile profile, int version) {
        DigitalSignature sig = new DigitalSignature();
        sig.setSigningProfile(profile);
        sig.setSigningProfileVersion(version);
        sig.setSigningTime(OffsetDateTime.now());
        digitalSignatureRepository.save(sig);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // List
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testListSigningProfiles_returnsExistingEntries() {
        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<SigningProfileListDto> response = signingProfileService.listSigningProfiles(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertEquals(1, response.getTotalItems());
        SigningProfileListDto listed = response.getItems().getFirst();
        Assertions.assertEquals(savedProfile.getUuid().toString(), listed.getUuid());
        Assertions.assertEquals(savedProfile.getName(), listed.getName());
        Assertions.assertEquals(savedProfile.getDescription(), listed.getDescription());
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, listed.getSigningWorkflowType());
        Assertions.assertFalse(listed.getEnabled());
    }

    @Test
    void testListSigningProfiles_emptyWhenNoneExist() {
        signingProfileRepository.delete(savedProfile);

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<SigningProfileListDto> response = signingProfileService.listSigningProfiles(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertEquals(0, response.getTotalItems());
        Assertions.assertTrue(response.getItems().isEmpty());
    }

    @Test
    void testListSigningProfiles_multipleProfilesWithDifferentWorkflowTypes() throws AttributeException, NotFoundException {
        // Create additional profiles with different workflow types
        signingProfileService.createSigningProfile(buildDelegatedDocumentRequest("document-profile"));
        signingProfileService.createSigningProfile(buildDelegatedCodeBinaryRequest("code-binary-profile"));
        signingProfileService.createSigningProfile(buildDelegatedTimestampingRequest("timestamping-profile"));

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<SigningProfileListDto> response = signingProfileService.listSigningProfiles(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertEquals(4, response.getTotalItems());

        List<SigningWorkflowType> returnedTypes = response.getItems().stream()
                .map(SigningProfileListDto::getSigningWorkflowType)
                .toList();
        Assertions.assertTrue(returnedTypes.contains(SigningWorkflowType.RAW_SIGNING));
        Assertions.assertTrue(returnedTypes.contains(SigningWorkflowType.DOCUMENT_SIGNING));
        Assertions.assertTrue(returnedTypes.contains(SigningWorkflowType.CODE_BINARY_SIGNING));
        Assertions.assertTrue(returnedTypes.contains(SigningWorkflowType.TIMESTAMPING));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Get
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testGetSigningProfile_returnsCorrectDto() throws NotFoundException {
        SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(savedProfile.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals(savedProfile.getName(), dto.getName());
        Assertions.assertEquals(savedProfile.getDescription(), dto.getDescription());
        Assertions.assertFalse(dto.getEnabled());
        Assertions.assertEquals(1, dto.getVersion());
    }

    @Test
    void testGetSigningProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.getSigningProfile(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001"), null));
    }

    @Test
    void testGetSigningProfile_specificVersion_returnsSnapshotData() throws AttributeException, NotFoundException {
        // Create a profile via service — this creates the version 1 snapshot
        SigningProfileDto created = signingProfileService.createSigningProfile(buildDelegatedRawRequest("profile-for-version-get"));
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

        // Get with explicit version=1
        SigningProfileDto dto = signingProfileService.getSigningProfile(profileUuid, 1);

        Assertions.assertNotNull(dto);
        Assertions.assertEquals(1, dto.getVersion());
        Assertions.assertNotNull(dto.getSigningScheme());
        Assertions.assertEquals(SigningScheme.DELEGATED, dto.getSigningScheme().getSigningScheme());
        Assertions.assertNotNull(dto.getWorkflow());
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, dto.getWorkflow().getType());
    }

    @Test
    void testGetSigningProfile_nonExistentVersion_throwsNotFoundException() throws AttributeException, NotFoundException {
        SigningProfileDto created = signingProfileService.createSigningProfile(buildDelegatedRawRequest("profile-missing-version"));
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

        // Version 99 does not exist
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.getSigningProfile(profileUuid, 99));
    }

    @Test
    void testGetSigningProfile_afterVersionBump_oldVersionPreservesOriginalWorkflowType()
            throws AttributeException, NotFoundException {
        // Create with DELEGATED + RAW_SIGNING (version 1)
        SigningProfileDto created = signingProfileService.createSigningProfile(buildDelegatedRawRequest("profile-history"));
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
        SigningProfile entity = signingProfileRepository.findById(UUID.fromString(created.getUuid())).orElseThrow();

        // Add a digital signature so that the next update triggers a version bump
        createDigitalSignatureFor(entity, 1);

        // Update to DELEGATED + DOCUMENT_SIGNING → should bump to version 2
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedDocumentRequest("profile-history"));

        // Version 1 snapshot must still report RAW_SIGNING
        SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
        Assertions.assertEquals(1, v1.getVersion());
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, v1.getWorkflow().getType());

        // Latest (version 2) must report DOCUMENT_SIGNING
        SigningProfileDto latest = signingProfileService.getSigningProfile(profileUuid, null);
        Assertions.assertEquals(2, latest.getVersion());
        Assertions.assertEquals(SigningWorkflowType.DOCUMENT_SIGNING, latest.getWorkflow().getType());
    }

    @Test
    void testGetSigningProfile_noProtocolsLinked_enabledProtocolsIsEmpty() throws NotFoundException {
        // savedProfile has no ILM or TSP linked
        SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

        Assertions.assertNotNull(dto.getEnabledProtocols());
        Assertions.assertTrue(dto.getEnabledProtocols().isEmpty(),
                "No protocols should be enabled when none are linked");
    }

    @Test
    void testGetSigningProfile_withBothProtocolsLinked_enabledProtocolsContainsBoth() throws NotFoundException {
        IlmSigningProtocolConfiguration ilmConfig = new IlmSigningProtocolConfiguration();
        ilmConfig.setName("ilm-for-dto-test");
        ilmConfig = ilmRepository.save(ilmConfig);

        TspConfiguration tspConfig = new TspConfiguration();
        tspConfig.setName("tsp-for-dto-test");
        tspConfig = tspRepository.save(tspConfig);

        savedProfile.setIlmSigningProtocolConfiguration(ilmConfig);
        savedProfile.setTspConfiguration(tspConfig);
        signingProfileRepository.save(savedProfile);

        SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

        Assertions.assertNotNull(dto.getEnabledProtocols());
        Assertions.assertTrue(dto.getEnabledProtocols().contains(SigningProtocol.ILM_SIGNING_PROTOCOL),
                "ILM_SIGNING_PROTOCOL should appear in enabledProtocols");
        Assertions.assertTrue(dto.getEnabledProtocols().contains(SigningProtocol.TSP),
                "TSP should appear in enabledProtocols");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateSigningProfile_delegatedRawSigning_assertDtoAndDbEntity() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedRawRequest("new-delegated-profile");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertNotNull(dto.getUuid());
        Assertions.assertEquals("new-delegated-profile", dto.getName());
        Assertions.assertEquals("Test description for new-delegated-profile", dto.getDescription());
        Assertions.assertFalse(dto.getEnabled());
        Assertions.assertEquals(1, dto.getVersion());
        Assertions.assertNotNull(dto.getSigningScheme());
        Assertions.assertNotNull(dto.getWorkflow());

        // Assert entity reloaded from the database
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals("new-delegated-profile", entity.getName());
        Assertions.assertEquals("Test description for new-delegated-profile", entity.getDescription());
        Assertions.assertFalse(entity.getEnabled());
        Assertions.assertEquals(SigningScheme.DELEGATED, entity.getSigningScheme());
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, entity.getWorkflowType());
        Assertions.assertEquals(1, entity.getLatestVersion());
        Assertions.assertNull(entity.getDelegatedSignerConnectorUuid());
    }

    @Test
    void testCreateSigningProfile_createsVersionSnapshot() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedRawRequest("profile-with-snapshot");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        UUID profileUuid = UUID.fromString(dto.getUuid());
        Optional<com.czertainly.core.dao.entity.signing.SigningProfileVersion> snapshot =
                signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuid, 1);

        Assertions.assertTrue(snapshot.isPresent(), "Version 1 snapshot should be created on profile creation");
        Assertions.assertEquals(1, snapshot.get().getVersion());
        Assertions.assertNotNull(snapshot.get().getSchemeSnapshot());
        Assertions.assertNotNull(snapshot.get().getWorkflowSnapshot());
    }

    @Test
    void testCreateSigningProfile_enabled_assertDtoAndDbEntity() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedRawRequest("enabled-profile");
        request.setEnabled(true);

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        // Assert returned DTO
        Assertions.assertTrue(dto.getEnabled());

        // Assert entity in database
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertTrue(fromDb.get().getEnabled());
    }

    @Test
    void testCreateSigningProfile_invalidName_throwsValidationException() {
        SigningProfileRequestDto request = buildDelegatedRawRequest("invalid name with spaces!");

        Assertions.assertThrows(ValidationException.class,
                () -> signingProfileService.createSigningProfile(request));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create – Signing scheme variations
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateSigningProfile_staticKeyManaged_assertSchemeAndEntityFields() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildManagedStaticKeyRawRequest("static-key-profile");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        // Assert scheme type in DTO
        Assertions.assertNotNull(dto.getSigningScheme());
        Assertions.assertEquals(SigningScheme.MANAGED, dto.getSigningScheme().getSigningScheme());

        // Assert entity fields
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
        Assertions.assertEquals(ManagedSigningType.STATIC_KEY, entity.getManagedSigningType());
        // No delegated connector when using managed scheme
        Assertions.assertNull(entity.getDelegatedSignerConnectorUuid());
        // No RA profile / CSR template for static key
        Assertions.assertNull(entity.getRaProfileUuid());
        Assertions.assertNull(entity.getCsrTemplateUuid());
    }

    @Test
    void testCreateSigningProfile_oneTimeKeyManaged_assertSchemeAndEntityFields() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildManagedOneTimeKeyRawRequest("one-time-key-profile");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        // Assert scheme type in DTO
        Assertions.assertNotNull(dto.getSigningScheme());
        Assertions.assertEquals(SigningScheme.MANAGED, dto.getSigningScheme().getSigningScheme());

        // Assert entity fields
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
        Assertions.assertEquals(ManagedSigningType.ONE_TIME_KEY, entity.getManagedSigningType());
        // No delegated connector when using managed scheme
        Assertions.assertNull(entity.getDelegatedSignerConnectorUuid());
        // Static key UUID is not set for one-time key type
        Assertions.assertNull(entity.getCryptographicKeyUuid());
    }

    @Test
    void testCreateSigningProfile_allSchemeTypesCreateVersionSnapshot() throws AttributeException, NotFoundException {
        SigningProfileDto staticKeyDto = signingProfileService.createSigningProfile(buildManagedStaticKeyRawRequest("snapshot-static"));
        SigningProfileDto oneTimeKeyDto = signingProfileService.createSigningProfile(buildManagedOneTimeKeyRawRequest("snapshot-onetime"));

        for (String uuidStr : List.of(staticKeyDto.getUuid(), oneTimeKeyDto.getUuid())) {
            UUID profileUuid = UUID.fromString(uuidStr);
            Optional<com.czertainly.core.dao.entity.signing.SigningProfileVersion> snapshot =
                    signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuid, 1);
            Assertions.assertTrue(snapshot.isPresent(),
                    "Version 1 snapshot should exist for profile " + uuidStr);
            Assertions.assertNotNull(snapshot.get().getSchemeSnapshot());
            Assertions.assertNotNull(snapshot.get().getWorkflowSnapshot());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create – Workflow type variations
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateSigningProfile_documentSigningWorkflow_assertWorkflowTypeAndEntity() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedDocumentRequest("document-signing-profile");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertNotNull(dto.getWorkflow());
        Assertions.assertEquals(SigningWorkflowType.DOCUMENT_SIGNING, dto.getWorkflow().getType());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(SigningWorkflowType.DOCUMENT_SIGNING, fromDb.get().getWorkflowType());
    }

    @Test
    void testCreateSigningProfile_codeBinarySigningWorkflow_assertWorkflowTypeAndEntity() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedCodeBinaryRequest("code-binary-signing-profile");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertNotNull(dto.getWorkflow());
        Assertions.assertEquals(SigningWorkflowType.CODE_BINARY_SIGNING, dto.getWorkflow().getType());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(SigningWorkflowType.CODE_BINARY_SIGNING, fromDb.get().getWorkflowType());
    }

    @Test
    void testCreateSigningProfile_timestampingWorkflow_assertWorkflowTypeAndEntity() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedTimestampingRequest("timestamping-profile");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertNotNull(dto.getWorkflow());
        Assertions.assertEquals(SigningWorkflowType.TIMESTAMPING, dto.getWorkflow().getType());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(SigningWorkflowType.TIMESTAMPING, fromDb.get().getWorkflowType());
    }

    @Test
    void testCreateSigningProfile_timestampingWorkflowWithPoliciesAndAlgorithms_assertEntityFields()
            throws AttributeException, NotFoundException {
        TimestampingWorkflowRequestDto timestampingWorkflow = new TimestampingWorkflowRequestDto();
        timestampingWorkflow.setDefaultPolicyId("1.2.3.4.5");
        timestampingWorkflow.setAllowedPolicyIds(List.of("1.2.3.4.5", "1.2.3.4.6"));
        timestampingWorkflow.setAllowedDigestAlgorithms(List.of(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384));
        timestampingWorkflow.setQualifiedTimestamp(false);

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("timestamping-with-policies");
        request.setDescription("Timestamping profile with policies");
        request.setEnabled(false);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(timestampingWorkflow);

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        // Assert workflow fields in DTO
        Assertions.assertNotNull(dto.getWorkflow());
        Assertions.assertEquals(SigningWorkflowType.TIMESTAMPING, dto.getWorkflow().getType());
        TimestampingWorkflowDto tsDto = (TimestampingWorkflowDto) dto.getWorkflow();
        Assertions.assertEquals("1.2.3.4.5", tsDto.getDefaultPolicyId());
        Assertions.assertEquals(List.of("1.2.3.4.5", "1.2.3.4.6"), tsDto.getAllowedPolicyIds());
        Assertions.assertTrue(tsDto.getAllowedDigestAlgorithms().contains(DigestAlgorithm.SHA_256));
        Assertions.assertTrue(tsDto.getAllowedDigestAlgorithms().contains(DigestAlgorithm.SHA_384));
        Assertions.assertFalse(tsDto.getQualifiedTimestamp());

        // Assert entity fields in database
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningWorkflowType.TIMESTAMPING, entity.getWorkflowType());
        Assertions.assertEquals("1.2.3.4.5", entity.getDefaultPolicyId());
        Assertions.assertEquals(List.of("1.2.3.4.5", "1.2.3.4.6"), entity.getAllowedPolicyIds());
        Assertions.assertEquals(
                List.of(DigestAlgorithm.SHA_256.getCode(), DigestAlgorithm.SHA_384.getCode()),
                entity.getAllowedDigestAlgorithms()
        );
        Assertions.assertFalse(entity.getQualifiedTimestamp());
    }

    @Test
    void testCreateSigningProfile_managedStaticKey_withDocumentSigningWorkflow_assertBothFields()
            throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("managed-document-profile");
        request.setDescription("Managed static-key profile with document signing workflow");
        request.setEnabled(true);
        request.setSigningScheme(new StaticKeyManagedSigningRequestDto());
        request.setWorkflow(new DocumentSigningWorkflowRequestDto());

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertEquals(SigningScheme.MANAGED, dto.getSigningScheme().getSigningScheme());
        Assertions.assertEquals(SigningWorkflowType.DOCUMENT_SIGNING, dto.getWorkflow().getType());
        Assertions.assertTrue(dto.getEnabled());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
        Assertions.assertEquals(ManagedSigningType.STATIC_KEY, entity.getManagedSigningType());
        Assertions.assertEquals(SigningWorkflowType.DOCUMENT_SIGNING, entity.getWorkflowType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testUpdateSigningProfile_assertDtoAndDbEntity() throws NotFoundException, AttributeException {
        SigningProfileRequestDto request = buildDelegatedRawRequest("updated-profile");
        request.setDescription("Updated description");
        request.setEnabled(true);

        SigningProfileDto dto = signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(savedProfile.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals("updated-profile", dto.getName());
        Assertions.assertEquals("Updated description", dto.getDescription());
        Assertions.assertTrue(dto.getEnabled());
        Assertions.assertEquals(1, dto.getVersion()); // no bump — no digital signatures exist

        // Assert entity reloaded from the database
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals("updated-profile", entity.getName());
        Assertions.assertEquals("Updated description", entity.getDescription());
        Assertions.assertTrue(entity.getEnabled());
        Assertions.assertEquals(1, entity.getLatestVersion());
        Assertions.assertEquals(SigningScheme.DELEGATED, entity.getSigningScheme());
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, entity.getWorkflowType());
    }

    @Test
    void testUpdateSigningProfile_noDigitalSignatures_doesNotBumpVersion() throws NotFoundException, AttributeException {
        Assertions.assertEquals(1, savedProfile.getLatestVersion());

        SigningProfileRequestDto request = buildDelegatedRawRequest("profile-no-bump");
        signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), request);

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(1, fromDb.get().getLatestVersion(),
                "Version should not be bumped when no digital signatures exist");
    }

    @Test
    void testUpdateSigningProfile_withDigitalSignaturesOnCurrentVersion_bumpsVersion() throws NotFoundException, AttributeException {
        // Create a digital signature linked to version 1 of the profile
        createDigitalSignatureFor(savedProfile, 1);

        SigningProfileRequestDto request = buildDelegatedRawRequest("profile-with-bump");
        SigningProfileDto dto = signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), request);

        // The version in the DTO should be bumped to 2
        Assertions.assertEquals(2, dto.getVersion());

        // The entity in the database should reflect version 2
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(2, fromDb.get().getLatestVersion(),
                "Version should be bumped when digital signatures exist for the current version");

        // A new snapshot for version 2 should have been created
        Optional<com.czertainly.core.dao.entity.signing.SigningProfileVersion> v2snapshot =
                signingProfileVersionRepository.findBySigningProfileUuidAndVersion(savedProfile.getUuid(), 2);
        Assertions.assertTrue(v2snapshot.isPresent(), "Version 2 snapshot should be created after bump");
    }

    @Test
    void testUpdateSigningProfile_notFound_throwsNotFoundException() {
        SigningProfileRequestDto request = buildDelegatedRawRequest("does-not-matter");

        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.updateSigningProfile(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001"), request));
    }

    @Test
    void testUpdateSigningProfile_invalidName_throwsValidationException() {
        SigningProfileRequestDto request = buildDelegatedRawRequest("invalid name!");

        Assertions.assertThrows(ValidationException.class,
                () -> signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), request));
    }

    @Test
    void testUpdateSigningProfile_changeSchemeFromDelegatedToStaticKeyManaged() throws NotFoundException, AttributeException {
        // savedProfile uses DELEGATED scheme
        Assertions.assertEquals(SigningScheme.DELEGATED, savedProfile.getSigningScheme());

        // Update to MANAGED/STATIC_KEY
        signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), buildManagedStaticKeyRawRequest("scheme-switched"));

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
        Assertions.assertEquals(ManagedSigningType.STATIC_KEY, entity.getManagedSigningType());
        // Previous delegated connector reference must have been cleared
        Assertions.assertNull(entity.getDelegatedSignerConnectorUuid());
    }

    @Test
    void testUpdateSigningProfile_changeSchemeFromStaticKeyManagedToDelegated() throws AttributeException, NotFoundException {
        // Create a MANAGED/STATIC_KEY profile first
        SigningProfileDto created = signingProfileService.createSigningProfile(buildManagedStaticKeyRawRequest("managed-to-delegated"));
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

        // Switch to DELEGATED
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedRawRequest("managed-to-delegated"));

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(created.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningScheme.DELEGATED, entity.getSigningScheme());
        // managedSigningType must have been cleared by applyScheme
        Assertions.assertNull(entity.getManagedSigningType());
        // token profile and key references must have been cleared
        Assertions.assertNull(entity.getTokenProfileUuid());
        Assertions.assertNull(entity.getCryptographicKeyUuid());
    }

    @Test
    void testUpdateSigningProfile_changeWorkflowFromRawToTimestamping() throws NotFoundException, AttributeException {
        // savedProfile uses RAW_SIGNING workflow
        TimestampingWorkflowRequestDto timestampingWorkflow = new TimestampingWorkflowRequestDto();
        timestampingWorkflow.setDefaultPolicyId("1.2.3.4.5");
        timestampingWorkflow.setAllowedPolicyIds(List.of("1.2.3.4.5"));
        timestampingWorkflow.setQualifiedTimestamp(false);

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("workflow-changed");
        request.setDescription("Changed to timestamping");
        request.setEnabled(false);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(timestampingWorkflow);

        SigningProfileDto dto = signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), request);

        Assertions.assertEquals(SigningWorkflowType.TIMESTAMPING, dto.getWorkflow().getType());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningWorkflowType.TIMESTAMPING, entity.getWorkflowType());
        Assertions.assertEquals("1.2.3.4.5", entity.getDefaultPolicyId());
        Assertions.assertFalse(entity.getQualifiedTimestamp());
    }

    @Test
    void testUpdateSigningProfile_noVersionBump_overwritesExistingSnapshot() throws AttributeException, NotFoundException {
        // Create via service to get a proper v1 snapshot
        SigningProfileDto created = signingProfileService.createSigningProfile(buildDelegatedRawRequest("overwrite-snapshot-profile"));
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
        UUID profileUuidRaw = UUID.fromString(created.getUuid());

        // Update without digital signatures (no bump — snapshot is overwritten in place)
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedDocumentRequest("overwrite-snapshot-profile"));

        // Still only one snapshot (version 1) — overwritten
        Optional<com.czertainly.core.dao.entity.signing.SigningProfileVersion> v1Snapshot =
                signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 1);
        Assertions.assertTrue(v1Snapshot.isPresent(), "Version 1 snapshot should still exist after in-place overwrite");
        Assertions.assertFalse(
                signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 2).isPresent(),
                "No version 2 snapshot should exist when version was not bumped"
        );

        // Fetching version 1 should now return the updated workflow type
        SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
        Assertions.assertEquals(SigningWorkflowType.DOCUMENT_SIGNING, v1.getWorkflow().getType(),
                "Overwritten v1 snapshot should reflect the new workflow type");
    }

    @Test
    void testUpdateSigningProfile_multipleBumps_versionsAccumulate() throws AttributeException, NotFoundException {
        // Create a profile (version 1)
        SigningProfileDto created = signingProfileService.createSigningProfile(buildDelegatedRawRequest("multi-bump-profile"));
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
        UUID profileUuidRaw = UUID.fromString(created.getUuid());
        SigningProfile entity = signingProfileRepository.findById(profileUuidRaw).orElseThrow();

        // Trigger first bump: add sig for v1, then update → v2
        createDigitalSignatureFor(entity, 1);
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedDocumentRequest("multi-bump-profile"));

        // Trigger second bump: add sig for v2, then update → v3
        entity = signingProfileRepository.findById(profileUuidRaw).orElseThrow();
        createDigitalSignatureFor(entity, 2);
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedCodeBinaryRequest("multi-bump-profile"));

        // Verify three snapshot versions exist
        Assertions.assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 1).isPresent());
        Assertions.assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 2).isPresent());
        Assertions.assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 3).isPresent());

        // Verify latest is version 3 with CODE_BINARY_SIGNING
        SigningProfileDto latest = signingProfileService.getSigningProfile(profileUuid, null);
        Assertions.assertEquals(3, latest.getVersion());
        Assertions.assertEquals(SigningWorkflowType.CODE_BINARY_SIGNING, latest.getWorkflow().getType());

        // Verify version 1 still has RAW_SIGNING
        SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, v1.getWorkflow().getType());

        // Verify version 2 has DOCUMENT_SIGNING
        SigningProfileDto v2 = signingProfileService.getSigningProfile(profileUuid, 2);
        Assertions.assertEquals(SigningWorkflowType.DOCUMENT_SIGNING, v2.getWorkflow().getType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testDeleteSigningProfile_removesEntityFromDatabase() throws NotFoundException {
        signingProfileService.deleteSigningProfile(savedProfile.getSecuredUuid());

        Assertions.assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null));
    }

    @Test
    void testDeleteSigningProfile_notFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.deleteSigningProfile(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDeleteSigningProfile_withDigitalSignatures_clearsReferencesAndDeletes() throws NotFoundException {
        createDigitalSignatureFor(savedProfile, 1);

        signingProfileService.deleteSigningProfile(savedProfile.getSecuredUuid());

        // Profile should be removed from the database
        Assertions.assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        // Digital signatures should have their signing profile UUID cleared (not pointing to the deleted profile)
        Assertions.assertFalse(
                digitalSignatureRepository.existsBySigningProfileUuidAndSigningProfileVersion(savedProfile.getUuid(), 1),
                "Digital signature should no longer reference the deleted profile UUID");
    }

    @Test
    void testDeleteSigningProfile_usedAsDefaultInIlmConfig_clearsReferenceAndDeletes() throws NotFoundException {
        IlmSigningProtocolConfiguration ilmConfig = new IlmSigningProtocolConfiguration();
        ilmConfig.setName("blocking-ilm-config");
        ilmConfig.setDefaultSigningProfile(savedProfile);
        ilmConfig = ilmRepository.save(ilmConfig);
        final UUID ilmConfigUuid = ilmConfig.getUuid();

        signingProfileService.deleteSigningProfile(savedProfile.getSecuredUuid());

        // Profile should be removed from the database
        Assertions.assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        // The ILM config's default signing profile reference should be cleared
        IlmSigningProtocolConfiguration reloadedIlm = ilmRepository.findById(ilmConfigUuid).orElseThrow();
        Assertions.assertNull(reloadedIlm.getDefaultSigningProfileUuid(),
                "ILM config's default signing profile UUID should be cleared after profile deletion");
    }

    @Test
    void testDeleteSigningProfile_usedAsDefaultInTspConfig_clearsReferenceAndDeletes() throws NotFoundException {
        TspConfiguration tspConfig = new TspConfiguration();
        tspConfig.setName("blocking-tsp-config");
        tspConfig.setDefaultSigningProfile(savedProfile);
        tspConfig = tspRepository.save(tspConfig);
        final UUID tspConfigUuid = tspConfig.getUuid();

        signingProfileService.deleteSigningProfile(savedProfile.getSecuredUuid());

        // Profile should be removed from the database
        Assertions.assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        // The TSP config's default signing profile reference should be cleared
        TspConfiguration reloadedTsp = tspRepository.findById(tspConfigUuid).orElseThrow();
        Assertions.assertNull(reloadedTsp.getDefaultSigningProfileUuid(),
                "TSP config's default signing profile UUID should be cleared after profile deletion");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bulk delete
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testBulkDeleteSigningProfiles_removesAllEntities() throws AttributeException, NotFoundException {
        SigningProfileDto second = signingProfileService.createSigningProfile(buildDelegatedRawRequest("second-profile"));

        List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(
                List.of(savedProfile.getSecuredUuid(),
                        SecuredUUID.fromString(second.getUuid())));

        Assertions.assertNotNull(messages);
        Assertions.assertTrue(messages.isEmpty(), "Expected no errors but got: " + messages);
        Assertions.assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        Assertions.assertFalse(signingProfileRepository.findById(UUID.fromString(second.getUuid())).isPresent());
    }

    @Test
    void testBulkDeleteSigningProfiles_withDigitalSignatures_clearsReferencesAndDeletesAll()
            throws AttributeException, NotFoundException {
        // Attach a digital signature to the savedProfile — deletion should still succeed
        createDigitalSignatureFor(savedProfile, 1);

        // Create a second profile with no dependencies
        SigningProfileDto second = signingProfileService.createSigningProfile(buildDelegatedRawRequest("second-profile-no-deps"));

        List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(
                List.of(savedProfile.getSecuredUuid(),
                        SecuredUUID.fromString(second.getUuid())));

        // Both profiles should be deleted with no errors
        Assertions.assertNotNull(messages);
        Assertions.assertTrue(messages.isEmpty(), "Expected no errors: silently clears references before deleting");
        Assertions.assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        Assertions.assertFalse(signingProfileRepository.findById(UUID.fromString(second.getUuid())).isPresent());
        // Digital signatures should have their signing profile UUID cleared
        Assertions.assertFalse(
                digitalSignatureRepository.existsBySigningProfileUuidAndSigningProfileVersion(savedProfile.getUuid(), 1),
                "Digital signature should no longer reference the deleted profile UUID");
    }

    @Test
    void testBulkDeleteSigningProfiles_emptyList_returnsEmptyMessages() {
        List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(List.of());

        Assertions.assertNotNull(messages);
        Assertions.assertTrue(messages.isEmpty(), "Bulk delete of empty list should return no error messages");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Enable / Disable
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testEnableSigningProfile() throws NotFoundException {
        Assertions.assertFalse(savedProfile.getEnabled());

        signingProfileService.enableSigningProfile(savedProfile.getSecuredUuid());

        SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);
        Assertions.assertTrue(dto.getEnabled());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertTrue(fromDb.get().getEnabled());
    }

    @Test
    void testEnableSigningProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.enableSigningProfile(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testEnableSigningProfile_alreadyEnabled_remainsEnabled() throws NotFoundException {
        savedProfile.setEnabled(true);
        signingProfileRepository.save(savedProfile);

        // Enable again — should be idempotent
        signingProfileService.enableSigningProfile(savedProfile.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertTrue(fromDb.get().getEnabled(), "Profile should remain enabled after enabling an already-enabled profile");
    }

    @Test
    void testDisableSigningProfile() throws NotFoundException {
        savedProfile.setEnabled(true);
        signingProfileRepository.save(savedProfile);

        signingProfileService.disableSigningProfile(savedProfile.getSecuredUuid());

        SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);
        Assertions.assertFalse(dto.getEnabled());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertFalse(fromDb.get().getEnabled());
    }

    @Test
    void testDisableSigningProfile_notFound() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.disableSigningProfile(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDisableSigningProfile_alreadyDisabled_remainsDisabled() throws NotFoundException {
        // savedProfile is already disabled (enabled = false from setUp)
        signingProfileService.disableSigningProfile(savedProfile.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertFalse(fromDb.get().getEnabled(), "Profile should remain disabled after disabling an already-disabled profile");
    }

    @Test
    void testBulkEnableSigningProfiles() {
        signingProfileService.bulkEnableSigningProfiles(List.of(savedProfile.getSecuredUuid()));

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertTrue(fromDb.get().getEnabled());
    }

    @Test
    void testBulkDisableSigningProfiles() {
        savedProfile.setEnabled(true);
        signingProfileRepository.save(savedProfile);

        signingProfileService.bulkDisableSigningProfiles(List.of(savedProfile.getSecuredUuid()));

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertFalse(fromDb.get().getEnabled());
    }

    @Test
    void testBulkEnableSigningProfiles_multipleProfiles() throws AttributeException, NotFoundException {
        SigningProfileDto second = signingProfileService.createSigningProfile(buildDelegatedRawRequest("second-for-bulk-enable"));
        SigningProfileDto third = signingProfileService.createSigningProfile(buildDelegatedDocumentRequest("third-for-bulk-enable"));

        signingProfileService.bulkEnableSigningProfiles(List.of(
                savedProfile.getSecuredUuid(),
                SecuredUUID.fromString(second.getUuid()),
                SecuredUUID.fromString(third.getUuid())
        ));

        Assertions.assertTrue(signingProfileRepository.findById(savedProfile.getUuid()).map(SigningProfile::getEnabled).orElse(false));
        Assertions.assertTrue(signingProfileRepository.findById(UUID.fromString(second.getUuid())).map(SigningProfile::getEnabled).orElse(false));
        Assertions.assertTrue(signingProfileRepository.findById(UUID.fromString(third.getUuid())).map(SigningProfile::getEnabled).orElse(false));
    }

    @Test
    void testBulkDisableSigningProfiles_multipleProfiles() throws AttributeException, NotFoundException {
        // Create two additional enabled profiles
        SigningProfileRequestDto req2 = buildDelegatedRawRequest("second-for-bulk-disable");
        req2.setEnabled(true);
        SigningProfileDto second = signingProfileService.createSigningProfile(req2);

        SigningProfileRequestDto req3 = buildDelegatedDocumentRequest("third-for-bulk-disable");
        req3.setEnabled(true);
        SigningProfileDto third = signingProfileService.createSigningProfile(req3);

        signingProfileService.bulkDisableSigningProfiles(List.of(
                SecuredUUID.fromString(second.getUuid()),
                SecuredUUID.fromString(third.getUuid())
        ));

        Assertions.assertFalse(signingProfileRepository.findById(UUID.fromString(second.getUuid())).map(SigningProfile::getEnabled).orElse(true));
        Assertions.assertFalse(signingProfileRepository.findById(UUID.fromString(third.getUuid())).map(SigningProfile::getEnabled).orElse(true));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Protocol activation — ILM
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testActivateIlmSigningProtocol_linksConfigToProfile() throws NotFoundException {
        IlmSigningProtocolConfiguration ilmConfig = new IlmSigningProtocolConfiguration();
        ilmConfig.setName("test-ilm-config");
        ilmConfig = ilmRepository.save(ilmConfig);

        signingProfileService.activateIlmSigningProtocol(savedProfile.getSecuredUuid(), ilmConfig.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(ilmConfig.getUuid(), fromDb.get().getIlmSigningProtocolConfigurationUuid());
    }

    @Test
    void testActivateIlmSigningProtocol_profileNotFound_throwsNotFoundException() {
        IlmSigningProtocolConfiguration ilmConfig = new IlmSigningProtocolConfiguration();
        ilmConfig.setName("ilm-for-not-found-test");
        ilmConfig = ilmRepository.save(ilmConfig);
        final UUID ilmUuid = ilmConfig.getUuid();

        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.activateIlmSigningProtocol(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001"),
                        SecuredUUID.fromUUID(ilmUuid)));
    }

    @Test
    void testActivateIlmSigningProtocol_ilmConfigNotFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.activateIlmSigningProtocol(
                        savedProfile.getSecuredUuid(),
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000002")));
    }

    @Test
    void testActivateIlmSigningProtocol_replacesExistingLink() throws NotFoundException {
        IlmSigningProtocolConfiguration ilmConfig1 = new IlmSigningProtocolConfiguration();
        ilmConfig1.setName("ilm-config-1");
        ilmConfig1 = ilmRepository.save(ilmConfig1);

        IlmSigningProtocolConfiguration ilmConfig2 = new IlmSigningProtocolConfiguration();
        ilmConfig2.setName("ilm-config-2");
        ilmConfig2 = ilmRepository.save(ilmConfig2);

        // Link the first config
        signingProfileService.activateIlmSigningProtocol(savedProfile.getSecuredUuid(), ilmConfig1.getSecuredUuid());
        // Replace with the second config
        signingProfileService.activateIlmSigningProtocol(savedProfile.getSecuredUuid(), ilmConfig2.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(ilmConfig2.getUuid(), fromDb.get().getIlmSigningProtocolConfigurationUuid(),
                "The profile should reference the second ILM config after replacement");
    }

    @Test
    void testDeactivateIlmSigningProtocol_unlinksConfigFromProfile() throws NotFoundException {
        IlmSigningProtocolConfiguration ilmConfig = new IlmSigningProtocolConfiguration();
        ilmConfig.setName("test-ilm-config");
        ilmConfig = ilmRepository.save(ilmConfig);

        savedProfile.setIlmSigningProtocolConfiguration(ilmConfig);
        signingProfileRepository.save(savedProfile);

        signingProfileService.deactivateIlmSigningProtocol(savedProfile.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertNull(fromDb.get().getIlmSigningProtocolConfigurationUuid());
    }

    @Test
    void testDeactivateIlmSigningProtocol_profileNotFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.deactivateIlmSigningProtocol(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDeactivateIlmSigningProtocol_removesFromEnabledProtocols() throws NotFoundException {
        IlmSigningProtocolConfiguration ilmConfig = new IlmSigningProtocolConfiguration();
        ilmConfig.setName("ilm-to-deactivate");
        ilmConfig = ilmRepository.save(ilmConfig);

        savedProfile.setIlmSigningProtocolConfiguration(ilmConfig);
        signingProfileRepository.save(savedProfile);

        // Verify ILM is listed as an enabled protocol before deactivation
        SigningProfileDto before = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);
        Assertions.assertTrue(before.getEnabledProtocols().contains(SigningProtocol.ILM_SIGNING_PROTOCOL));

        signingProfileService.deactivateIlmSigningProtocol(savedProfile.getSecuredUuid());

        // After deactivation, ILM should no longer appear in enabled protocols
        SigningProfileDto after = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);
        Assertions.assertFalse(after.getEnabledProtocols().contains(SigningProtocol.ILM_SIGNING_PROTOCOL),
                "ILM_SIGNING_PROTOCOL should be removed from enabledProtocols after deactivation");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Protocol activation — TSP
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testActivateTsp_linksConfigToProfile() throws NotFoundException {
        TspConfiguration tspConfig = new TspConfiguration();
        tspConfig.setName("test-tsp-config");
        tspConfig = tspRepository.save(tspConfig);

        signingProfileService.activateTsp(savedProfile.getSecuredUuid(), tspConfig.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(tspConfig.getUuid(), fromDb.get().getTspConfigurationUuid());
    }

    @Test
    void testActivateTsp_profileNotFound_throwsNotFoundException() {
        TspConfiguration tspConfig = new TspConfiguration();
        tspConfig.setName("tsp-for-not-found-test");
        tspConfig = tspRepository.save(tspConfig);
        final UUID tspUuid = tspConfig.getUuid();

        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.activateTsp(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001"),
                        SecuredUUID.fromUUID(tspUuid)));
    }

    @Test
    void testActivateTsp_tspConfigNotFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.activateTsp(
                        savedProfile.getSecuredUuid(),
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000002")));
    }

    @Test
    void testActivateTsp_replacesExistingLink() throws NotFoundException {
        TspConfiguration tspConfig1 = new TspConfiguration();
        tspConfig1.setName("tsp-config-1");
        tspConfig1 = tspRepository.save(tspConfig1);

        TspConfiguration tspConfig2 = new TspConfiguration();
        tspConfig2.setName("tsp-config-2");
        tspConfig2 = tspRepository.save(tspConfig2);

        // Link the first config
        signingProfileService.activateTsp(savedProfile.getSecuredUuid(), tspConfig1.getSecuredUuid());
        // Replace with the second config
        signingProfileService.activateTsp(savedProfile.getSecuredUuid(), tspConfig2.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(tspConfig2.getUuid(), fromDb.get().getTspConfigurationUuid(),
                "The profile should reference the second TSP config after replacement");
    }

    @Test
    void testDeactivateTsp_unlinksConfigFromProfile() throws NotFoundException {
        TspConfiguration tspConfig = new TspConfiguration();
        tspConfig.setName("test-tsp-config");
        tspConfig = tspRepository.save(tspConfig);

        savedProfile.setTspConfiguration(tspConfig);
        signingProfileRepository.save(savedProfile);

        signingProfileService.deactivateTsp(savedProfile.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertNull(fromDb.get().getTspConfigurationUuid());
    }

    @Test
    void testDeactivateTsp_profileNotFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.deactivateTsp(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDeactivateTsp_removesFromEnabledProtocols() throws NotFoundException {
        TspConfiguration tspConfig = new TspConfiguration();
        tspConfig.setName("tsp-to-deactivate");
        tspConfig = tspRepository.save(tspConfig);

        savedProfile.setTspConfiguration(tspConfig);
        signingProfileRepository.save(savedProfile);

        // Verify TSP is listed as an enabled protocol before deactivation
        SigningProfileDto before = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);
        Assertions.assertTrue(before.getEnabledProtocols().contains(SigningProtocol.TSP));

        signingProfileService.deactivateTsp(savedProfile.getSecuredUuid());

        // After deactivation, TSP should no longer appear in enabled protocols
        SigningProfileDto after = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);
        Assertions.assertFalse(after.getEnabledProtocols().contains(SigningProtocol.TSP),
                "TSP should be removed from enabledProtocols after deactivation");
    }
}
