package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeV2;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttributeV2;
import com.czertainly.api.model.client.attribute.ResponseAttributeV3;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.DelegatedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.api.model.client.signing.profile.scheme.OneTimeKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningDto;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.ContentSigningWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.ContentSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.RawSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.WorkflowRequestDto;
import com.czertainly.api.model.core.oid.SystemOid;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.AttributeVersion;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v3.CustomAttributeV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.AttributeRelation;
import com.czertainly.core.dao.entity.signing.SigningProfileVersion;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.api.model.common.attribute.common.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.DataAttributeV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.common.enums.cryptography.RsaSignatureScheme;
import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.api.model.client.connector.v2.ConnectorVersion;
import com.czertainly.core.attribute.RsaSignatureAttributes;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.AttributeOperation;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.entity.signing.DigitalSignature;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TspProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.dao.repository.signing.DigitalSignatureRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileVersionRepository;
import com.czertainly.core.dao.repository.signing.TspProfileRepository;
import com.czertainly.core.model.signing.workflow.ManagedContentSigningWorkflow;
import com.czertainly.core.model.signing.workflow.ManagedRawSigningWorkflow;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.model.signing.workflow.TimestampingWorkflow;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.MetaDefinitions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SigningProfileServiceImplTest extends BaseSpringBootTest {

    private static final String CUSTOM_ATTR_UUID = "a1b2c3d4-0001-0002-0003-000000000003";
    private static final String CUSTOM_ATTR_NAME = "signingProfileTestAttribute";

    @Autowired
    private SigningProfileService signingProfileService;

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

    @Autowired
    private TokenProfileRepository tokenProfileRepository;

    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;

    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private SigningProfileRepository signingProfileRepository;

    @Autowired
    private SigningProfileVersionRepository signingProfileVersionRepository;

    @Autowired
    private DigitalSignatureRepository digitalSignatureRepository;

    @Autowired
    private TspProfileRepository tspRepository;

    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;

    @Autowired
    private AttributeRelationRepository attributeRelationRepository;

    /**
     * A signing profile saved directly via repository, used as pre-existing data in tests.
     */
    private SigningProfile savedProfile;

    /**
     * A token profile used as an FK reference in static-key managed signing scheme requests.
     */
    private TokenProfile tokenProfile;

    /**
     * A CryptographicKey backed by an MLDSA key item (empty signing operation attribute definitions).
     * Used for generic static-key scheme tests that do not exercise signing operation attributes.
     */
    private CryptographicKey cryptographicKey;

    /**
     * A CryptographicKey backed by an RSA key item (RSA signing operation attribute definitions).
     * Used for tests that specifically exercise signing operation attribute storage and retrieval.
     */
    private CryptographicKey rsaCryptographicKey;

    /**
     * A Certificate associated with {@link #cryptographicKey} (MLDSA key).
     * Satisfies all conditions of constructQueryDigitalSigningCertAcceptable:
     * not archived, state=ISSUED, validationStatus=VALID, key has a private key that is ACTIVE
     * with SIGN usage, and the associated key has a Token Profile assigned.
     */
    private Certificate certificate;

    /**
     * A Certificate associated with {@link #rsaCryptographicKey} (RSA key).
     * Satisfies the same conditions as {@link #certificate}.
     */
    private Certificate rsaCertificate;

    /**
     * A Certificate specifically configured for TIMESTAMPING workflow type.
     * Contains the id-kp-timeStamping EKU and is marked as critical.
     */
    private Certificate tsaCertificate;

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

        // Shared token instance infrastructure required by the static-key managed scheme
        Connector connector = new Connector();
        connector.setName("cryptography-connector");
        connector.setUrl("http://cryptography-connector");
        connector.setVersion(ConnectorVersion.V1);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        TokenInstanceReference tokenInstanceRef = new TokenInstanceReference();
        tokenInstanceRef.setName("test-token-instance");
        tokenInstanceRef.setTokenInstanceUuid(UUID.randomUUID().toString());
        tokenInstanceRef.setConnector(connector);
        tokenInstanceRef.setStatus(TokenInstanceStatus.CONNECTED);
        tokenInstanceRef = tokenInstanceReferenceRepository.saveAndFlush(tokenInstanceRef);

        tokenProfile = new TokenProfile();
        tokenProfile.setName("test-token-profile");
        tokenProfile.setTokenInstanceReference(tokenInstanceRef);
        tokenProfile.setEnabled(true);
        tokenProfile.setTokenInstanceName("test-token-instance");
        tokenProfile = tokenProfileRepository.saveAndFlush(tokenProfile);

        // MLDSA key — produces empty attribute definitions; used by generic scheme tests
        cryptographicKey = new CryptographicKey();
        cryptographicKey.setName("test-key-mldsa");
        cryptographicKey.setTokenProfile(tokenProfile);
        cryptographicKey.setTokenInstanceReference(tokenInstanceRef);
        cryptographicKey = cryptographicKeyRepository.saveAndFlush(cryptographicKey);

        CryptographicKeyItem mldsaKeyItem = new CryptographicKeyItem();
        mldsaKeyItem.setKey(cryptographicKey);
        mldsaKeyItem.setKeyUuid(cryptographicKey.getUuid());
        mldsaKeyItem.setType(KeyType.PRIVATE_KEY);
        mldsaKeyItem.setState(KeyState.ACTIVE);
        mldsaKeyItem.setEnabled(true);
        mldsaKeyItem.setKeyAlgorithm(KeyAlgorithm.MLDSA);
        mldsaKeyItem.setLength(2048);
        mldsaKeyItem.setUsage(List.of(KeyUsage.SIGN));
        mldsaKeyItem = cryptographicKeyItemRepository.saveAndFlush(mldsaKeyItem);
        mldsaKeyItem.setKeyReferenceUuid(mldsaKeyItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(mldsaKeyItem);

        // Certificate associated with the MLDSA key; satisfies constructQueryDigitalSigningCertAcceptable conditions
        certificate = new Certificate();
        certificate.setKey(cryptographicKey);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate = certificateRepository.saveAndFlush(certificate);

        // RSA key — produces RSA attribute definitions; used by attribute-persistence tests
        rsaCryptographicKey = new CryptographicKey();
        rsaCryptographicKey.setName("test-key-rsa");
        rsaCryptographicKey.setTokenProfile(tokenProfile);
        rsaCryptographicKey.setTokenInstanceReference(tokenInstanceRef);
        rsaCryptographicKey = cryptographicKeyRepository.saveAndFlush(rsaCryptographicKey);

        CryptographicKeyItem rsaKeyItem = new CryptographicKeyItem();
        rsaKeyItem.setKey(rsaCryptographicKey);
        rsaKeyItem.setKeyUuid(rsaCryptographicKey.getUuid());
        rsaKeyItem.setType(KeyType.PRIVATE_KEY);
        rsaKeyItem.setState(KeyState.ACTIVE);
        rsaKeyItem.setEnabled(true);
        rsaKeyItem.setKeyAlgorithm(KeyAlgorithm.RSA);
        rsaKeyItem.setLength(2048);
        rsaKeyItem.setUsage(List.of(KeyUsage.SIGN));
        rsaKeyItem = cryptographicKeyItemRepository.saveAndFlush(rsaKeyItem);
        rsaKeyItem.setKeyReferenceUuid(rsaKeyItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(rsaKeyItem);

        // Certificate associated with the RSA key; satisfies constructQueryDigitalSigningCertAcceptable conditions
        rsaCertificate = new Certificate();
        rsaCertificate.setKey(rsaCryptographicKey);
        rsaCertificate.setState(CertificateState.ISSUED);
        rsaCertificate.setValidationStatus(CertificateValidationStatus.VALID);
        rsaCertificate = certificateRepository.saveAndFlush(rsaCertificate);

        // Certificate specifically configured for TIMESTAMPING; satisfies RFC 3161 requirements
        tsaCertificate = new Certificate();
        tsaCertificate.setKey(rsaCryptographicKey);
        tsaCertificate.setState(CertificateState.ISSUED);
        tsaCertificate.setValidationStatus(CertificateValidationStatus.VALID);
        tsaCertificate.setExtendedKeyUsage(MetaDefinitions.serializeArrayString(List.of(SystemOid.TIME_STAMPING.getOid())));
        tsaCertificate.setExtendedKeyUsageCritical(true);
        tsaCertificate = certificateRepository.saveAndFlush(tsaCertificate);

        // Register a custom attribute available for Signing Profile resources
        CustomAttributeV3 attrDef = new CustomAttributeV3();
        attrDef.setUuid(CUSTOM_ATTR_UUID);
        attrDef.setName(CUSTOM_ATTR_NAME);
        attrDef.setDescription("test custom attribute for signing profile");
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
        attributeRelation.setResource(Resource.SIGNING_PROFILE);
        attributeRelation.setAttributeDefinitionUuid(attributeDefinition.getUuid());
        attributeRelationRepository.save(attributeRelation);
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
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and RAW_SIGNING workflow.
     * Uses the shared MLDSA {@link #cryptographicKey} so no signing-operation-attribute
     * definitions are produced and no attribute content needs to be provided.
     */
    private SigningProfileRequestDto buildManagedStaticKeyRawRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(certificate.getUuid());
        request.setSigningScheme(scheme);
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
        request.setSigningScheme(new OneTimeKeyManagedSigningRequestDto());
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a DELEGATED scheme and CONTENT_SIGNING workflow.
     */
    private SigningProfileRequestDto buildDelegatedContentRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new ContentSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a DELEGATED scheme and TIMESTAMPING workflow.
     */
    private SigningProfileRequestDto buildDelegatedTimestampingRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new TimestampingWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and CONTENT_SIGNING workflow,
     * optionally setting a Signature Formatter Connector UUID on the workflow.
     */
    private SigningProfileRequestDto buildManagedStaticKeyContentRequest(String name, UUID formatterConnectorUuid) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(certificate.getUuid());
        request.setSigningScheme(scheme);
        ContentSigningWorkflowRequestDto workflow = new ContentSigningWorkflowRequestDto();
        workflow.setSignatureFormatterConnectorUuid(formatterConnectorUuid);
        request.setWorkflow(workflow);
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and TIMESTAMPING workflow,
     * with no additional validation properties set.
     */
    private SigningProfileRequestDto buildManagedStaticKeyTimestampingRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(tsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));
        request.setSigningScheme(scheme);
        request.setWorkflow(new TimestampingWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and TIMESTAMPING workflow
     * with a default policy ID, two allowed policy IDs, and SHA-256 as an allowed digest algorithm.
     */
    private SigningProfileRequestDto buildManagedStaticKeyTimestampingRequestWithValidationProps(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(tsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));
        request.setSigningScheme(scheme);
        TimestampingWorkflowRequestDto wf = new TimestampingWorkflowRequestDto();
        wf.setDefaultPolicyId("1.2.3.4.5");
        wf.setAllowedPolicyIds(List.of("1.2.3.4.5", "1.2.3.4.6"));
        wf.setAllowedDigestAlgorithms(List.of(DigestAlgorithm.SHA_256));
        request.setWorkflow(wf);
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
        Assertions.assertFalse(listed.isEnabled());
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
        signingProfileService.createSigningProfile(buildDelegatedContentRequest("content-profile"));
        signingProfileService.createSigningProfile(buildDelegatedTimestampingRequest("timestamping-profile"));

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<SigningProfileListDto> response = signingProfileService.listSigningProfiles(request, SecurityFilter.create());

        Assertions.assertNotNull(response);
        Assertions.assertEquals(3, response.getTotalItems());

        List<SigningWorkflowType> returnedTypes = response.getItems().stream()
                .map(SigningProfileListDto::getSigningWorkflowType)
                .toList();
        Assertions.assertTrue(returnedTypes.contains(SigningWorkflowType.RAW_SIGNING));
        Assertions.assertTrue(returnedTypes.contains(SigningWorkflowType.CONTENT_SIGNING));
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
        Assertions.assertFalse(dto.isEnabled());
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

        // Update to DELEGATED + CONTENT_SIGNING → should bump to version 2
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedContentRequest("profile-history"));

        // Version 1 snapshot must still report RAW_SIGNING
        SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
        Assertions.assertEquals(1, v1.getVersion());
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, v1.getWorkflow().getType());

        // Latest (version 2) must report CONTENT_SIGNING
        SigningProfileDto latest = signingProfileService.getSigningProfile(profileUuid, null);
        Assertions.assertEquals(2, latest.getVersion());
        Assertions.assertEquals(SigningWorkflowType.CONTENT_SIGNING, latest.getWorkflow().getType());
    }

    @Test
    void testGetSigningProfile_noProtocolsLinked_enabledProtocolsIsEmpty() throws NotFoundException {
        // savedProfile has no TSP linked
        SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

        Assertions.assertNotNull(dto.getEnabledProtocols());
        Assertions.assertTrue(dto.getEnabledProtocols().isEmpty(),
                "No protocols should be enabled when none are linked");
    }

    @Test
    void testGetSigningProfile_withTspLinked_enabledProtocolsContainsTsp() throws NotFoundException {
        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("tsp-for-dto-test");
        tspProfile = tspRepository.save(tspProfile);

        savedProfile.setTspProfile(tspProfile);
        signingProfileRepository.save(savedProfile);

        SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

        Assertions.assertNotNull(dto.getEnabledProtocols());
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
        Assertions.assertFalse(dto.isEnabled());
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
        Optional<SigningProfileVersion> snapshot = signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuid, 1);

        Assertions.assertTrue(snapshot.isPresent(), "Version 1 snapshot should be created on profile creation");
        Assertions.assertEquals(1, snapshot.get().getVersion());
        Assertions.assertNotNull(snapshot.get().getSchemeSnapshot());
        Assertions.assertNotNull(snapshot.get().getWorkflowSnapshot());
    }

    @Test
    void testCreateSigningProfile_enabled_assertDtoAndDbEntity() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedRawRequest("enabled-profile");

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);
        Assertions.assertFalse(dto.isEnabled());

        signingProfileService.enableSigningProfile(SecuredUUID.fromString(dto.getUuid()));

        // Assert entity in database
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertTrue(fromDb.get().getEnabled());
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
        // No delegated connector when using the managed signing scheme
        Assertions.assertNull(entity.getDelegatedSignerConnectorUuid());
        // No RA profile / CSR template for the static key managed signing scheme
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
        // Certificate UUID is not set for one-time key type
        Assertions.assertNull(entity.getCertificateUuid());
    }

    @Test
    void testCreateSigningProfile_allSchemeTypesCreateVersionSnapshot() throws AttributeException, NotFoundException {
        SigningProfileDto staticKeyDto = signingProfileService.createSigningProfile(buildManagedStaticKeyRawRequest("snapshot-static"));
        SigningProfileDto oneTimeKeyDto = signingProfileService.createSigningProfile(buildManagedOneTimeKeyRawRequest("snapshot-onetime"));

        for (String uuidStr : List.of(staticKeyDto.getUuid(), oneTimeKeyDto.getUuid())) {
            UUID profileUuid = UUID.fromString(uuidStr);
            Optional<SigningProfileVersion> snapshot = signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuid, 1);
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
    void testCreateSigningProfile_contentSigningWorkflow_assertWorkflowTypeAndEntity() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildDelegatedContentRequest("content-signing-profile");
        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertNotNull(dto.getWorkflow());
        Assertions.assertEquals(SigningWorkflowType.CONTENT_SIGNING, dto.getWorkflow().getType());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(SigningWorkflowType.CONTENT_SIGNING, fromDb.get().getWorkflowType());
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
    void testCreateSigningProfile_timestampingWorkflowWithPoliciesAndAlgorithms_assertEntityFields() throws AttributeException, NotFoundException {
        TimestampingWorkflowRequestDto timestampingWorkflow = new TimestampingWorkflowRequestDto();
        timestampingWorkflow.setDefaultPolicyId("1.2.3.4.5");
        timestampingWorkflow.setAllowedPolicyIds(List.of("1.2.3.4.5", "1.2.3.4.6"));
        timestampingWorkflow.setAllowedDigestAlgorithms(List.of(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384));
        timestampingWorkflow.setQualifiedTimestamp(false);

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("timestamping-with-policies");
        request.setDescription("Timestamping profile with policies");
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

        // Assert entity fields in the database
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
    void testCreateSigningProfile_managedStaticKey_withContentSigningWorkflow_assertBothFields() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("managed-content-profile");
        request.setDescription("Managed static-key profile with content signing workflow");
        StaticKeyManagedSigningRequestDto managedContentScheme = new StaticKeyManagedSigningRequestDto();
        managedContentScheme.setCertificateUuid(certificate.getUuid());
        request.setSigningScheme(managedContentScheme);
        request.setWorkflow(new ContentSigningWorkflowRequestDto());

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertEquals(SigningScheme.MANAGED, dto.getSigningScheme().getSigningScheme());
        Assertions.assertEquals(SigningWorkflowType.CONTENT_SIGNING, dto.getWorkflow().getType());
        Assertions.assertFalse(dto.isEnabled());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(dto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
        Assertions.assertEquals(ManagedSigningType.STATIC_KEY, entity.getManagedSigningType());
        Assertions.assertEquals(SigningWorkflowType.CONTENT_SIGNING, entity.getWorkflowType());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testUpdateSigningProfile_assertDtoAndDbEntity() throws NotFoundException, AttributeException {
        SigningProfileRequestDto request = buildDelegatedRawRequest("updated-profile");
        request.setDescription("Updated description");

        SigningProfileDto dto = signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), request);

        // Assert returned DTO
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(savedProfile.getUuid().toString(), dto.getUuid());
        Assertions.assertEquals("updated-profile", dto.getName());
        Assertions.assertEquals("Updated description", dto.getDescription());
        Assertions.assertFalse(dto.isEnabled());
        Assertions.assertEquals(1, dto.getVersion()); // no bump — no digital signatures exist

        // Assert entity reloaded from the database
        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        SigningProfile entity = fromDb.get();
        Assertions.assertEquals("updated-profile", entity.getName());
        Assertions.assertEquals("Updated description", entity.getDescription());
        Assertions.assertFalse(entity.getEnabled());
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
        Optional<SigningProfileVersion> v2snapshot = signingProfileVersionRepository.findBySigningProfileUuidAndVersion(savedProfile.getUuid(), 2);
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
        // token profile and certificate references must have been cleared
        Assertions.assertNull(entity.getCertificateUuid());
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
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedContentRequest("overwrite-snapshot-profile"));

        // Still only one snapshot (version 1) — overwritten
        Optional<SigningProfileVersion> v1Snapshot = signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 1);
        Assertions.assertTrue(v1Snapshot.isPresent(), "Version 1 snapshot should still exist after in-place overwrite");
        Assertions.assertFalse(
                signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 2).isPresent(),
                "No version 2 snapshot should exist when version was not bumped"
        );

        // Fetching version 1 should now return the updated workflow type
        SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
        Assertions.assertEquals(SigningWorkflowType.CONTENT_SIGNING, v1.getWorkflow().getType(),
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
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedContentRequest("multi-bump-profile"));

        // Trigger second bump: add sig for v2, then update → v3
        entity = signingProfileRepository.findById(profileUuidRaw).orElseThrow();
        createDigitalSignatureFor(entity, 2);
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedTimestampingRequest("multi-bump-profile"));

        // Verify three snapshot versions exist
        Assertions.assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 1).isPresent());
        Assertions.assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 2).isPresent());
        Assertions.assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 3).isPresent());

        // Verify latest is version 3 with TIMESTAMPING
        SigningProfileDto latest = signingProfileService.getSigningProfile(profileUuid, null);
        Assertions.assertEquals(3, latest.getVersion());
        Assertions.assertEquals(SigningWorkflowType.TIMESTAMPING, latest.getWorkflow().getType());

        // Verify version 1 still has RAW_SIGNING
        SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
        Assertions.assertEquals(SigningWorkflowType.RAW_SIGNING, v1.getWorkflow().getType());

        // Verify version 2 has CONTENT_SIGNING
        SigningProfileDto v2 = signingProfileService.getSigningProfile(profileUuid, 2);
        Assertions.assertEquals(SigningWorkflowType.CONTENT_SIGNING, v2.getWorkflow().getType());
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
    void testDeleteSigningProfile_usedAsDefaultIntspProfile_clearsReferenceAndDeletes() throws NotFoundException {
        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("blocking-tsp-profile");
        tspProfile.setDefaultSigningProfile(savedProfile);
        tspProfile = tspRepository.save(tspProfile);
        final UUID tspProfileUuid = tspProfile.getUuid();

        signingProfileService.deleteSigningProfile(savedProfile.getSecuredUuid());

        // Profile should be removed from the database
        Assertions.assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        // The TSP profile's default signing profile reference should be cleared
        TspProfile reloadedTsp = tspRepository.findById(tspProfileUuid).orElseThrow();
        Assertions.assertNull(reloadedTsp.getDefaultSigningProfileUuid(),
                "TSP profile's default signing profile UUID should be cleared after profile deletion");
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
        Assertions.assertTrue(dto.isEnabled());

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
        Assertions.assertFalse(dto.isEnabled());

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
        SigningProfileDto third = signingProfileService.createSigningProfile(buildDelegatedContentRequest("third-for-bulk-enable"));

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
        SigningProfileDto second = signingProfileService.createSigningProfile(req2);
        signingProfileService.enableSigningProfile(SecuredUUID.fromString(second.getUuid()));

        SigningProfileRequestDto req3 = buildDelegatedContentRequest("third-for-bulk-disable");
        SigningProfileDto third = signingProfileService.createSigningProfile(req3);
        signingProfileService.enableSigningProfile(SecuredUUID.fromString(third.getUuid()));

        signingProfileService.bulkDisableSigningProfiles(List.of(
                SecuredUUID.fromString(second.getUuid()),
                SecuredUUID.fromString(third.getUuid())
        ));

        Assertions.assertFalse(signingProfileRepository.findById(UUID.fromString(second.getUuid())).map(SigningProfile::getEnabled).orElse(true));
        Assertions.assertFalse(signingProfileRepository.findById(UUID.fromString(third.getUuid())).map(SigningProfile::getEnabled).orElse(true));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Protocol activation — TSP
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testActivateTsp_linksProfiles() throws AttributeException, NotFoundException {
        SigningProfileDto profileDto = signingProfileService.createSigningProfile(buildDelegatedTimestampingRequest("timestamping-for-tsp-activate"));
        SecuredUUID profileUuid = SecuredUUID.fromString(profileDto.getUuid());

        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("test-tsp-profile");
        tspProfile = tspRepository.save(tspProfile);

        var activationDto = signingProfileService.activateTsp(profileUuid, tspProfile.getSecuredUuid());
        Assertions.assertTrue(activationDto.isAvailable());
        Assertions.assertNotNull(activationDto.getSigningUrl());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(profileDto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(tspProfile.getUuid(), fromDb.get().getTspProfileUuid());
    }

    @Test
    void testActivateTsp_profileNotFound_throwsNotFoundException() {
        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("tsp-for-not-found-test");
        tspProfile = tspRepository.save(tspProfile);
        final UUID tspUuid = tspProfile.getUuid();

        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.activateTsp(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001"),
                        SecuredUUID.fromUUID(tspUuid)));
    }

    @Test
    void testActivateTsp_tspProfileNotFound_throwsNotFoundException() throws AttributeException, NotFoundException {
        SigningProfileDto profileDto = signingProfileService.createSigningProfile(buildDelegatedTimestampingRequest("timestamping-for-tsp-not-found"));
        SecuredUUID profileUuid = SecuredUUID.fromString(profileDto.getUuid());

        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.activateTsp(
                        profileUuid,
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000002")));
    }

    @Test
    void testActivateTsp_replacesExistingLink() throws AttributeException, NotFoundException {
        SigningProfileDto profileDto = signingProfileService.createSigningProfile(buildDelegatedTimestampingRequest("timestamping-for-tsp-replace"));
        SecuredUUID profileUuid = SecuredUUID.fromString(profileDto.getUuid());

        TspProfile tspProfile1 = new TspProfile();
        tspProfile1.setName("tsp-profile-1");
        tspProfile1 = tspRepository.save(tspProfile1);

        TspProfile tspProfile2 = new TspProfile();
        tspProfile2.setName("tsp-profile-2");
        tspProfile2 = tspRepository.save(tspProfile2);

        // Link the first [rpfo;e
        signingProfileService.activateTsp(profileUuid, tspProfile1.getSecuredUuid());
        // Replace it with the second profile
        signingProfileService.activateTsp(profileUuid, tspProfile2.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(UUID.fromString(profileDto.getUuid()));
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(tspProfile2.getUuid(), fromDb.get().getTspProfileUuid(),
                "The profile should reference the second TSP profile after replacement");
    }

    @Test
    void testDeactivateTsp_unlinksProfiles() throws NotFoundException {
        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("test-tsp-profile");
        tspProfile = tspRepository.save(tspProfile);

        savedProfile.setTspProfile(tspProfile);
        signingProfileRepository.save(savedProfile);

        signingProfileService.deactivateTsp(savedProfile.getSecuredUuid());

        Optional<SigningProfile> fromDb = signingProfileRepository.findById(savedProfile.getUuid());
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertNull(fromDb.get().getTspProfileUuid());
    }

    @Test
    void testActivateTsp_unsupportedWorkflowType_throwsValidationException() {
        // savedProfile uses RAW_SIGNING which does not support TSP
        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("tsp-profile-unsupported-workflow");
        tspProfile = tspRepository.save(tspProfile);
        final SecuredUUID tspUuid = tspProfile.getSecuredUuid();

        Assertions.assertThrows(ValidationException.class,
                () -> signingProfileService.activateTsp(savedProfile.getSecuredUuid(), tspUuid));
    }

    @Test
    void testDeactivateTsp_profileNotFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.deactivateTsp(
                        SecuredUUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void testDeactivateTsp_removesFromEnabledProtocols() throws NotFoundException {
        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("tsp-to-deactivate");
        tspProfile = tspRepository.save(tspProfile);

        savedProfile.setTspProfile(tspProfile);
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

    // ──────────────────────────────────────────────────────────────────────────
    // Connector attribute persistence — signingOperationAttributes
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a valid RSA {@code signingOperationAttributes} request attribute for use in tests.
     */
    private RequestAttributeV2 buildRsaSchemeAttribute(RsaSignatureScheme scheme) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME);
        attr.setContentType(AttributeContentType.STRING);
        StringAttributeContentV2 content = new StringAttributeContentV2(scheme.getLabel(), scheme.getCode());
        attr.setContent(List.of(content));
        return attr;
    }

    /**
     * Builds a valid digest {@code signingOperationAttributes} request attribute for use in tests.
     */
    private RequestAttributeV2 buildDigestAttribute(DigestAlgorithm algorithm) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST);
        attr.setContentType(AttributeContentType.STRING);
        StringAttributeContentV2 content = new StringAttributeContentV2(algorithm.getLabel(), algorithm.getCode());
        attr.setContent(List.of(content));
        return attr;
    }

    @Test
    void testCreateSigningProfile_staticKey_signingOperationAttributesPersistedAndReturned() throws AttributeException, NotFoundException {
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(rsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("static-key-with-sign-attrs");
        request.setDescription("Profile with signing operation attributes");
        request.setSigningScheme(scheme);
        request.setWorkflow(new RawSigningWorkflowRequestDto());

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        // The returned DTO must expose the persisted signing operation attributes
        Assertions.assertInstanceOf(StaticKeyManagedSigningDto.class, dto.getSigningScheme());
        StaticKeyManagedSigningDto schemeDto = (StaticKeyManagedSigningDto) dto.getSigningScheme();
        Assertions.assertNotNull(schemeDto.getSigningOperationAttributes());
        Assertions.assertFalse(schemeDto.getSigningOperationAttributes().isEmpty(),
                "Signing operation attributes should be populated after create");
        Assertions.assertTrue(
                schemeDto.getSigningOperationAttributes().stream()
                        .anyMatch(a -> RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME.equals(a.getName())),
                "RSA signature scheme attribute should be present in the returned DTO");
    }

    @Test
    void testGetSigningProfile_staticKey_signingOperationAttributesLoadedFromEngine() throws AttributeException, NotFoundException {
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(rsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("static-key-get-sign-attrs");
        request.setSigningScheme(scheme);
        request.setWorkflow(new RawSigningWorkflowRequestDto());

        SigningProfileDto created = signingProfileService.createSigningProfile(request);
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

        // Re-fetch — attributes must be loaded from AttributeEngine
        SigningProfileDto fetched = signingProfileService.getSigningProfile(profileUuid, null);
        Assertions.assertInstanceOf(StaticKeyManagedSigningDto.class, fetched.getSigningScheme());
        StaticKeyManagedSigningDto schemeDto = (StaticKeyManagedSigningDto) fetched.getSigningScheme();
        Assertions.assertFalse(schemeDto.getSigningOperationAttributes().isEmpty(),
                "Signing operation attributes should survive a create→get round-trip");
    }

    @Test
    void testUpdateSigningProfile_staticKey_signingOperationAttributesReplacedOnUpdate() throws AttributeException, NotFoundException {
        // Create with PKCS1-v1_5 / SHA-256
        StaticKeyManagedSigningRequestDto schemeV1 = new StaticKeyManagedSigningRequestDto();
        schemeV1.setCertificateUuid(rsaCertificate.getUuid());
        schemeV1.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));

        SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
        createRequest.setName("static-key-update-sign-attrs");
        createRequest.setSigningScheme(schemeV1);
        createRequest.setWorkflow(new RawSigningWorkflowRequestDto());

        SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

        // Update to PSS / SHA-384
        StaticKeyManagedSigningRequestDto schemeV2 = new StaticKeyManagedSigningRequestDto();
        schemeV2.setCertificateUuid(rsaCertificate.getUuid());
        schemeV2.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                buildDigestAttribute(DigestAlgorithm.SHA_384)));

        SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
        updateRequest.setName("static-key-update-sign-attrs");
        updateRequest.setSigningScheme(schemeV2);
        updateRequest.setWorkflow(new RawSigningWorkflowRequestDto());

        SigningProfileDto updated = signingProfileService.updateSigningProfile(profileUuid, updateRequest);

        Assertions.assertInstanceOf(StaticKeyManagedSigningDto.class, updated.getSigningScheme());
        StaticKeyManagedSigningDto schemeDto = (StaticKeyManagedSigningDto) updated.getSigningScheme();
        List<ResponseAttribute> signingOperationAttributes = schemeDto.getSigningOperationAttributes();
        Assertions.assertFalse(signingOperationAttributes.isEmpty());
        Optional<ResponseAttribute> rsaSigningSchemeAttribute = signingOperationAttributes.stream().filter(
                a -> RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME.equals(a.getName())).findFirst();
        Assertions.assertTrue(rsaSigningSchemeAttribute.isPresent());
        // The RSA sig-scheme attribute content should reflect the new PSS value, not the old PKCS1-v1_5
        if (AttributeVersion.V2.equals(rsaSigningSchemeAttribute.get().getVersion())) {
            ResponseAttributeV2 rsaSigningSchemeAttributeV2 = (ResponseAttributeV2) rsaSigningSchemeAttribute.get();
            var attributeContentV2 = rsaSigningSchemeAttributeV2.getContent().getFirst();
            Assertions.assertEquals(RsaSignatureScheme.PSS.getCode(), attributeContentV2.getData().toString(),
                    "Signing operation attributes should be replaced with new value on update");
        } else if (AttributeVersion.V3.equals(rsaSigningSchemeAttribute.get().getVersion())) {
            ResponseAttributeV3 rsaSigningSchemeAttributeV3 = (ResponseAttributeV3) rsaSigningSchemeAttribute.get();
            var attributeContentV3 = rsaSigningSchemeAttributeV3.getContent().getFirst();
            Assertions.assertEquals(RsaSignatureScheme.PSS.getCode(), attributeContentV3.getData().toString(),
                    "Signing operation attributes should be replaced with new value on update");
        } else {
            Assertions.fail("Unknown attribute version: " + rsaSigningSchemeAttribute.get().getVersion() + " - the test needs to be updated to handle this version");
        }
    }

    @Test
    void testUpdateSigningProfile_schemeChangedToNonStaticKey_signingOperationAttributesCleared() throws AttributeException, NotFoundException {
        // Create a STATIC_KEY profile with signing operation attributes
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(rsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));

        SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
        createRequest.setName("static-key-to-delegated");
        createRequest.setSigningScheme(scheme);
        createRequest.setWorkflow(new RawSigningWorkflowRequestDto());

        SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

        // Switch to DELEGATED — should clear signing-operation attributes from the engine
        signingProfileService.updateSigningProfile(profileUuid, buildDelegatedRawRequest("static-key-to-delegated"));

        // Verify nothing remains in AttributeEngine under SIGNING_SCHEME for this profile
        List<ResponseAttribute> remaining = attributeEngine.getObjectDataAttributesContent(
                null, AttributeOperation.SIGN, Resource.SIGNING_PROFILE, UUID.fromString(created.getUuid()));
        Assertions.assertTrue(remaining.isEmpty(),
                "Signing-scheme attributes should be deleted when scheme changes away from STATIC_KEY");
    }

    @Test
    void testDeleteSigningProfile_removesSigningOperationAttributesFromEngine()
            throws AttributeException, NotFoundException {
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(rsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("delete-clears-sign-attrs");
        request.setSigningScheme(scheme);
        request.setWorkflow(new RawSigningWorkflowRequestDto());

        SigningProfileDto created = signingProfileService.createSigningProfile(request);
        UUID profileUuid = UUID.fromString(created.getUuid());

        signingProfileService.deleteSigningProfile(SecuredUUID.fromUUID(profileUuid));

        // AttributeEngine should have no attributes left for this profile
        List<ResponseAttribute> remaining = attributeEngine.getObjectDataAttributesContent(
                null, AttributeOperation.SIGN, Resource.SIGNING_PROFILE, profileUuid);
        Assertions.assertTrue(remaining.isEmpty(),
                "Signing-scheme attributes should be removed by deleteAllObjectAttributeContent on profile deletion");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Connector attribute persistence — signatureFormatterConnectorAttributes
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates and persists a minimal {@link Connector} entity for use as a signature formatter connector,
     * also pre-registering a simple data attribute definition so that AttributeEngine can accept content.
     */
    private Connector createFormatterConnector(String name) {
        Connector connector = new Connector();
        connector.setName(name);
        connector.setUrl("http://formatter-connector/" + name);
        connector.setVersion(ConnectorVersion.V1);
        connector.setStatus(ConnectorStatus.CONNECTED);
        return connectorRepository.save(connector);
    }

    /**
     * Builds a {@link RequestAttributeV2} to use as a formatter connector attribute in tests.
     * The UUID and name here are arbitrary but must be pre-registered via
     * {@link AttributeEngine#updateDataAttributeDefinitions} before being stored.
     */
    private RequestAttributeV2 buildFormatterAttribute(UUID attrUuid, String attrName, String value) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(attrUuid);
        attr.setName(attrName);
        attr.setContentType(AttributeContentType.STRING);
        StringAttributeContentV2 content = new StringAttributeContentV2();
        content.setData(value);
        attr.setContent(List.of(content));
        return attr;
    }

    @Test
    void testCreateSigningProfile_contentSigning_formatterAttributesPersistedAndReturned() throws AttributeException, NotFoundException {
        Connector formatter = createFormatterConnector("formatter-content-create");

        // Pre-register the attribute definition so the engine can store content for it
        UUID attrUuid = UUID.randomUUID();
        String attrName = "data_testFormatterAttr";
        DataAttributeV2 attrDef = new DataAttributeV2();
        attrDef.setUuid(attrUuid.toString());
        attrDef.setName(attrName);
        attrDef.setContentType(AttributeContentType.STRING);
        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Test Formatter Attribute");
        attrDef.setProperties(props);
        attributeEngine.updateDataAttributeDefinitions(formatter.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, List.of(attrDef));

        ContentSigningWorkflowRequestDto workflow = new ContentSigningWorkflowRequestDto();
        workflow.setSignatureFormatterConnectorUuid(formatter.getUuid());
        workflow.setSignatureFormatterConnectorAttributes(
                List.of(buildFormatterAttribute(attrUuid, attrName, "testValue")));

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("doc-profile-with-formatter-attrs");
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(workflow);

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertInstanceOf(ContentSigningWorkflowDto.class, dto.getWorkflow());
        ContentSigningWorkflowDto wfDto = (ContentSigningWorkflowDto) dto.getWorkflow();
        Assertions.assertFalse(wfDto.getSignatureFormatterConnectorAttributes().isEmpty(),
                "Formatter connector attributes should be populated after create");
        Assertions.assertEquals(attrName, wfDto.getSignatureFormatterConnectorAttributes().getFirst().getName());
    }

    @Test
    void testUpdateSigningProfile_workflowFormatterConnectorChanged_oldAttributesCleared() throws AttributeException, NotFoundException {
        Connector formatterA = createFormatterConnector("formatter-old");
        Connector formatterB = createFormatterConnector("formatter-new");

        // Pre-register attribute definition for both connectors
        UUID attrUuid = UUID.randomUUID();
        String attrName = "data_switchTest";
        DataAttributeV2 attrDef = new DataAttributeV2();
        attrDef.setUuid(attrUuid.toString());
        attrDef.setName(attrName);
        attrDef.setContentType(AttributeContentType.STRING);
        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Switch Test Attribute");
        attrDef.setProperties(props);
        attributeEngine.updateDataAttributeDefinitions(formatterA.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, List.of(attrDef));
        attributeEngine.updateDataAttributeDefinitions(formatterB.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, List.of(attrDef));

        // Create with formatterA
        ContentSigningWorkflowRequestDto workflowA = new ContentSigningWorkflowRequestDto();
        workflowA.setSignatureFormatterConnectorUuid(formatterA.getUuid());
        workflowA.setSignatureFormatterConnectorAttributes(
                List.of(buildFormatterAttribute(attrUuid, attrName, "valueA")));

        SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
        createRequest.setName("workflow-formatter-switch");
        createRequest.setSigningScheme(new DelegatedSigningRequestDto());
        createRequest.setWorkflow(workflowA);

        SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
        SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
        UUID profileUuidRaw = UUID.fromString(created.getUuid());

        // Update with formatterB — old formatter A attributes should be cleared
        ContentSigningWorkflowRequestDto workflowB = new ContentSigningWorkflowRequestDto();
        workflowB.setSignatureFormatterConnectorUuid(formatterB.getUuid());
        workflowB.setSignatureFormatterConnectorAttributes(
                List.of(buildFormatterAttribute(attrUuid, attrName, "valueB")));

        SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
        updateRequest.setName("workflow-formatter-switch");
        updateRequest.setSigningScheme(new DelegatedSigningRequestDto());
        updateRequest.setWorkflow(workflowB);

        signingProfileService.updateSigningProfile(profileUuid, updateRequest);

        // Attributes for old formatterA should be gone
        List<ResponseAttribute> oldAttrs = attributeEngine.getObjectDataAttributesContent(
                formatterA.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, Resource.SIGNING_PROFILE, profileUuidRaw);
        Assertions.assertTrue(oldAttrs.isEmpty(),
                "Attributes for the old formatter connector should be removed when the connector changes");

        // Attributes for new formatterB should be present
        List<ResponseAttribute> newAttrs = attributeEngine.getObjectDataAttributesContent(
                formatterB.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, Resource.SIGNING_PROFILE, profileUuidRaw);
        Assertions.assertFalse(newAttrs.isEmpty(),
                "Attributes for the new formatter connector should be stored after the update");
    }

    @Test
    void testGetSigningProfile_formatterAttributesReturnedForAllWorkflowTypes() throws AttributeException, NotFoundException {
        Connector formatter = createFormatterConnector("formatter-multi-workflow");

        UUID attrUuid = UUID.randomUUID();
        String attrName = "data_multiWorkflowAttr";
        DataAttributeV2 attrDef = new DataAttributeV2();
        attrDef.setUuid(attrUuid.toString());
        attrDef.setName(attrName);
        attrDef.setContentType(AttributeContentType.STRING);
        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Multi Workflow Attribute");
        attrDef.setProperties(props);
        attributeEngine.updateDataAttributeDefinitions(formatter.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, List.of(attrDef));

        for (SigningWorkflowType workflowLabel : List.of(SigningWorkflowType.CONTENT_SIGNING, SigningWorkflowType.TIMESTAMPING)) {
            WorkflowRequestDto wfRequest;
            switch (workflowLabel) {
                case CONTENT_SIGNING -> {
                    ContentSigningWorkflowRequestDto wf = new ContentSigningWorkflowRequestDto();
                    wf.setSignatureFormatterConnectorUuid(formatter.getUuid());
                    wf.setSignatureFormatterConnectorAttributes(List.of(buildFormatterAttribute(attrUuid, attrName, "val-" + workflowLabel)));
                    wfRequest = wf;
                }
                case TIMESTAMPING -> {
                    TimestampingWorkflowRequestDto wf = new TimestampingWorkflowRequestDto();
                    wf.setSignatureFormatterConnectorUuid(formatter.getUuid());
                    wf.setSignatureFormatterConnectorAttributes(List.of(buildFormatterAttribute(attrUuid, attrName, "val-" + workflowLabel)));
                    wfRequest = wf;
                }
                default -> throw new IllegalStateException("Unexpected workflow type: " + workflowLabel);
            }

            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("formatter-attrs-" + workflowLabel);
            request.setSigningScheme(new DelegatedSigningRequestDto());
            request.setWorkflow(wfRequest);

            SigningProfileDto dto = signingProfileService.createSigningProfile(request);
            SigningProfileDto fetched = signingProfileService.getSigningProfile(
                    SecuredUUID.fromString(dto.getUuid()), null);

            List<ResponseAttribute> fetchedAttrs;
            switch (fetched.getWorkflow().getType()) {
                case CONTENT_SIGNING ->
                        fetchedAttrs = ((ContentSigningWorkflowDto) fetched.getWorkflow()).getSignatureFormatterConnectorAttributes();
                case TIMESTAMPING ->
                        fetchedAttrs = ((TimestampingWorkflowDto) fetched.getWorkflow()).getSignatureFormatterConnectorAttributes();
                default ->
                        throw new IllegalStateException("Unexpected workflow type: " + fetched.getWorkflow().getType());
            }
            Assertions.assertFalse(fetchedAttrs.isEmpty(),
                    "Formatter attributes should be loaded for workflow type: " + workflowLabel);
            Assertions.assertEquals(attrName, fetchedAttrs.getFirst().getName());
        }
    }

    @Test
    void testDeleteSigningProfile_removesFormatterAttributesFromEngine() throws AttributeException, NotFoundException {
        Connector formatter = createFormatterConnector("formatter-delete-test");

        UUID attrUuid = UUID.randomUUID();
        String attrName = "data_deleteFormatterAttr";
        DataAttributeV2 attrDef = new DataAttributeV2();
        attrDef.setUuid(attrUuid.toString());
        attrDef.setName(attrName);
        attrDef.setContentType(AttributeContentType.STRING);
        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel("Delete Formatter Attribute");
        attrDef.setProperties(props);
        attributeEngine.updateDataAttributeDefinitions(formatter.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, List.of(attrDef));

        ContentSigningWorkflowRequestDto workflow = new ContentSigningWorkflowRequestDto();
        workflow.setSignatureFormatterConnectorUuid(formatter.getUuid());
        workflow.setSignatureFormatterConnectorAttributes(
                List.of(buildFormatterAttribute(attrUuid, attrName, "toDelete")));

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName("delete-clears-formatter-attrs");
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(workflow);

        SigningProfileDto created = signingProfileService.createSigningProfile(request);
        UUID profileUuid = UUID.fromString(created.getUuid());

        signingProfileService.deleteSigningProfile(SecuredUUID.fromUUID(profileUuid));

        List<ResponseAttribute> remaining = attributeEngine.getObjectDataAttributesContent(
                formatter.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, Resource.SIGNING_PROFILE, profileUuid);
        Assertions.assertTrue(remaining.isEmpty(),
                "Formatter attributes should be removed by deleteAllObjectAttributeContent on profile deletion");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Custom attributes via ResourceExtensionService
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testCreateSigningProfile_withCustomAttributes_returnedInDto() throws AttributeException, NotFoundException {
        RequestAttributeV3 customAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                List.of(new StringAttributeContentV3("profile-value-on-create")));

        SigningProfileRequestDto request = buildDelegatedRawRequest("profile-with-custom-attr");
        request.setCustomAttributes(List.of(customAttr));

        SigningProfileDto dto = signingProfileService.createSigningProfile(request);

        Assertions.assertNotNull(dto.getCustomAttributes());
        Assertions.assertFalse(dto.getCustomAttributes().isEmpty(),
                "Custom attributes should be returned in the create DTO");
        Assertions.assertEquals("profile-value-on-create",
                ((ResponseAttributeV3) dto.getCustomAttributes().getFirst()).getContent().getFirst().getData());
    }

    @Test
    void testUpdateSigningProfile_withCustomAttributes_returnedInDto() throws AttributeException, NotFoundException {
        RequestAttributeV3 createAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                List.of(new StringAttributeContentV3("initial-value")));
        SigningProfileRequestDto createRequest = buildDelegatedRawRequest("profile-update-custom-attr");
        createRequest.setCustomAttributes(List.of(createAttr));
        SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);

        RequestAttributeV3 updateAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                List.of(new StringAttributeContentV3("updated-value")));
        SigningProfileRequestDto updateRequest = buildDelegatedRawRequest("profile-update-custom-attr");
        updateRequest.setCustomAttributes(List.of(updateAttr));
        SigningProfileDto updated = signingProfileService.updateSigningProfile(
                SecuredUUID.fromString(created.getUuid()), updateRequest);

        Assertions.assertNotNull(updated.getCustomAttributes());
        Assertions.assertFalse(updated.getCustomAttributes().isEmpty());
        Assertions.assertEquals("updated-value",
                ((ResponseAttributeV3) updated.getCustomAttributes().getFirst()).getContent().getFirst().getData());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getSigningProfileModel
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void testGetSigningProfileModel_notFound_throwsNotFoundException() {
        Assertions.assertThrows(NotFoundException.class,
                () -> signingProfileService.getSigningProfileModel("no-such-profile"));
    }

    @Test
    void testGetSigningProfileModel_rawSigning_staticKeyScheme_returnsTypedModelWithResolvedCertificate() throws AttributeException, NotFoundException {
        signingProfileService.createSigningProfile(buildManagedStaticKeyRawRequest("raw-static-key"));

        SigningProfileModel<?, ?> model = signingProfileService.getSigningProfileModel("raw-static-key");

        Assertions.assertInstanceOf(ManagedRawSigningWorkflow.class, model.workflow(),
                "RAW_SIGNING managed profile should produce a ManagedRawSigningWorkflow");
        Assertions.assertInstanceOf(StaticKeyManagedSigning.class, model.signingScheme(),
                "STATIC_KEY scheme should produce a StaticKeyManagedSigning");
        StaticKeyManagedSigning schemeModel = (StaticKeyManagedSigning) model.signingScheme();
        Assertions.assertNotNull(schemeModel.certificate(),
                "Certificate entity should be resolved and non-null");
        Assertions.assertEquals(certificate.getUuid(), schemeModel.certificate().getUuid(),
                "Resolved certificate UUID must match the one configured on the profile");
    }

    @Test
    void testGetSigningProfileModel_contentSigning_staticKeyScheme_returnsTypedModelWithResolvedCertificate() throws AttributeException, NotFoundException {
        signingProfileService.createSigningProfile(buildManagedStaticKeyContentRequest("content-static-key", null));

        SigningProfileModel<?, ?> model = signingProfileService.getSigningProfileModel("content-static-key");

        Assertions.assertInstanceOf(ManagedContentSigningWorkflow.class, model.workflow(),
                "CONTENT_SIGNING managed profile should produce a ManagedContentSigningWorkflow");
        Assertions.assertInstanceOf(StaticKeyManagedSigning.class, model.signingScheme());
        StaticKeyManagedSigning schemeModel = (StaticKeyManagedSigning) model.signingScheme();
        Assertions.assertNotNull(schemeModel.certificate(),
                "Certificate entity should be resolved and non-null");
        Assertions.assertEquals(certificate.getUuid(), schemeModel.certificate().getUuid());
    }

    @Test
    void testGetSigningProfileModel_contentSigning_staticKeyScheme_workflowCarriesFormatterConnectorUuid() throws AttributeException, NotFoundException {
        Connector formatter = createFormatterConnector("formatter-content-create");
        signingProfileService.createSigningProfile(
                buildManagedStaticKeyContentRequest("content-static-key-formatter", formatter.getUuid()));

        SigningProfileModel<?, ?> model = signingProfileService.getSigningProfileModel("content-static-key-formatter");

        ManagedContentSigningWorkflow wf = (ManagedContentSigningWorkflow) model.workflow();
        Assertions.assertNotNull(wf.signatureFormatterConnectorUuid(),
                "Signature Formatter Connector reference should be present when a UUID was configured");
        Assertions.assertEquals(formatter.getUuid(), wf.signatureFormatterConnectorUuid(),
                "Workflow model must carry the exact formatter connector UUID set at profile creation");
    }

    @Test
    void testGetSigningProfileModel_timestamping_staticKeyScheme_returnsTypedModelWithResolvedCertificate() throws AttributeException, NotFoundException {
        signingProfileService.createSigningProfile(buildManagedStaticKeyTimestampingRequest("ts-static-key"));

        SigningProfileModel<?, ?> model = signingProfileService.getSigningProfileModel("ts-static-key");

        Assertions.assertInstanceOf(ManagedTimestampingWorkflow.class, model.workflow(),
                "TIMESTAMPING managed profile should produce a ManagedTimestampingWorkflow");
        Assertions.assertInstanceOf(StaticKeyManagedSigning.class, model.signingScheme());
        StaticKeyManagedSigning schemeModel = (StaticKeyManagedSigning) model.signingScheme();
        Assertions.assertNotNull(schemeModel.certificate(),
                "Certificate entity should be resolved and non-null");
        Assertions.assertEquals(tsaCertificate.getUuid(), schemeModel.certificate().getUuid());
    }

    @Test
    void testGetSigningProfileModel_timestamping_staticKeyScheme_workflowCarriesValidationProperties() throws AttributeException, NotFoundException {
        signingProfileService.createSigningProfile(
                buildManagedStaticKeyTimestampingRequestWithValidationProps("ts-static-key-validation"));

        SigningProfileModel<?, ?> model = signingProfileService.getSigningProfileModel("ts-static-key-validation");

        TimestampingWorkflow wf = (TimestampingWorkflow) model.workflow();
        Assertions.assertEquals("1.2.3.4.5", wf.defaultPolicyId(),
                "Default policy ID must round-trip through create → model");
        Assertions.assertEquals(List.of("1.2.3.4.5", "1.2.3.4.6"), wf.allowedPolicyIds(),
                "Allowed policy IDs must round-trip through create → model");
        Assertions.assertEquals(List.of(DigestAlgorithm.SHA_256), wf.allowedDigestAlgorithms(),
                "Allowed digest algorithms must round-trip through create → model");
    }

    @Test
    void testGetSigningProfileModel_baseFieldsArePropagatedToModel() throws AttributeException, NotFoundException {
        SigningProfileRequestDto request = buildManagedStaticKeyRawRequest("raw-base-fields");
        request.setDescription("expected description");
        SigningProfileDto created = signingProfileService.createSigningProfile(request);

        SigningProfileModel<?, ?> model = signingProfileService.getSigningProfileModel("raw-base-fields");

        Assertions.assertEquals("raw-base-fields", model.name());
        Assertions.assertEquals("expected description", model.description());
        Assertions.assertEquals(UUID.fromString(created.getUuid()), model.uuid());
        Assertions.assertEquals(1, model.version());
        Assertions.assertFalse(model.enabled(),
                "Newly created profiles are disabled by default");
    }

    @Test
    void testGetSigningProfileModel_withTspLinked_enabledProtocolsContainsTsp() throws AttributeException, NotFoundException {
        signingProfileService.createSigningProfile(buildManagedStaticKeyTimestampingRequest("ts-tsp-linked"));

        SigningProfile entity = signingProfileRepository.findByName("ts-tsp-linked").orElseThrow();
        TspProfile tspProfile = new TspProfile();
        tspProfile.setName("tsp-for-model-test");
        tspProfile = tspRepository.save(tspProfile);
        entity.setTspProfile(tspProfile);
        signingProfileRepository.save(entity);

        SigningProfileModel<?, ?> model = signingProfileService.getSigningProfileModel("ts-tsp-linked");

        Assertions.assertTrue(model.enabledProtocols().contains(SigningProtocol.TSP),
                "TSP should appear in enabledProtocols when a TSP Profile is linked");
    }
}
