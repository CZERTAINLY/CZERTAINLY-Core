package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
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
import com.czertainly.api.model.client.signing.profile.workflow.ContentSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.RawSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowRequestDto;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.api.model.client.signing.profile.workflow.WorkflowRequestDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordListDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.AttributeOperation;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.Audited_;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.SigningProfile_;
import com.czertainly.core.dao.entity.signing.SigningProfileVersion;
import com.czertainly.core.dao.entity.signing.TspProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.signing.SigningRecordRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileVersionRepository;
import com.czertainly.core.dao.repository.signing.TspProfileRepository;
import com.czertainly.core.mapper.signing.SigningProfileMapper;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.CryptographicOperationService;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.model.SecuredList;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.FilterPredicatesBuilder;
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

import java.util.*;

@Service(Resource.Codes.SIGNING_PROFILE)
@Slf4j
public class SigningProfileServiceImpl implements SigningProfileService {
    /**
     * Defines which signing protocols are allowed for each workflow type.
     */
    private static final Map<SigningWorkflowType, Set<SigningProtocol>> SUPPORTED_PROTOCOLS = Map.of(
            SigningWorkflowType.TIMESTAMPING, EnumSet.of(SigningProtocol.TSP)
    );

    private CryptographicOperationService cryptographicOperationService;
    private CertificateRepository certificateRepository;
    private CertificateService certificateService;
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;
    private SigningRecordRepository signingRecordRepository;
    private SigningProfileRepository signingProfileRepository;
    private SigningProfileVersionRepository signingProfileVersionRepository;
    private TspProfileRepository tspRepository;

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
    public List<SigningProtocol> listSupportedProtocols(SigningWorkflowType workflowType) {
        return List.copyOf(SUPPORTED_PROTOCOLS.getOrDefault(workflowType, EnumSet.noneOf(SigningProtocol.class)));
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.LIST)
    @Transactional
    public PaginationResponseDto<SigningProfileListDto> listSigningProfiles(SearchRequestDto request, SecurityFilter filter) {
        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<SigningProfile>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<SigningProfileListDto> profiles = signingProfileRepository.findUsingSecurityFilter(filter, List.of(), predicate, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED)))
                .stream()
                .map(SigningProfileMapper::toListDto)
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
    public SecuredList<SigningProfile> listSigningProfilesAssociatedTimeQualityConfiguration(UUID timeQualityConfigurationUuid, SecurityFilter filter) {
        List<SigningProfile> signingProfiles = signingProfileRepository.findAllByTimeQualityConfigurationUuid(timeQualityConfigurationUuid);
        return SecuredList.fromFilter(filter, signingProfiles);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.LIST)
    @Transactional
    public SecuredList<SigningProfile> listSigningProfilesAssociatedWithTsp(UUID tspProfileUuid, SecurityFilter filter) {
        List<SigningProfile> signingProfiles = signingProfileRepository.findAllByTspProfileUuid(tspProfileUuid);
        return SecuredList.fromFilter(filter, signingProfiles);
    }

    @Override
    public List<CertificateDto> listSigningCertificates(SigningWorkflowType signingWorkflowType, boolean qualifiedTimestamp) {
        return certificateService.listDigitalSigningCertificates(SecurityFilter.create(), signingWorkflowType, qualifiedTimestamp);
    }

    @Override
    public List<BaseAttribute> listSignatureAttributesForCertificate(UUID certificateUuid) throws NotFoundException {
        Certificate certificate = certificateRepository.findByUuid(certificateUuid)
                .orElseThrow(() -> new NotFoundException(Certificate.class, certificateUuid));
        if (certificate.getKey() == null) {
            return List.of();
        }
        return cryptographicKeyItemRepository.findByKeyUuidIn(List.of(certificate.getKey().getUuid()))
                .stream()
                .findFirst()
                .map(item -> cryptographicOperationService.listSignatureAttributes(item.getKeyAlgorithm()))
                .orElse(List.of());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Get (by name)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
//    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public SigningProfileDto getSigningProfile(String name) throws NotFoundException {
        Optional<SigningProfile> signingProfileOptional = signingProfileRepository.findByName(name);
        if (signingProfileOptional.isEmpty()) {
            throw new NotFoundException("Signing profile with name '" + name + "' not found");
        }
        SigningProfile signingProfile = signingProfileOptional.get();
        return buildDtoFromProfile(signingProfile);
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
        } else {
            return buildDtoFromProfile(profile);
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ? extends SigningSchemeModel> getManagedTimestampingProfileModel(String name) throws NotFoundException {
        SigningProfile profile = signingProfileRepository.findWithAssociationsByName(name)
                .orElseThrow(() -> new NotFoundException("Signing Profile not found: " + name));

        if (profile.getWorkflowType() != SigningWorkflowType.TIMESTAMPING) {
            throw new NotFoundException("Signing Profile '%s' is not configured with a timestamping workflow".formatted(name));
        }
        return buildModel(profile, SigningProfileMapper::toManagedTimestampingModel);
    }

    private <T> T buildModel(SigningProfile profile, SigningProfileMapper.SigningProfileModelFactory<T> factory) {
        UUID profileUuid = profile.getUuid();
        List<RequestAttribute> signingOperationAttributes = attributeEngine
                .getRequestObjectDataAttributesContent(ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                        .operation(AttributeOperation.SIGN)
                        .version(profile.getLatestVersion()).build());
        List<RequestAttribute> signatureFormatterConnectorAttributes = attributeEngine
                .getRequestObjectDataAttributesContent(ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                        .connector(profile.getSignatureFormatterConnectorUuid())
                        .operation(AttributeOperation.WORKFLOW_FORMATTER)
                        .version(profile.getLatestVersion()).build());
        return factory.create(profile, signingOperationAttributes, signatureFormatterConnectorAttributes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.CREATE)
    @Transactional
    public SigningProfileDto createSigningProfile(SigningProfileRequestDto request) throws AlreadyExistException, AttributeException, NotFoundException {
        if (signingProfileRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException("Signing Profile with name '" + request.getName() + "' already exists.");
        }
        validateSigningSchemeCoherence(request.getSigningScheme());
        attributeEngine.validateCustomAttributesContent(Resource.SIGNING_PROFILE, request.getCustomAttributes());

        SigningProfile profile = new SigningProfile();
        profile.setName(request.getName());
        profile.setDescription(request.getDescription());
        profile.setLatestVersion(1);
        applyWorkflow(profile, request.getWorkflow());
        applyScheme(profile, request.getSigningScheme());
        profile = signingProfileRepository.save(profile);

        saveVersionSnapshot(profile, 1, request.getSigningScheme(), request.getWorkflow(), false);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.SIGNING_PROFILE, profile.getUuid(), request.getCustomAttributes());
        List<ResponseAttribute> signingOperationAttributes = persistSigningOperationAttributes(profile, request.getSigningScheme());
        List<ResponseAttribute> signatureFormatterConnectorAttributes = persistSignatureFormatterConnectorAttributes(profile, request.getWorkflow());
        return SigningProfileMapper.toDto(profile, customAttributes, signingOperationAttributes, signatureFormatterConnectorAttributes);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update (lenient version bump)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public SigningProfileDto updateSigningProfile(SecuredUUID uuid, SigningProfileRequestDto request) throws AlreadyExistException, AttributeException, NotFoundException {
        validateSigningSchemeCoherence(request.getSigningScheme());
        attributeEngine.validateCustomAttributesContent(Resource.SIGNING_PROFILE, request.getCustomAttributes());

        SigningProfile profile = findByUuid(uuid);

        Optional<SigningProfile> existingWithSameName = signingProfileRepository.findByName(request.getName());
        if (existingWithSameName.isPresent() && !existingWithSameName.get().getUuid().equals(profile.getUuid())) {
            throw new AlreadyExistException("Signing Profile with name '" + request.getName() + "' already exists.");
        }

        profile.setName(request.getName());
        profile.setDescription(request.getDescription());
        applyWorkflow(profile, request.getWorkflow());
        applyScheme(profile, request.getSigningScheme());

        // Lenient version bump: only bump if signatures exist for the current latest version
        boolean bump = signingRecordRepository.existsBySigningProfileUuidAndSigningProfileVersion(profile.getUuid(), profile.getLatestVersion());
        if (bump) {
            profile.setLatestVersion(profile.getLatestVersion() + 1); // :TODO: this is a potential race condition, we should use advisory locking
        }
        profile = signingProfileRepository.save(profile);

        saveVersionSnapshot(profile, profile.getLatestVersion(), request.getSigningScheme(), request.getWorkflow(), !bump);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.SIGNING_PROFILE, profile.getUuid(), request.getCustomAttributes());
        List<ResponseAttribute> signingOperationAttributes = persistSigningOperationAttributes(profile, request.getSigningScheme());
        List<ResponseAttribute> signatureFormatterConnectorAttributes = persistSignatureFormatterConnectorAttributes(profile, request.getWorkflow());
        return SigningProfileMapper.toDto(profile, customAttributes, signingOperationAttributes, signatureFormatterConnectorAttributes);
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
        signingRecordRepository.clearSigningProfileUuid(signingProfile.getUuid());
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
    // Signing records scoped to profile (stub list)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public PaginationResponseDto<SigningRecordListDto> listSigningRecordsForSigningProfile(
            SecuredUUID uuid, SearchRequestDto request, SecurityFilter filter) throws NotFoundException {
        // :TODO:
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Protocol activation — TSP
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    @Transactional
    public TspActivationDetailDto getTspActivationDetails(SecuredUUID uuid) throws NotFoundException {
        SigningProfile signingProfile = findByUuid(uuid);
        return SigningProfileMapper.toTspActivationDto(signingProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public TspActivationDetailDto activateTsp(SecuredUUID signingProfileUuid, SecuredUUID tspProfileUuid) throws NotFoundException {
        SigningProfile signingProfile = findByUuid(signingProfileUuid);
        validateSupportedProtocol(signingProfile.getWorkflowType(), SigningProtocol.TSP);
        TspProfile tspProfile = tspRepository.findByUuid(tspProfileUuid)
                .orElseThrow(() -> new NotFoundException("TSP Profile not found: " + tspProfileUuid));
        signingProfile.setTspProfile(tspProfile);
        signingProfileRepository.save(signingProfile);
        return SigningProfileMapper.toTspActivationDto(signingProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    @Transactional
    public void deactivateTsp(SecuredUUID uuid) throws NotFoundException {
        SigningProfile profile = findByUuid(uuid);
        profile.setTspProfile(null);
        signingProfileRepository.save(profile);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private SigningProfile findByUuid(SecuredUUID uuid) throws NotFoundException {
        return signingProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Signing Profile not found: " + uuid));
    }

    private void validateSupportedProtocol(SigningWorkflowType workflowType, SigningProtocol protocol) {
        Set<SigningProtocol> supported = SUPPORTED_PROTOCOLS.getOrDefault(workflowType, EnumSet.noneOf(SigningProtocol.class));
        if (!supported.contains(protocol)) {
            throw new ValidationException(protocol.getCode() + " is not supported for workflow type " + workflowType.getCode());
        }
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
    private void applyScheme(SigningProfile p, SigningSchemeRequestDto scheme) throws AttributeException, NotFoundException {
        // Delete any previously stored signing-operation attributes before overwriting the scheme.
        if (p.getUuid() != null) {
            attributeEngine.deleteOperationObjectAttributesContent(AttributeType.DATA,
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, p.getUuid())
                            .operation(AttributeOperation.SIGN).build());
        }
        p.setSigningScheme(scheme.getSigningScheme());
        p.setManagedSigningType(null);
        p.setTokenProfile(null);
        p.setCertificate(null);
        p.setRaProfile(null);
        p.setCsrTemplateUuid(null);
        p.setDelegatedSignerConnector(null);

        switch (scheme) {
            case StaticKeyManagedSigningRequestDto s -> {
                p.setManagedSigningType(ManagedSigningType.STATIC_KEY);
                Certificate certificate = certificateRepository.findWithAssociationsByUuid(s.getCertificateUuid())
                        .orElseThrow(() -> new NotFoundException(Certificate.class, s.getCertificateUuid()));
                if (CertificateUtil.isCertificateDigitalSigningAcceptable(certificate, p.getWorkflowType(), Boolean.TRUE.equals(p.getQualifiedTimestamp()))) {
                    p.setCertificate(certificate);
                } else {
                    throw new ValidationException("Certificate " + certificate.getUuid() + " is not eligible for signing workflow type " + p.getWorkflowType());
                }
            }
            case OneTimeKeyManagedSigningRequestDto s -> {
                p.setManagedSigningType(ManagedSigningType.ONE_TIME_KEY);
                p.setTokenProfileUuid(s.getTokenProfileUuid());
                p.setRaProfileUuid(s.getRaProfileUuid());
                p.setCsrTemplateUuid(s.getCsrTemplateUuid());
            }
            case DelegatedSigningRequestDto s -> {
                p.setDelegatedSignerConnectorUuid(s.getConnectorUuid());
            }
            default ->
                    throw new IllegalStateException("Unexpected type for Signing Scheme: " + scheme.getSigningScheme());
        }
    }

    /**
     * Maps request workflow DTO → flat entity columns. Clears previous values first.
     */
    private void applyWorkflow(SigningProfile p, WorkflowRequestDto workflow) throws AttributeException, NotFoundException {
        // Delete any previously stored workflow-formatter attributes before overwriting the workflow.
        if (p.getUuid() != null && p.getSignatureFormatterConnectorUuid() != null) {
            attributeEngine.deleteOperationObjectAttributesContent(AttributeType.DATA,
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, p.getUuid())
                            .connector(p.getSignatureFormatterConnectorUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER).build());
        }
        p.setWorkflowType(workflow.getType());
        p.setSignatureFormatterConnector(null);
        p.setQualifiedTimestamp(null);
        p.setTimeQualityConfiguration(null);
        p.setDefaultPolicyId(null);
        p.setAllowedPolicyIds(new ArrayList<>());
        p.setAllowedDigestAlgorithms(new ArrayList<>());

        switch (workflow) {
            case ContentSigningWorkflowRequestDto w ->
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
            applyWorkflow(tmp, workflowReq);
            applyScheme(tmp, schemeReq);

            List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.SIGNING_PROFILE, live.getUuid());
            List<ResponseAttribute> signingOperationAttributes = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, live.getUuid())
                            .operation(AttributeOperation.SIGN)
                            .version(spv.getVersion()).build());
            List<ResponseAttribute> signatureFormatterConnectorAttributes = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, live.getUuid())
                            .connector(tmp.getSignatureFormatterConnectorUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER)
                            .version(spv.getVersion()).build());
            return SigningProfileMapper.toDto(tmp, customAttributes, signingOperationAttributes, signatureFormatterConnectorAttributes);
        } catch (Exception e) {
            log.error("Failed to load signing profile snapshot v{} for {}: {}",
                    spv.getVersion(), live.getUuid(), e.getMessage());
            throw new IllegalStateException("Cannot deserialize signing profile version snapshot", e);
        }
    }

    private SigningProfileDto buildDtoFromProfile(SigningProfile profile) {
        List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.SIGNING_PROFILE, profile.getUuid());
        List<ResponseAttribute> signingOperationAttributes = attributeEngine.getObjectDataAttributesContent(
                ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profile.getUuid())
                        .operation(AttributeOperation.SIGN)
                        .version(profile.getLatestVersion()).build());
        List<ResponseAttribute> signatureFormatterConnectorAttributes = attributeEngine.getObjectDataAttributesContent(
                ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profile.getUuid())
                        .connector(profile.getSignatureFormatterConnectorUuid())
                        .operation(AttributeOperation.WORKFLOW_FORMATTER)
                        .version(profile.getLatestVersion()).build());
        return SigningProfileMapper.toDto(profile, customAttributes, signingOperationAttributes, signatureFormatterConnectorAttributes);
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

    private List<ResponseAttribute> persistSigningOperationAttributes(SigningProfile signingProfile, SigningSchemeRequestDto signingScheme) throws AttributeException, NotFoundException {
        if (signingScheme instanceof StaticKeyManagedSigningRequestDto staticKeyScheme) {
            List<RequestAttribute> signingOperationAttributes = staticKeyScheme.getSigningOperationAttributes();
            List<BaseAttribute> definitions = cryptographicKeyItemRepository.findByKeyUuidIn(List.of(signingProfile.getCertificate().getKey().getUuid()))
                    .stream()
                    .findFirst()
                    .map(item -> cryptographicOperationService.listSignatureAttributes(item.getKeyAlgorithm()))
                    .orElse(List.of());

            // The signing operation attributes are Core-internal (not connector-owned), so connectorUuid is null.
            attributeEngine.validateUpdateDataAttributes(null, AttributeOperation.SIGN, definitions, signingOperationAttributes);
            return attributeEngine.replaceVersionedOperationAttributeContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, signingProfile.getUuid())
                            .operation(AttributeOperation.SIGN)
                            .version(signingProfile.getLatestVersion()).build(),
                    signingOperationAttributes);
        }
        // For non-STATIC_KEY schemes there are no signing-op attributes; clean up any that may
        // remain for the current version (e.g. when switching scheme type in an in-place update).
        attributeEngine.deleteOperationObjectAttributesContent(AttributeType.DATA,
                ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, signingProfile.getUuid())
                        .operation(AttributeOperation.SIGN)
                        .version(signingProfile.getLatestVersion()).build());
        return null;
    }

    private List<ResponseAttribute> persistSignatureFormatterConnectorAttributes(SigningProfile p, WorkflowRequestDto workflow) throws AttributeException, NotFoundException {
        return switch (workflow) {
            case ContentSigningWorkflowRequestDto w -> attributeEngine.replaceVersionedOperationAttributeContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, p.getUuid())
                            .connector(w.getSignatureFormatterConnectorUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER)
                            .version(p.getLatestVersion()).build(),
                    w.getSignatureFormatterConnectorAttributes());
            case RawSigningWorkflowRequestDto w -> {
                // Raw signing has no formatter; clean up any formatter attrs that may remain for this version.
                attributeEngine.deleteOperationObjectAttributesContent(AttributeType.DATA,
                        ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, p.getUuid())
                                .operation(AttributeOperation.WORKFLOW_FORMATTER)
                                .version(p.getLatestVersion()).build());
                yield null;
            }
            case TimestampingWorkflowRequestDto w -> attributeEngine.replaceVersionedOperationAttributeContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, p.getUuid())
                            .connector(w.getSignatureFormatterConnectorUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER)
                            .version(p.getLatestVersion()).build(),
                    w.getSignatureFormatterConnectorAttributes());
            default -> throw new IllegalStateException("Unexpected type for Signing Workflow: " + workflow);
        };
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
// ResourceExtensionService
// ──────────────────────────────────────────────────────────────────────────

    @Override
    public NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException {
        return signingProfileRepository.findResourceObject(objectUuid, SigningProfile_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.DETAIL)
    public NameAndUuidDto getResourceObjectExternal(SecuredUUID objectUuid) throws NotFoundException {
        return signingProfileRepository.findResourceObject(objectUuid.getValue(), SigningProfile_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) {
        return signingProfileRepository.listResourceObjects(filter, SigningProfile_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SIGNING_PROFILE, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        findByUuid(uuid);
    }

// ──────────────────────────────────────────────────────────────────────────
// Dependencies
// ──────────────────────────────────────────────────────────────────────────

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setCryptographicOperationService(CryptographicOperationService cryptographicOperationService) {
        this.cryptographicOperationService = cryptographicOperationService;
    }

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setCryptographicKeyItemRepository(CryptographicKeyItemRepository cryptographicKeyItemRepository) {
        this.cryptographicKeyItemRepository = cryptographicKeyItemRepository;
    }

    @Autowired
    public void setSigningRecordRepository(SigningRecordRepository signingRecordRepository) {
        this.signingRecordRepository = signingRecordRepository;
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
    public void setTspRepository(TspProfileRepository tspProfileRepository) {
        this.tspRepository = tspProfileRepository;
    }
}
