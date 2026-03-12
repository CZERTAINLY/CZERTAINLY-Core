package com.czertainly.core.service.impl;

import com.czertainly.api.clients.secret.SecretApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.v3.content.data.ResourceObjectContentData;
import com.czertainly.api.model.common.attribute.v3.content.data.ResourceSecretContentData;
import com.czertainly.api.model.common.error.ErrorCode;
import com.czertainly.api.model.connector.secrets.*;
import com.czertainly.api.model.connector.secrets.content.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.v2.ConnectorDetailDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.secret.*;
import com.czertainly.api.model.core.secret.SecretRequestDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.ConnectorRequestAttributesBuilder;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.service.v2.ConnectorService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.SearchHelper;
import com.czertainly.core.util.SecretsUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service(value = Resource.Codes.SECRET)
@Transactional
public class SecretServiceImpl implements SecretService, AttributeResourceService {


    private static final Logger logger = LoggerFactory.getLogger(SecretServiceImpl.class);

    private AttributeEngine attributeEngine;
    private ConnectorRequestAttributesBuilder connectorRequestAttributesBuilder;
    private PermissionEvaluator permissionEvaluator;

    private VaultProfileRepository vaultProfileRepository;
    private VaultInstanceRepository vaultInstanceRepository;
    private SecretRepository secretRepository;
    private SecretVersionRepository secretVersionRepository;
    private Secret2SyncVaultProfileRepository secret2SyncVaultProfileRepository;

    private ResourceObjectAssociationService objectAssociationService;
    private ConnectorService connectorService;
    private VaultProfileService vaultProfileService;
    private VaultInstanceService vaultInstanceService;

    private SecretApiClient secretApiClient;

    @Autowired
    public void setVaultInstanceService(VaultInstanceService vaultInstanceService) {
        this.vaultInstanceService = vaultInstanceService;
    }

    @Autowired
    public void setSecret2SyncVaultProfileRepository(Secret2SyncVaultProfileRepository secret2SyncVaultProfileRepository) {
        this.secret2SyncVaultProfileRepository = secret2SyncVaultProfileRepository;
    }

    @Autowired
    public void setConnectorRequestAttributesBuilder(ConnectorRequestAttributesBuilder connectorRequestAttributesBuilder) {
        this.connectorRequestAttributesBuilder = connectorRequestAttributesBuilder;
    }

    @Autowired
    public void setVaultProfileService(VaultProfileService vaultProfileService) {
        this.vaultProfileService = vaultProfileService;
    }

    @Autowired
    public void setSecretApiClient(SecretApiClient secretApiClient) {
        this.secretApiClient = secretApiClient;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setSecretVersionRepository(SecretVersionRepository secretVersionRepository) {
        this.secretVersionRepository = secretVersionRepository;
    }

    @Autowired
    public void setPermissionEvaluator(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Autowired
    public void setObjectAssociationService(ResourceObjectAssociationService objectAssociationService) {
        this.objectAssociationService = objectAssociationService;
    }

    @Autowired
    public void setSecretRepository(SecretRepository secretRepository) {
        this.secretRepository = secretRepository;
    }

    @Autowired
    public void setVaultProfileRepository(VaultProfileRepository vaultProfileRepository) {
        this.vaultProfileRepository = vaultProfileRepository;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setVaultInstanceRepository(VaultInstanceRepository vaultInstanceRepository) {
        this.vaultInstanceRepository = vaultInstanceRepository;
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(Resource.SECRET, false);
        List<SearchFieldDataDto> fieldDataDtos = new ArrayList<>(List.of(
                SearchHelper.prepareSearch(FilterField.SECRET_NAME),
                SearchHelper.prepareSearch(FilterField.SECRET_TYPE, Arrays.stream(com.czertainly.api.model.connector.secrets.SecretType.values()).map(com.czertainly.api.model.connector.secrets.SecretType::getCode).toList()),
                SearchHelper.prepareSearch(FilterField.SECRET_STATE, Arrays.stream(SecretState.values()).map(SecretState::getCode).toList()),
                SearchHelper.prepareSearch(FilterField.SECRET_ENABLED),
                SearchHelper.prepareSearch(FilterField.SECRET_SOURCE_VAULT_PROFILE, vaultProfileRepository.findAllNames()),
                SearchHelper.prepareSearch(FilterField.SECRET_SYNC_VAULT_PROFILE, vaultProfileRepository.findAllNames())
        ));

        fieldDataDtos.sort(new SearchFieldDataComparator());
        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fieldDataDtos, FilterFieldSource.PROPERTY));
        return searchFieldDataByGroupDtos;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.LIST, parentResource = Resource.VAULT_PROFILE, parentAction = ResourceAction.MEMBERS)
    public PaginationResponseDto<SecretDto> listSecrets(SearchRequestDto searchRequest, SecurityFilter securityFilter) {
        TriFunction<Root<Secret>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, searchRequest.getFilters());
        Pageable p = PageRequest.of(searchRequest.getPageNumber() - 1, searchRequest.getItemsPerPage());
        List<Secret> secrets = getSecrets(securityFilter, p, additionalWhereClause);
        List<SecretDto> secretDtos = secrets.stream().map(Secret::mapToDto).toList();
        PaginationResponseDto<SecretDto> response = new PaginationResponseDto<>();
        response.setItems(secretDtos);
        response.setPageNumber(searchRequest.getPageNumber());
        response.setItemsPerPage(searchRequest.getItemsPerPage());
        response.setTotalItems(secretRepository.countUsingSecurityFilter(securityFilter, additionalWhereClause));
        response.setTotalPages((int) Math.ceil((double) response.getTotalItems() / searchRequest.getItemsPerPage()));
        return response;
    }

    private List<Secret> getSecrets(SecurityFilter securityFilter, Pageable p, TriFunction<Root<Secret>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause) {
        securityFilter.setParentRefProperty(Secret_.SOURCE_VAULT_PROFILE_UUID);
        return secretRepository.findUsingSecurityFilter(securityFilter, List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED))).stream().toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.CREATE, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public SecretDetailDto createSecret(SecretRequestDto secretRequest, SecuredParentUUID sourceVaultProfileUuid, SecuredUUID vaultInstanceUuid) throws NotFoundException, AttributeException, AlreadyExistException, ConnectorException {
        if (secretRepository.existsByName(secretRequest.getName())) {
            throw new AlreadyExistException("Secret with name '" + secretRequest.getName() + "' already exists");
        }

        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(sourceVaultProfileUuid)
                .orElseThrow(() -> new NotFoundException(VaultProfile.class, sourceVaultProfileUuid.toString()));

        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(vaultInstanceUuid)
                .orElseThrow(() -> new NotFoundException(VaultInstance.class, vaultInstanceUuid.toString()));

        SecretResponseDto secretResponseDto = createSecretInVault(vaultInstance.getConnectorUuid(), vaultProfile.getVaultInstanceUuid(), secretRequest.getSecret().getType(), vaultProfile.getUuid(), secretRequest);

        attributeEngine.validateCustomAttributesContent(Resource.SECRET, secretRequest.getCustomAttributes());

        Secret secret = new Secret();
        secret.setName(secretRequest.getName());
        secret.setDescription(secretRequest.getDescription());
        secret.setSourceVaultProfile(vaultProfile);
        secret.setState(SecretState.ACTIVE);
        secret.setType(secretRequest.getSecret().getType());

        SecretVersion secretVersion = new SecretVersion();
        secretVersion.setSecret(secret);
        secretVersion.setVersion(1);
        String fingerprint;
        try {
            fingerprint = SecretsUtil.calculateSecretContentFingerprint(secretRequest.getSecret());
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new ValidationException("Unable to calculate secret fingerprint: " + e.getMessage());
        }

        secretVersion.setFingerprint(fingerprint);
        secretVersion.setVaultInstance(vaultInstance);
        secretVersion.setVaultVersion(secretResponseDto.getVersion());
        secretVersionRepository.save(secretVersion);

        secret.setLatestVersion(secretVersion);
        secret.getVersions().add(secretVersion);
        secretRepository.save(secret);

        secretVersion.setSecret(secret);
        secretVersionRepository.save(secretVersion);

        objectAssociationService.setOwnerFromProfile(Resource.SECRET, secret.getUuid());
        attributeEngine.updateMetadataAttributes(secretResponseDto.getMetadata(), new ObjectAttributeContentInfo(vaultInstance.getConnectorUuid(), Resource.SECRET, secret.getUuid()));

        SecretDetailDto secretDetailDto = secret.mapToDetailDto();
        secretDetailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.SECRET, secret.getUuid(), secretRequest.getCustomAttributes()));
        secretDetailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.SECRET, secret.getUuid(), secretRequest.getAttributes()));
        secretDetailDto.setMetadata(attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.SECRET, secret.getUuid())));

        return secretDetailDto;
    }

    private SecretResponseDto createSecretInVault(UUID connectorUuid, UUID vaultUuid, SecretType type, UUID vaultProfileUuid, SecretRequestDto secretRequest) throws ConnectorException, NotFoundException, AttributeException {
        ConnectorDetailDto connectorDetailDto = connectorService.getConnector(SecuredUUID.fromUUID(connectorUuid));
        List<BaseAttribute> createSecretAttributes = vaultProfileService.getAttributesForCreatingSecret(SecuredParentUUID.fromUUID(vaultUuid), SecuredUUID.fromUUID(vaultProfileUuid), type);
        List<RequestAttribute> requestAttributes = connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(connectorUuid, createSecretAttributes, secretRequest.getAttributes());
        CreateSecretRequestDto createSecretRequestDto = new CreateSecretRequestDto();
        createSecretRequestDto.setName(secretRequest.getName());
        createSecretRequestDto.setSecret(secretRequest.getSecret());
        List<BaseAttribute> vaultAttributes = vaultInstanceService.listVaultInstanceAttributes(connectorUuid);
        createSecretRequestDto.setVaultAttributes(connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(connectorUuid, vaultAttributes, attributeEngine.getRequestObjectDataAttributesContent(connectorUuid, null, Resource.VAULT, vaultUuid)));
        createSecretRequestDto.setSecretAttributes(requestAttributes);

        return secretApiClient.createSecret(connectorDetailDto, createSecretRequestDto);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.UPDATE)
    public SecretDetailDto updateSecret(UUID uuid, SecretUpdateRequestDto secretRequest) throws NotFoundException, AttributeException, ConnectorException {
        Secret secret = getSecretEntity(uuid);

        attributeEngine.validateCustomAttributesContent(Resource.SECRET, secretRequest.getCustomAttributes());
        VaultProfile currentSourceVaultProfile = secret.getSourceVaultProfile();
        secret.setDescription(secretRequest.getDescription());

        List<ResponseAttribute> updatedAttributes = null;
        if (secretRequest.getSecret() != null) {
            SecretVersion latestVersion = secret.getLatestVersion();
            String newFingerprint;
            try {
                newFingerprint = SecretsUtil.calculateSecretContentFingerprint(secretRequest.getSecret());
            } catch (NoSuchAlgorithmException | JsonProcessingException e) {
                throw new ValidationException("Unable to calculate secret fingerprint: " + e.getMessage());
            }

            boolean contentChanged = !newFingerprint.equals(latestVersion.getFingerprint());
            if (contentChanged) {
                SecretVersion newVersion = new SecretVersion();
                newVersion.setSecret(secret);
                newVersion.setVersion(latestVersion.getVersion() + 1);
                newVersion.setFingerprint(newFingerprint);
                newVersion.setVaultInstance(currentSourceVaultProfile.getVaultInstance());
                secretVersionRepository.save(newVersion);
                Set<UUID> processedVaultInstanceUuids = new HashSet<>();
                processedVaultInstanceUuids.add(currentSourceVaultProfile.getVaultInstance().getUuid());
                SecretResponseDto sourceVaultProfileResponse = updateSecretInVault(secret, secret.getSourceVaultProfile(), secretRequest, secretRequest.getAttributes());
                for (Secret2SyncVaultProfile profile : secret.getSyncVaultProfiles()) {
                    if (!processedVaultInstanceUuids.contains(profile.getVaultProfile().getVaultInstance().getUuid())) {
                        updateSecretInVault(secret, profile.getVaultProfile(), secretRequest, profile.getSecretAttributes());
                        processedVaultInstanceUuids.add(profile.getVaultProfile().getVaultInstance().getUuid());
                    }
                }
                attributeEngine.updateMetadataAttributes(sourceVaultProfileResponse.getMetadata(), new ObjectAttributeContentInfo(currentSourceVaultProfile.getVaultInstance().getConnectorUuid(), Resource.CERTIFICATE, secret.getUuid()));
                secret.setLatestVersion(newVersion);
                secret.getLatestVersion().setVaultVersion(sourceVaultProfileResponse.getVersion());
                secret.getVersions().add(newVersion);
                updatedAttributes = attributeEngine.updateObjectDataAttributesContent(currentSourceVaultProfile.getVaultInstance().getConnectorUuid(), null, Resource.SECRET, secret.getUuid(), secretRequest.getAttributes());
            }
        }

        SecretDetailDto secretDetailDto = secret.mapToDetailDto();
        secretDetailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.SECRET, secret.getUuid(), secretRequest.getCustomAttributes()));
        secretDetailDto.setAttributes(updatedAttributes == null ? attributeEngine.getObjectDataAttributesContent(currentSourceVaultProfile.getVaultInstance().getConnectorUuid(), null, Resource.SECRET, secret.getUuid()) : updatedAttributes);
        secretDetailDto.setMetadata(attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.SECRET, secret.getUuid())));

        return secretDetailDto;
    }


    private SecretResponseDto updateSecretInVault(Secret secret, VaultProfile vaultProfile, SecretUpdateRequestDto secretRequest, List<RequestAttribute> secretAttributes) throws
            NotFoundException, ConnectorException, AttributeException {
        ConnectorDetailDto connectorDetailDto = connectorService.getConnector(SecuredUUID.fromUUID(vaultProfile.getVaultInstance().getConnectorUuid()));
        List<BaseAttribute> createSecretAttributes = vaultProfileService.getAttributesForCreatingSecret(vaultProfile.getVaultInstance().getSecuredParentUuid(), vaultProfile.getSecuredUuid(), secretRequest.getSecret().getType());
        UUID connectorUuid = UUID.fromString(connectorDetailDto.getUuid());
        List<RequestAttribute> requestAttributes = connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(connectorUuid, createSecretAttributes, secretAttributes);
        UpdateSecretRequestDto updateSecretRequestDto = new UpdateSecretRequestDto();
        updateSecretRequestDto.setName(secret.getName());
        updateSecretRequestDto.setSecret(secretRequest.getSecret());
        List<BaseAttribute> vaultAttributes = vaultInstanceService.listVaultInstanceAttributes(connectorUuid);
        updateSecretRequestDto.setVaultAttributes(connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(connectorUuid, vaultAttributes, attributeEngine.getRequestObjectDataAttributesContent(connectorUuid, null, Resource.VAULT, vaultProfile.getVaultInstanceUuid())));
        updateSecretRequestDto.setSecretAttributes(requestAttributes);
        updateSecretRequestDto.setMetadata(attributeEngine.getMetadataAttributesDefinitionContent(new ObjectAttributeContentInfo(connectorUuid, Resource.SECRET, secret.getUuid())));
        return secretApiClient.updateSecret(connectorDetailDto, updateSecretRequestDto);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.DELETE)
    public void deleteSecret(UUID uuid) throws NotFoundException, ConnectorException, AttributeException {
        Secret secret = secretRepository.findByUuid(SecuredUUID.fromUUID(uuid))
                .orElseThrow(() -> new NotFoundException(Secret.class, uuid));
        permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(secret.getSourceVaultProfile().getUuid()));
        // Delete secret from vaults
        Set<UUID> vaultInstanceUuids = new HashSet<>();
        deleteSecretFromVault(secret.getSourceVaultProfile(), secret, attributeEngine.getRequestObjectDataAttributesContent(secret.getSourceVaultProfile().getVaultInstance().getConnectorUuid(), null, Resource.SECRET, secret.getUuid()));
        vaultInstanceUuids.add(secret.getSourceVaultProfile().getVaultInstance().getUuid());
        for (Secret2SyncVaultProfile profile : secret.getSyncVaultProfiles()) {
            if (!vaultInstanceUuids.contains(profile.getVaultProfile().getVaultInstance().getUuid())) {
                deleteSecretFromVault(profile.getVaultProfile(), secret, profile.getSecretAttributes());
                vaultInstanceUuids.add(profile.getVaultProfile().getVaultInstance().getUuid());
            }
        }
        Set<SecretVersion> secretVersions = new HashSet<>(secret.getVersions());
        secret.setOwner(null);
        secret.getGroups().clear();
        objectAssociationService.removeObjectAssociations(Resource.SECRET, secret.getUuid());
        secretRepository.delete(secret);
        secretVersionRepository.deleteAll(secretVersions);
    }

    private void deleteSecretFromVault(VaultProfile profile, Secret secret, List<RequestAttribute> secretAttributes) throws NotFoundException, ConnectorException, AttributeException {
        ConnectorDetailDto connectorDetailDto = connectorService.getConnector(SecuredUUID.fromUUID(profile.getVaultInstance().getConnectorUuid()));
        com.czertainly.api.model.connector.secrets.SecretRequestDto secretRequestDto = getSecretRequestDto(secret, connectorDetailDto, profile.getVaultInstance().getUuid(), secretAttributes);
        try {
            secretApiClient.deleteSecret(connectorDetailDto, secretRequestDto);
        } catch (ConnectorProblemException e) {
            if (e.getProblemDetail().getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                // If the secret is not found in the vault, we can consider it as already deleted and continue with the process
                logger.warn("Secret {} has already been removed in the vault {}", secret.getName(), profile.getVaultInstance().getName());
            } else {
                throw e;
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.ENABLE)
    public void enableSecret(UUID uuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        secret.setEnabled(true);
        secretRepository.save(secret);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.ENABLE)
    public void disableSecret(UUID uuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        secret.setEnabled(false);
        secretRepository.save(secret);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.UPDATE)
    public void addVaultProfileToSecret(UUID uuid, UUID
            vaultProfileUuid, List<RequestAttribute> createSecretAttributes) throws
            NotFoundException, ConnectorException, AttributeException {
        Secret secret = getSecretEntity(uuid);
        if (secret.getSourceVaultProfile().getUuid().equals(vaultProfileUuid)) {
            throw new ValidationException("Vault Profile with UUID %s is the source vault profile for secret with UUID %s".formatted(vaultProfileUuid, uuid));
        }
        if (secret.getSyncVaultProfiles().stream().anyMatch(profile -> profile.getVaultProfile().getUuid().equals(vaultProfileUuid))) {
            throw new ValidationException("Vault Profile with UUID %s is already a sync vault profile for secret with UUID %s".formatted(vaultProfileUuid, uuid));
        }

        VaultProfile addedVaultProfile = vaultProfileRepository.findByUuid(SecuredUUID.fromUUID(vaultProfileUuid))
                .orElseThrow(() -> new NotFoundException(VaultProfile.class, vaultProfileUuid));
        permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(addedVaultProfile.getUuid()));
        if (!addedVaultProfile.getVaultInstanceUuid().equals(secret.getSourceVaultProfile().getVaultInstanceUuid())) {
            List<BaseAttribute> attributeDefinition = vaultProfileService.getAttributesForCreatingSecret(addedVaultProfile.getVaultInstance().getSecuredParentUuid(), addedVaultProfile.getSecuredUuid(), secret.getType());
            List<RequestAttribute> requestAttributes = connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(
                    addedVaultProfile.getVaultInstance().getConnectorUuid(), attributeDefinition, createSecretAttributes
            );
            SecretRequestDto createSecretRequestDto = new SecretRequestDto();
            createSecretRequestDto.setName(secret.getName());
            createSecretRequestDto.setAttributes(requestAttributes);
            SecretContent secretContent = getSecretContent(uuid);
            createSecretRequestDto.setSecret(secretContent);
            try {
                createSecretInVault(addedVaultProfile.getVaultInstance().getConnectorUuid(), addedVaultProfile.getVaultInstanceUuid(), secret.getType(), addedVaultProfile.getUuid(), createSecretRequestDto);
            } catch (ConnectorProblemException e) {
                if (e.getProblemDetail().getErrorCode() == ErrorCode.RESOURCE_ALREADY_EXISTS) {
                    logger.warn("Secret {} already exists in the vault {}", secret.getName(), addedVaultProfile.getVaultInstance().getName());
                } else {
                    throw e;
                }
            }
        }
        Secret2SyncVaultProfileId secret2SyncVaultProfileId = new Secret2SyncVaultProfileId(secret.getUuid(), addedVaultProfile.getUuid());
        Secret2SyncVaultProfile secret2SyncVaultProfile = new Secret2SyncVaultProfile();
        secret2SyncVaultProfile.setId(secret2SyncVaultProfileId);
        secret2SyncVaultProfile.setSecretAttributes(createSecretAttributes);
        secret2SyncVaultProfile.setVaultProfile(addedVaultProfile);
        secret2SyncVaultProfile.setSecret(secret);
        secret2SyncVaultProfileRepository.save(secret2SyncVaultProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.UPDATE)
    public void removeVaultProfileFromSecret(UUID uuid, UUID vaultProfileUuid) throws
            NotFoundException, ConnectorException, AttributeException {
        Secret secret = getSecretEntity(uuid);
        if (secret.getSyncVaultProfiles().stream().noneMatch(profile -> profile.getVaultProfile().getUuid().equals(vaultProfileUuid))) {
            throw new ValidationException("Vault Profile with UUID %s is not a sync vault profile for secret with UUID %s".formatted(vaultProfileUuid, uuid));
        }
        VaultProfile removedVaultProfile = secret.getSyncVaultProfiles().stream().filter(profile -> profile.getVaultProfile().getUuid().equals(vaultProfileUuid)).findFirst().orElseThrow(
                () -> new NotFoundException(VaultProfile.class, vaultProfileUuid)
        ).getVaultProfile();
        Secret2SyncVaultProfile secret2SyncVaultProfile = secret2SyncVaultProfileRepository.getReferenceById(new Secret2SyncVaultProfileId(secret.getUuid(), removedVaultProfile.getUuid()));
        // Check if there are any vault profiles related to the secret with the same vault instance as the one being removed
        // If there are no such profiles, remove the secret from the vault
        if (!Objects.equals(removedVaultProfile.getVaultInstanceUuid(), secret.getSourceVaultProfile().getVaultInstanceUuid()) && secret.getSyncVaultProfiles()
                .stream()
                .noneMatch(profile -> profile.getVaultProfile().getVaultInstanceUuid().equals(removedVaultProfile.getVaultInstanceUuid()))) {
            deleteSecretFromVault(removedVaultProfile, secret, secret2SyncVaultProfile.getSecretAttributes());
        }
        secret.getSyncVaultProfiles().remove(secret2SyncVaultProfile);
        secretRepository.save(secret);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.DETAIL)
    public SecretDetailDto getSecretDetails(UUID uuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        SecretDetailDto secretDetailDto = secret.mapToDetailDto();
        secretDetailDto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.SECRET, secret.getUuid()));
        secretDetailDto.setAttributes(attributeEngine.getObjectDataAttributesContent(secret.getSourceVaultProfile().getVaultInstance().getConnectorUuid(), null, Resource.SECRET, secret.getUuid()));
        secretDetailDto.setMetadata(attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.SECRET, secret.getUuid())));
        return secretDetailDto;
    }

    private Secret getSecretEntity(UUID uuid) throws NotFoundException {
        Secret secret = secretRepository.findWithAssociationsByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Secret.class, uuid));
        permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(secret.getSourceVaultProfile().getUuid()));
        return secret;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.DETAIL)
    public List<SecretVersionDto> getSecretVersions(UUID uuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        return secret.getVersions().stream().map(SecretVersion::mapToDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.GET_SECRET_CONTENT)
    public SecretContent getSecretContent(UUID uuid) throws NotFoundException, ConnectorException, AttributeException {
        Secret secret = getSecretEntity(uuid);
        if (!secret.getSourceVaultProfile().isEnabled()) {
            throw new ValidationException("Source vault profile" + secret.getSourceVaultProfile().getName() + " is not enabled");
        }
        if (!secret.isEnabled()) {
            throw new ValidationException("Secret" + secret.getName() + " is not enabled");
        }
        SecretVersion latestVersion = secret.getLatestVersion();
        ConnectorDetailDto connectorDetailDto = connectorService.getConnector(SecuredUUID.fromUUID(latestVersion.getVaultInstance().getConnectorUuid()));
        com.czertainly.api.model.connector.secrets.SecretRequestDto secretRequestDto = getSecretRequestDto(secret, connectorDetailDto, latestVersion.getVaultInstance().getUuid(), attributeEngine.getRequestObjectDataAttributesContent(UUID.fromString(connectorDetailDto.getUuid()), null, Resource.SECRET, secret.getUuid()));
        SecretContentResponseDto secretContent = secretApiClient.getSecretContent(connectorDetailDto, secretRequestDto, latestVersion.getVaultVersion());
        String secretContentFingerprint = null;
        try {
            secretContentFingerprint = SecretsUtil.calculateSecretContentFingerprint(secretContent.getContent());
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new ValidationException("Unable to calculate secret fingerprint: " + e.getMessage());
        }
        if (!secret.getLatestVersion().getFingerprint().equals(secretContentFingerprint)) {
            SecretVersion newVersion = new SecretVersion();
            newVersion.setSecret(secret);
            newVersion.setVersion(latestVersion.getVersion() + 1);
            newVersion.setFingerprint(secretContentFingerprint);
            newVersion.setVaultInstance(latestVersion.getVaultInstance());
            newVersion.setVaultVersion(secretContent.getVersion());
            secretVersionRepository.save(newVersion);
            secret.setLatestVersion(newVersion);
            secret.getVersions().add(newVersion);
            secretRepository.save(secret);
        }
        return secretContent.getContent();
    }

    private com.czertainly.api.model.connector.secrets.SecretRequestDto getSecretRequestDto(Secret secret, ConnectorDetailDto connectorDetailDto, UUID vaultInstanceUuid, List<RequestAttribute> secretAttributes) throws ConnectorException, NotFoundException, AttributeException {
        com.czertainly.api.model.connector.secrets.SecretRequestDto secretRequestDto = new com.czertainly.api.model.connector.secrets.SecretRequestDto();
        secretRequestDto.setName(secret.getName());
        secretRequestDto.setType(secret.getType());
        UUID connectorUuid = UUID.fromString(connectorDetailDto.getUuid());
        List<BaseAttribute> attributeDefinition = vaultProfileService.getAttributesForCreatingSecret(secret.getSourceVaultProfile().getVaultInstance().getSecuredParentUuid(), secret.getSourceVaultProfile().getSecuredUuid(), secret.getType());
        secretRequestDto.setSecretAttributes(connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(connectorUuid, attributeDefinition, secretAttributes));
        List<BaseAttribute> vaultAttributes = vaultInstanceService.listVaultInstanceAttributes(connectorUuid);
        secretRequestDto.setVaultAttributes(connectorRequestAttributesBuilder.prepareRequestAttributesForConnectorRequest(connectorUuid, vaultAttributes, attributeEngine.getRequestObjectDataAttributesContent(connectorUuid, null, Resource.VAULT, vaultInstanceUuid)));
        secretRequestDto.setMetadata(attributeEngine.getMetadataAttributesDefinitionContent(new ObjectAttributeContentInfo(connectorUuid, Resource.SECRET, secret.getUuid())));
        return secretRequestDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.UPDATE)
    public void updateSecretObjects(UUID uuid, SecretUpdateObjectsDto request) throws
            NotFoundException, ConnectorException, AttributeException {
        Secret secret = getSecretEntity(uuid);
        if (request.getSourceVaultProfileUuid() != null) {
            updateSourceVaultProfile(request, secret);
        }
        if (request.getGroupUuids() != null) {
            // check if there is change in groups compared to the current state
            Set<UUID> currentGroups = secret.getGroups().stream().map(Group::getUuid).collect(Collectors.toSet());
            if (currentGroups.equals(request.getGroupUuids())) {
                return;
            }
            objectAssociationService.setGroups(Resource.SECRET, secret.getUuid(), request.getGroupUuids());
        }
        if (request.getOwnerUuid() != null) {
            if (secret.getOwner() != null && request.getOwnerUuid().equals(secret.getOwner().getUuid().toString())) {
                return;
            }
            if (request.getOwnerUuid().isEmpty()) {
                secret.setOwner(null);
                secretRepository.save(secret);
            }
            objectAssociationService.setOwner(Resource.SECRET, secret.getUuid(), request.getOwnerUuid().isEmpty() ? null : UUID.fromString(request.getOwnerUuid()));
        }
    }

    private void updateSourceVaultProfile(SecretUpdateObjectsDto request, Secret secret) throws
            NotFoundException, ConnectorException, AttributeException {
        UUID currentSourceVaultProfileUuid = secret.getSourceVaultProfile().getUuid();
        // Evaluate vault profile membership for current source profile
        permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(currentSourceVaultProfileUuid));
        VaultProfile currentSourceVaultProfile = secret.getSourceVaultProfile();

        boolean sourceVaultProfileChanged = !request.getSourceVaultProfileUuid().equals(currentSourceVaultProfileUuid);
        if (sourceVaultProfileChanged) {
            VaultProfile updatedSourceVaultProfile = vaultProfileRepository.findByUuid(SecuredUUID.fromUUID(request.getSourceVaultProfileUuid()))
                    .orElseThrow(() -> new NotFoundException(VaultProfile.class, request.getSourceVaultProfileUuid()));
            permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(request.getSourceVaultProfileUuid()));
            // Move original source vault profile to sync vault profiles
            Secret2SyncVaultProfile secret2SyncVaultProfile = new Secret2SyncVaultProfile();
            secret2SyncVaultProfile.setSecretAttributes(attributeEngine.getRequestObjectDataAttributesContent(currentSourceVaultProfile.getVaultInstance().getConnectorUuid(), null, Resource.SECRET, secret.getUuid()));
            secret2SyncVaultProfile.setId(new Secret2SyncVaultProfileId(secret.getUuid(), currentSourceVaultProfile.getUuid()));
            secret2SyncVaultProfile.setVaultProfile(currentSourceVaultProfile);
            secret2SyncVaultProfile.setSecret(secret);
            secret2SyncVaultProfileRepository.save(secret2SyncVaultProfile);

            // If new source vault profile was in sync vault profiles, remove association
            if (secret.getSyncVaultProfiles().stream().anyMatch(profile -> profile.getVaultProfile().getUuid().equals(updatedSourceVaultProfile.getUuid()))) {
                secret2SyncVaultProfileRepository.deleteById(new Secret2SyncVaultProfileId(secret.getUuid(), updatedSourceVaultProfile.getUuid()));
                secret.getSyncVaultProfiles().removeIf(profile -> profile.getVaultProfile().getUuid().equals(updatedSourceVaultProfile.getUuid()));
            }

            secret.setSourceVaultProfile(updatedSourceVaultProfile);
            if (updatedSourceVaultProfile.getVaultInstance() != currentSourceVaultProfile.getVaultInstance()) {
                attributeEngine.deleteObjectAttributesContent(AttributeType.DATA, new ObjectAttributeContentInfo(currentSourceVaultProfile.getVaultInstance().getConnectorUuid(), Resource.SECRET, secret.getUuid()));
                SecretVersion newVersion = new SecretVersion();
                newVersion.setSecret(secret);
                newVersion.setVersion(secret.getLatestVersion().getVersion() + 1);
                newVersion.setVaultInstance(updatedSourceVaultProfile.getVaultInstance());
                SecretRequestDto secretRequest = new SecretRequestDto();
                secretRequest.setName(secret.getName());
                secretRequest.setAttributes(request.getSecretAttributes());
                secretRequest.setSecret(getSecretContent(secret.getUuid()));
                SecretResponseDto secretResponseDto = null;
                try {
                    secretResponseDto = createSecretInVault(updatedSourceVaultProfile.getVaultInstance().getConnectorUuid(), updatedSourceVaultProfile.getVaultInstanceUuid(), secret.getType(), updatedSourceVaultProfile.getUuid(), secretRequest);
                } catch (ConnectorProblemException e) {
                    if (e.getProblemDetail().getErrorCode() == ErrorCode.RESOURCE_ALREADY_EXISTS) {
                        logger.warn("Secret {} already exists in the vault {}", secret.getName(), updatedSourceVaultProfile.getVaultInstance().getName());
                        newVersion.setVaultVersion(secret.getLatestVersion().getVaultVersion());
                    } else {
                        throw e;
                    }
                }
                if (secretResponseDto != null) {
                    attributeEngine.updateMetadataAttributes(secretResponseDto.getMetadata(), new ObjectAttributeContentInfo(updatedSourceVaultProfile.getVaultInstance().getConnectorUuid(), Resource.SECRET, secret.getUuid()));
                    newVersion.setVaultVersion(secretResponseDto.getVersion());
                }
                attributeEngine.updateObjectDataAttributesContent(updatedSourceVaultProfile.getVaultInstance().getConnectorUuid(), null, Resource.SECRET, secret.getUuid(), request.getSecretAttributes());
                newVersion.setFingerprint(secret.getLatestVersion().getFingerprint());
                secretVersionRepository.save(newVersion);
                secret.setLatestVersion(newVersion);
                secret.getVersions().add(newVersion);
                secretRepository.save(secret);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.GET_SECRET_CONTENT)
    public ResourceObjectContentData getResourceObjectContent(UUID uuid) throws NotFoundException, ConnectorException, AttributeException {
        ResourceSecretContentData resourceSecretContentData = new ResourceSecretContentData();
        SecretContent contentDto = getSecretContent(uuid);
        resourceSecretContentData.setContent(contentDto);
        return resourceSecretContentData;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.DETAIL)
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        Secret secret = getSecretEntity(objectUuid);
        return new NameAndUuidDto(secret.getUuid(), secret.getName());
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.LIST, parentResource = Resource.VAULT_PROFILE, parentAction = ResourceAction.MEMBERS)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) {
        filter.setParentRefProperty(Secret_.SOURCE_VAULT_PROFILE_UUID);
        return secretRepository.listResourceObjects(filter, Secret_.name,  (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, filters), pagination);
    }

    @Override
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getSecretEntity(uuid.getValue());
    }
}
