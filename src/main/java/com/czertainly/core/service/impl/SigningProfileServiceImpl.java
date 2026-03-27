package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.DelegatedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningRequestSchemeInterface;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.api.model.client.signing.profile.scheme.OneTimeKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.scheme.SigningSchemeRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.CodeBinarySigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.DocumentSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.RawSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.WorkflowRequestDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolActivationDetailDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Audited_;
import com.czertainly.core.dao.entity.signing.IlmSigningProtocolConfiguration;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.SigningProfileVersion;
import com.czertainly.core.dao.entity.signing.TspConfiguration;
import com.czertainly.core.dao.repository.signing.DigitalSignatureRepository;
import com.czertainly.core.dao.repository.signing.IlmSigningProtocolConfigurationRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileVersionRepository;
import com.czertainly.core.dao.repository.signing.TspConfigurationRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.model.SecuredList;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.ValidatorUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SigningProfileServiceImpl implements SigningProfileService {
    private DigitalSignatureRepository digitalSignatureRepository;
    private IlmSigningProtocolConfigurationRepository ilmSigningProtocolRepository;
    private SigningProfileRepository signingProfileRepository;
    private SigningProfileVersionRepository signingProfileVersionRepository;
    private TspConfigurationRepository tspRepository;

    private AttributeEngine attributeEngine;
    private ObjectMapper objectMapper;


    // ──────────────────────────────────────────────────────────────────────────
    // List / search
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.LIST)
    @Transactional
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.LIST)
    @Transactional
    public PaginationResponseDto<SigningProfileListDto> listSigningProfiles(SearchRequestDto request, SecurityFilter filter) {
        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<SigningProfile>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<SigningProfileListDto> profiles = signingProfileRepository.findUsingSecurityFilter(filter, List.of(), predicate, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED)))
                .stream()
                .map(SigningProfile::mapToListDto)
                .toList();
        PaginationResponseDto<SigningProfileListDto> response = new PaginationResponseDto<>();
        response.setItems(profiles);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        response.setTotalItems(signingProfileRepository.countUsingSecurityFilter(filter, predicate));
        response.setTotalPages((int) Math.ceil((double) response.getTotalItems() / request.getItemsPerPage()));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.LIST)
    @Transactional
    public SecuredList<SigningProfile> listSigningProfilesAssociatedWithIlmSigningProtocol(UUID ilmSigningProtocolConfigurationUuid, SecurityFilter filter) {
        List<SigningProfile> signingProfiles = signingProfileRepository.findAllByIlmSigningProtocolConfigurationUuid(ilmSigningProtocolConfigurationUuid);
        return SecuredList.fromFilter(filter, signingProfiles);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.LIST)
    @Transactional
    public SecuredList<SigningProfile> listSigningProfilesAssociatedWithTsp(UUID tspConfigurationUuid, SecurityFilter filter) {
        List<SigningProfile> signingProfiles = signingProfileRepository.findAllByTspConfigurationUuid(tspConfigurationUuid);
        return SecuredList.fromFilter(filter, signingProfiles);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Get (with optional version)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public SigningProfileDto getSigningProfile(SecuredUUID uuid, Integer version) throws NotFoundException {
        SigningProfile profile = findByUuid(uuid);
        if (version != null) {
            SigningProfileVersion spv = signingProfileVersionRepository
                    .findBySigningProfileUuidAndVersion(profile.getUuid(), version)
                    .orElseThrow(() -> new NotFoundException("Signing Profile version " + version + " not found"));
            return buildDtoFromSnapshot(profile, spv);
        }

        List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.SIGNING_PROFILE, uuid.getValue());
        return profile.mapToDto(customAttributes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.CREATE)
    @Transactional
    public SigningProfileDto createSigningProfile(SigningProfileRequestDto request) throws AttributeException, NotFoundException {
        validateSigningSchemeCoherence(request.getSigningScheme());
        if (ValidatorUtil.containsUnreservedCharacters(request.getName())) {
            throw new ValidationException(ValidationError.create("Name can contain only unreserved URI characters (alphanumeric, hyphen, period, underscore, and tilde)"));
        }
        attributeEngine.validateCustomAttributesContent(Resource.SIGNING_PROFILE, request.getCustomAttributes());

        SigningProfile profile = new SigningProfile();
        profile.setName(request.getName());
        profile.setDescription(request.getDescription());
        profile.setEnabled(request.getEnabled() != null ? request.getEnabled() : false);
        profile.setLatestVersion(1);
        applyScheme(profile, request.getSigningScheme());
        applyWorkflow(profile, request.getWorkflow());
        profile = signingProfileRepository.save(profile);

        saveVersionSnapshot(profile, 1, request.getSigningScheme(), request.getWorkflow(), false);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.SIGNING_PROFILE, profile.getUuid(), request.getCustomAttributes());
        return profile.mapToDto(customAttributes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update (lenient version bump)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public SigningProfileDto updateSigningProfile(SecuredUUID uuid, SigningProfileRequestDto request) throws NotFoundException, AttributeException {
        validateSigningSchemeCoherence(request.getSigningScheme());
        if (ValidatorUtil.containsUnreservedCharacters(request.getName())) {
            throw new ValidationException(ValidationError.create("Name can contain only unreserved URI characters (alphanumeric, hyphen, period, underscore, and tilde)"));
        }
        attributeEngine.validateCustomAttributesContent(Resource.SIGNING_PROFILE, request.getCustomAttributes());

        SigningProfile profile = findByUuid(uuid);
        profile.setName(request.getName());
        profile.setDescription(request.getDescription());
        profile.setEnabled(request.getEnabled());
        applyScheme(profile, request.getSigningScheme());
        applyWorkflow(profile, request.getWorkflow());

        // Lenient version bump: only bump if signatures exist for the current latest version
        boolean bump = digitalSignatureRepository.existsBySigningProfileUuidAndSigningProfileVersion(profile.getUuid(), profile.getLatestVersion());
        if (bump) {
            profile.setLatestVersion(profile.getLatestVersion() + 1); // :TODO: this is a potential race condition, we should use advisory locking
        }
        profile = signingProfileRepository.save(profile);

        saveVersionSnapshot(profile, profile.getLatestVersion(), request.getSigningScheme(), request.getWorkflow(), !bump);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.SIGNING_PROFILE, profile.getUuid(), request.getCustomAttributes());
        return profile.mapToDto(customAttributes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DELETE)
    @Transactional
    public void deleteSigningProfile(SecuredUUID uuid) throws NotFoundException {
        SigningProfile profile = findByUuid(uuid);
        deleteSigningProfile(profile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DELETE)
    @Transactional
    public List<BulkActionMessageDto> bulkDeleteSigningProfiles(List<SecuredUUID> uuids) {
        return bulkAction(uuids, this::deleteSigningProfile);
    }

    private void deleteSigningProfile(SigningProfile signingProfile) {
        digitalSignatureRepository.clearSigningProfileUuid(signingProfile.getUuid());
        ilmSigningProtocolRepository.clearDefaultSigningProfileUuid(signingProfile.getUuid());
        tspRepository.clearDefaultSigningProfileUuid(signingProfile.getUuid());
        signingProfileVersionRepository.deleteAllBySigningProfileUuid(signingProfile.getUuid());
        signingProfileRepository.delete(signingProfile);
        attributeEngine.deleteAllObjectAttributeContent(Resource.SIGNING_PROFILE, signingProfile.getUuid());
    }


    // ──────────────────────────────────────────────────────────────────────────
    // Enable / disable
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.ENABLE)
    @Transactional
    public void enableSigningProfile(SecuredUUID uuid) throws NotFoundException {
        SigningProfile p = findByUuid(uuid);
        p.setEnabled(true);
        signingProfileRepository.save(p);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.ENABLE)
    @Transactional
    public List<BulkActionMessageDto> bulkEnableSigningProfiles(List<SecuredUUID> uuids) {
        return bulkAction(uuids, this::enableSigningProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.ENABLE)
    @Transactional
    public void disableSigningProfile(SecuredUUID uuid) throws NotFoundException {
        SigningProfile p = findByUuid(uuid);
        p.setEnabled(false);
        signingProfileRepository.save(p);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.ENABLE)
    @Transactional
    public List<BulkActionMessageDto> bulkDisableSigningProfiles(List<SecuredUUID> uuids) {
        return bulkAction(uuids, this::disableSigningProfile);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Approval profile association (stubs)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public List<ApprovalProfileDto> getAssociatedApprovalProfiles(SecuredUUID uuid) throws NotFoundException {
        findByUuid(uuid);
        // :TODO:
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public void associateWithApprovalProfile(SecuredUUID signingProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException {
        // :TODO:
        throw new UnsupportedOperationException("Approval profile association not yet implemented");
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public void disassociateFromApprovalProfile(SecuredUUID signingProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException {
        // :TODO:
        throw new UnsupportedOperationException("Approval profile disassociation not yet implemented");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Digital signatures scoped to profile (stub list)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public PaginationResponseDto<DigitalSignatureListDto> listDigitalSignaturesForSigningProfile(
            SecuredUUID uuid, SearchRequestDto request, SecurityFilter filter) throws NotFoundException {
        // :TODO:
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Protocol activation — ILM Signing Protocol
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public IlmSigningProtocolActivationDetailDto getIlmSigningProtocolActivationDetails(SecuredUUID uuid) throws NotFoundException {
        SigningProfile profile = findByUuid(uuid);
        IlmSigningProtocolActivationDetailDto dto = new IlmSigningProtocolActivationDetailDto();
        // :TODO:
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public IlmSigningProtocolActivationDetailDto activateIlmSigningProtocol(SecuredUUID signingProfileUuid, SecuredUUID ilmConfigUuid) throws NotFoundException {
        SigningProfile profile = findByUuid(signingProfileUuid);
        IlmSigningProtocolConfiguration ilmConfig = ilmSigningProtocolRepository.findByUuid(ilmConfigUuid)
                .orElseThrow(() -> new NotFoundException("ILM Signing Protocol Configuration not found: " + ilmConfigUuid));
        profile.setIlmSigningProtocolConfiguration(ilmConfig);
        signingProfileRepository.save(profile);
        IlmSigningProtocolActivationDetailDto dto = new IlmSigningProtocolActivationDetailDto();
        // :TODO:
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public void deactivateIlmSigningProtocol(SecuredUUID uuid) throws NotFoundException {
        SigningProfile profile = findByUuid(uuid);
        profile.setIlmSigningProtocolConfiguration(null);
        signingProfileRepository.save(profile);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Protocol activation — TSP
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public TspActivationDetailDto getTspActivationDetails(SecuredUUID uuid) throws NotFoundException {
        SigningProfile profile = findByUuid(uuid);
        TspActivationDetailDto dto = new TspActivationDetailDto();
        // :TODO:
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public TspActivationDetailDto activateTsp(SecuredUUID signingProfileUuid, SecuredUUID tspConfigUuid) throws
            NotFoundException {
        SigningProfile profile = findByUuid(signingProfileUuid);
        TspConfiguration tspConfig = tspRepository.findByUuid(tspConfigUuid)
                .orElseThrow(() -> new NotFoundException("TSP Configuration not found: " + tspConfigUuid));
        profile.setTspConfiguration(tspConfig);
        signingProfileRepository.save(profile);
        TspActivationDetailDto dto = new TspActivationDetailDto();
        // :TODO:
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public void deactivateTsp(SecuredUUID uuid) throws NotFoundException {
        SigningProfile profile = findByUuid(uuid);
        profile.setTspConfiguration(null);
        signingProfileRepository.save(profile);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private SigningProfile findByUuid(SecuredUUID uuid) throws NotFoundException {
        return signingProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Signing Profile not found: " + uuid));
    }

    private void validateSigningSchemeCoherence(SigningSchemeRequestDto scheme) {
        if (scheme.getSigningScheme() == SigningScheme.MANAGED
                && !(scheme instanceof ManagedSigningRequestSchemeInterface)) {
            throw new ValidationException("MANAGED signing scheme must specify managedSigningType");
        }
        if (scheme.getSigningScheme() == SigningScheme.DELEGATED
                && scheme instanceof ManagedSigningRequestSchemeInterface) {
            throw new ValidationException("DELEGATED signing scheme must not have managedSigningType");
        }
    }

    /**
     * Maps request scheme DTO → flat entity columns. Clears previous values first.
     */
    private void applyScheme(SigningProfile p, SigningSchemeRequestDto scheme) {
        // :TODO: remove the previous connector attributes as well
        p.setSigningScheme(scheme.getSigningScheme());
        p.setManagedSigningType(null);
        p.setTokenProfile(null);
        p.setCryptographicKey(null);
        p.setRaProfile(null);
        p.setCsrTemplateUuid(null);
        p.setDelegatedSignerConnector(null);

        switch (scheme) {
            case StaticKeyManagedSigningRequestDto s -> {
                p.setManagedSigningType(ManagedSigningType.STATIC_KEY);
                p.setTokenProfileUuid(s.getTokenProfileUuid());
                p.setCryptographicKeyUuid(s.getCryptographicKeyUuid());
            }
            case OneTimeKeyManagedSigningRequestDto s -> {
                p.setManagedSigningType(ManagedSigningType.ONE_TIME_KEY);
                p.setTokenProfileUuid(s.getTokenProfileUuid());
                p.setRaProfileUuid(s.getRaProfileUuid());
                p.setCsrTemplateUuid(s.getCsrTemplateUuid());
            }
            case DelegatedSigningRequestDto s -> p.setDelegatedSignerConnectorUuid(s.getConnectorUuid());
            default -> {
            }
        }
    }

    /**
     * Maps request workflow DTO → flat entity columns. Clears previous values first.
     */
    private void applyWorkflow(SigningProfile p, WorkflowRequestDto workflow) {
        // :TODO: remove the previous connector attributes as well
        p.setWorkflowType(workflow.getType());
        p.setSignatureFormatterConnector(null);
        p.setQualifiedTimestamp(null);
        p.setTimeQualityConfiguration(null);
        p.setDefaultPolicyId(null);
        p.setAllowedPolicyIds(new ArrayList<>());
        p.setAllowedDigestAlgorithms(new ArrayList<>());

        switch (workflow) {
            case CodeBinarySigningWorkflowRequestDto w ->
                    p.setSignatureFormatterConnectorUuid(w.getSignatureFormatterConnectorUuid());
            case DocumentSigningWorkflowRequestDto w ->
                    p.setSignatureFormatterConnectorUuid(w.getSignatureFormatterConnectorUuid());
            case RawSigningWorkflowRequestDto w -> {
                // no formatter for raw signing
            }
            case TimestampingWorkflowRequestDto w -> {
                p.setSignatureFormatterConnectorUuid(w.getSignatureFormatterConnectorUuid());
                p.setQualifiedTimestamp(w.getQualifiedTimestamp());
                p.setTimeQualityConfigurationUuid(w.getTimeQualityConfigurationUuid());
                p.setDefaultPolicyId(w.getDefaultPolicyId());
                p.setAllowedPolicyIds(w.getAllowedPolicyIds() != null ? w.getAllowedPolicyIds() : new ArrayList<>());
                if (w.getAllowedDigestAlgorithms() != null) {
                    p.setAllowedDigestAlgorithms(w.getAllowedDigestAlgorithms().stream().map(DigestAlgorithm::getCode).toList());
                }
            }
            default -> throw new IllegalStateException("Unexpected type for Signing Workflow: " + workflow);
        }
    }

    /**
     * Saves (or overwrites) a SigningProfileVersion snapshot.
     *
     * @param overwrite if true, updates the existing row; if false, inserts a new row
     */
    private void saveVersionSnapshot(SigningProfile signingProfile, int version,
                                     SigningSchemeRequestDto scheme, WorkflowRequestDto workflow, boolean overwrite) {
        String schemeJson = toJson(scheme);
        String workflowJson = toJson(workflow);

        // :TODO: get attributes from AttributeEngine and save them as well
        if (overwrite) {
            signingProfileVersionRepository
                    .findBySigningProfileUuidAndVersion(signingProfile.getUuid(), version)
                    .ifPresentOrElse(spv -> {
                        spv.setSchemeSnapshot(schemeJson);
                        spv.setWorkflowSnapshot(workflowJson);
                        signingProfileVersionRepository.save(spv);
                    }, () -> insertSnapshot(signingProfile, version, schemeJson, workflowJson));
        } else {
            insertSnapshot(signingProfile, version, schemeJson, workflowJson);
        }
    }

    private void insertSnapshot(SigningProfile signingProfile, int version, String schemeJson, String
            workflowJson) {
        SigningProfileVersion spv = new SigningProfileVersion();
        spv.setSigningProfile(signingProfile);
        spv.setVersion(version);
        spv.setSchemeSnapshot(schemeJson);
        spv.setWorkflowSnapshot(workflowJson);
        signingProfileVersionRepository.save(spv);
    }

    /**
     * Reconstructs a SigningProfileDto from a version snapshot.
     */
    private SigningProfileDto buildDtoFromSnapshot(SigningProfile live, SigningProfileVersion spv) {
        try {
            SigningSchemeRequestDto schemeReq = objectMapper.readValue(spv.getSchemeSnapshot(), SigningSchemeRequestDto.class);
            WorkflowRequestDto workflowReq = objectMapper.readValue(spv.getWorkflowSnapshot(), WorkflowRequestDto.class);

            SigningProfile tmp = new SigningProfile();
            tmp.setUuid(live.getUuid());
            tmp.setName(live.getName());
            tmp.setDescription(live.getDescription());
            tmp.setEnabled(live.getEnabled());
            tmp.setLatestVersion(spv.getVersion());
            applyScheme(tmp, schemeReq);
            applyWorkflow(tmp, workflowReq);

            List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.SIGNING_PROFILE, live.getUuid());
            return tmp.mapToDto(customAttributes);
        } catch (Exception e) {
            log.error("Failed to load signing profile snapshot v{} for {}: {}",
                    spv.getVersion(), live.getUuid(), e.getMessage());
            throw new IllegalStateException("Cannot deserialize signing profile version snapshot", e);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise snapshot object: {}", e.getMessage());
            return "{}";
        }
    }

    @FunctionalInterface
    private interface CheckedConsumer<T> {
        void accept(T t) throws NotFoundException;
    }

    private List<BulkActionMessageDto> bulkAction(List<SecuredUUID> uuids, CheckedConsumer<SecuredUUID> action) {
        List<BulkActionMessageDto> results = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            try {
                action.accept(uuid);
            } catch (NotFoundException e) {
                BulkActionMessageDto message = new BulkActionMessageDto();
                message.setUuid(uuid.getValue().toString());
                // :TODO: Message needs to be more descriptive (action, entity name)
                message.setMessage(e.getMessage());
                results.add(message);
            }
        }
        return results;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Dependencies
    // ──────────────────────────────────────────────────────────────────────────

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setDigitalSignatureRepository(DigitalSignatureRepository digitalSignatureRepository) {
        this.digitalSignatureRepository = digitalSignatureRepository;
    }

    @Autowired
    public void setIlmSigningProtocolRepository(IlmSigningProtocolConfigurationRepository ilmSigningProtocolConfigurationRepository) {
        this.ilmSigningProtocolRepository = ilmSigningProtocolConfigurationRepository;
    }
    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setSigningProfileRepository(SigningProfileRepository signingProfileRepository) {
        this.signingProfileRepository = signingProfileRepository;
    }

    @Autowired
    public void setSigningProfileVersionRepository(SigningProfileVersionRepository signingProfileVersionRepository) {
        this.signingProfileVersionRepository = signingProfileVersionRepository;
    }

    @Autowired
    public void setTspRepository(TspConfigurationRepository tspConfigurationRepository) {
        this.tspRepository = tspConfigurationRepository;
    }
}
