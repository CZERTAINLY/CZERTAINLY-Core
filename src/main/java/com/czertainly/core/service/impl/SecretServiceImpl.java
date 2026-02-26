package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.secret.*;
import com.czertainly.api.model.core.secret.content.*;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.SecretRepository;
import com.czertainly.core.dao.repository.SecretVersionRepository;
import com.czertainly.core.dao.repository.VaultInstanceRepository;
import com.czertainly.core.dao.repository.VaultProfileRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.SearchHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.function.TriFunction;
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

    private AttributeEngine attributeEngine;
    private VaultProfileRepository vaultProfileRepository;
    private VaultInstanceRepository vaultInstanceRepository;
    private SecretRepository secretRepository;
    private ResourceObjectAssociationService objectAssociationService;
    private SecretVersionRepository secretVersionRepository;
    private PermissionEvaluator permissionEvaluator;

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
                SearchHelper.prepareSearch(FilterField.SECRET_TYPE, Arrays.stream(SecretType.values()).map(SecretType::getCode).toList()),
                SearchHelper.prepareSearch(FilterField.SECRET_STATE, Arrays.stream(SecretState.values()).map(SecretState::getCode).toList()),
                SearchHelper.prepareSearch(FilterField.SECRET_ENABLED),
                SearchHelper.prepareSearch(FilterField.SECRET_SOURCE_VAULT_PROFILE, vaultProfileRepository.findAllNames())
        ));

        fieldDataDtos.sort(new SearchFieldDataComparator());
        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fieldDataDtos, FilterFieldSource.PROPERTY));
        return searchFieldDataByGroupDtos;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.LIST, parentResource = Resource.VAULT_PROFILE, parentAction = ResourceAction.MEMBERS)
    public PaginationResponseDto<SecretDto> listSecrets(SearchRequestDto searchRequest, SecurityFilter securityFilter) {
        TriFunction<Root<Secret>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, searchRequest.getFilters());
        List<Secret> secrets = getSecrets(securityFilter, searchRequest.getPageNumber(), searchRequest.getItemsPerPage(), additionalWhereClause);
        List<SecretDto> secretDtos = secrets.stream().map(Secret::mapToDto).toList();
        PaginationResponseDto<SecretDto> response = new PaginationResponseDto<>();
        response.setItems(secretDtos);
        response.setPageNumber(searchRequest.getPageNumber());
        response.setItemsPerPage(searchRequest.getItemsPerPage());
        response.setTotalItems(secretRepository.countUsingSecurityFilter(securityFilter, additionalWhereClause));
        response.setTotalPages((int) Math.ceil((double) response.getTotalItems() / searchRequest.getItemsPerPage()));
        return response;
    }

    private List<Secret> getSecrets(SecurityFilter securityFilter, int pageNumber, int itemsPerPage, TriFunction<Root<Secret>, CriteriaBuilder, CriteriaQuery<?>, Predicate> additionalWhereClause) {
        securityFilter.setParentRefProperty(Secret_.SOURCE_VAULT_PROFILE_UUID);
        Pageable p = PageRequest.of(pageNumber - 1, itemsPerPage);
        return secretRepository.findUsingSecurityFilter(securityFilter, List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED))).stream().toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.CREATE, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public SecretDetailDto createSecret(SecretRequestDto secretRequest, SecuredParentUUID sourceVaultProfileUuid, SecuredUUID vaultInstanceUuid) throws NotFoundException, AttributeException, AlreadyExistException {
        if (Boolean.TRUE.equals(secretRepository.existsByName(secretRequest.getName()))) {
            throw new AlreadyExistException("Secret with name '" + secretRequest.getName() + "' already exists");
        }

        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(sourceVaultProfileUuid)
                .orElseThrow(() -> new NotFoundException(VaultProfile.class, sourceVaultProfileUuid.toString()));

        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(vaultInstanceUuid)
                .orElseThrow(() -> new NotFoundException(VaultInstance.class, vaultInstanceUuid.toString()));

        attributeEngine.validateCustomAttributesContent(Resource.SECRET, secretRequest.getCustomAttributes());

        Secret secret = new Secret();
        secret.setName(secretRequest.getName());
        secret.setDescription(secretRequest.getDescription());
        secret.setSourceVaultProfile(vaultProfile);
        secret.setState(SecretState.ACTIVE); // State from connector?
        secret.setType(secretRequest.getSecret().getType());
        secretRepository.save(secret);

        SecretVersion secretVersion = new SecretVersion();
        secretVersion.setSecret(secret);
        secretVersion.setVersion(1);
        String fingerprint;
        try {
            fingerprint = CertificateUtil.getThumbprint(SerializationUtils.serialize(secretRequest.getSecret()));
        } catch (NoSuchAlgorithmException e) {
            throw new ValidationException("Unable to calculate secret fingerprint" + e.getMessage());
        }

        // Check for existing secret with the same fingerprint in the same vault profile and the same type??
        // if exists - throw exception only?

        secretVersion.setFingerprint(fingerprint);
        secretVersion.setVaultInstance(vaultInstance);
//        secretVersion.setVaultVersion(); from connector
        secretVersion.setSecret(secret);
        secretVersionRepository.save(secretVersion);

        secret.setLatestVersion(secretVersion);
        secret.getVersions().add(secretVersion);
        secretRepository.save(secret);

        // TODO: create secret in vault

        objectAssociationService.setOwnerFromProfile(Resource.SECRET, secret.getUuid());

        SecretDetailDto secretDetailDto = secret.mapToDetailDto();
        secretDetailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.SECRET, secret.getUuid(), secretRequest.getCustomAttributes()));
        secretDetailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(vaultInstance.getConnectorUuid(), null, Resource.SECRET, secret.getUuid(), secretRequest.getAttributes()));
        secretDetailDto.setMetadata(attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.SECRET, secret.getUuid())));

        return secretDetailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.UPDATE)
    public SecretDetailDto updateSecret(UUID uuid, SecretUpdateRequestDto secretRequest) throws NotFoundException, AttributeException {
        Secret secret = getSecretEntity(uuid);

        attributeEngine.validateCustomAttributesContent(Resource.SECRET, secretRequest.getCustomAttributes());

        VaultProfile currentSourceVaultProfile = secret.getSourceVaultProfile();

        secret.setDescription(secretRequest.getDescription());

        if (secretRequest.getSecret() != null) {
            SecretVersion latestVersion = secret.getLatestVersion();
            String newFingerprint;
            try {
                newFingerprint = CertificateUtil.getThumbprint(SerializationUtils.serialize(secretRequest.getSecret()));
            } catch (NoSuchAlgorithmException e) {
                throw new ValidationException("Unable to calculate secret fingerprint" + e.getMessage());
            }

            boolean contentChanged = !newFingerprint.equals(latestVersion.getFingerprint());
            if (contentChanged) {
                SecretVersion newVersion = new SecretVersion();
                newVersion.setSecret(secret);
                newVersion.setVersion(latestVersion.getVersion() + 1);
                newVersion.setFingerprint(newFingerprint);
                newVersion.setVaultInstance(currentSourceVaultProfile.getVaultInstance());
                newVersion.setVaultVersion(latestVersion.getVaultVersion());
                secretVersionRepository.save(newVersion);
                secret.setLatestVersionUuid(newVersion.getUuid());
                secret.getVersions().add(newVersion);
            }

            // TODO: update secret in vault and other vaults, if there are other
        }


        SecretDetailDto secretDetailDto = secret.mapToDetailDto();
        secretDetailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.SECRET, secret.getUuid(), secretRequest.getCustomAttributes()));
        secretDetailDto.setAttributes(attributeEngine.updateObjectDataAttributesContent(currentSourceVaultProfile.getVaultInstance().getConnectorUuid(), null, Resource.SECRET, secret.getUuid(), secretRequest.getAttributes()));
        secretDetailDto.setMetadata(attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(Resource.SECRET, secret.getUuid())));

        return secretDetailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.DELETE)
    public void deleteSecret(UUID uuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        // TODO: delete secret from vault and other vaults, if there are other
        secretRepository.delete(secret);
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
    public void addVaultProfileToSecret(UUID uuid, UUID vaultProfileUuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        if (secret.getSourceVaultProfile().getUuid().equals(vaultProfileUuid)) {
            throw new ValidationException("Vault Profile with UUID %s is the source vault profile for secret with UUID %s".formatted(vaultProfileUuid, uuid));
        }
        if (secret.getSyncVaultProfiles().stream().anyMatch(profile -> profile.getUuid().equals(vaultProfileUuid))) {
            throw new ValidationException("Vault Profile with UUID %s is already a sync vault profile for secret with UUID %s".formatted(vaultProfileUuid, uuid));
        }

        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(SecuredUUID.fromUUID(vaultProfileUuid))
                .orElseThrow(() -> new NotFoundException(VaultProfile.class, vaultProfileUuid));
        permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(vaultProfile.getUuid()));
        secret.getSyncVaultProfiles().add(vaultProfile);
        // TODO: add to vault if new vault
        secretRepository.save(secret);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.UPDATE)
    public void removeVaultProfileFromSecret(UUID uuid, UUID vaultProfileUuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        if (secret.getSyncVaultProfiles().stream().noneMatch(profile -> profile.getUuid().equals(vaultProfileUuid))) {
            throw new ValidationException("Vault Profile with UUID %s is not a sync vault profile for secret with UUID %s".formatted(vaultProfileUuid, uuid));
        }
        secret.getSyncVaultProfiles().removeIf(profile -> profile.getUuid().equals(vaultProfileUuid));
        // TODO: remove from vault if removed from vault
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
        Secret secret = secretRepository.findByUuid(SecuredUUID.fromUUID(uuid))
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
    public SecretContent getSecretContent(UUID uuid) throws NotFoundException {
        Secret secret = getSecretEntity(uuid);
        // TODO: get content from vault
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.UPDATE)
    public void updateSecretObjects(UUID uuid, SecretUpdateObjectsDto request) throws NotFoundException {
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
            if (secret.getOwner() != null && request.getOwnerUuid().equals(secret.getOwner().getUuid())) {
                return;
            }
            objectAssociationService.setOwner(Resource.SECRET, secret.getUuid(), request.getOwnerUuid());
        }
    }

    private void updateSourceVaultProfile(SecretUpdateObjectsDto request, Secret secret) throws NotFoundException {
        UUID currentSourceVaultProfileUuid = secret.getSourceVaultProfile().getUuid();
        // Evaluate vault profile membership for current source profile
        permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(currentSourceVaultProfileUuid));
        VaultProfile currentSourceVaultProfile = secret.getSourceVaultProfile();

        boolean sourceVaultProfileChanged = request.getSourceVaultProfileUuid() != currentSourceVaultProfileUuid;
        if (sourceVaultProfileChanged) {
            VaultProfile updatedSourceVaultProfile = vaultProfileRepository.findByUuid(SecuredUUID.fromUUID(request.getSourceVaultProfileUuid()))
                    .orElseThrow(() -> new NotFoundException(VaultProfile.class, request.getSourceVaultProfileUuid()));
            // Or detail permission here?
            permissionEvaluator.vaultProfileMembers(SecuredUUID.fromUUID(request.getSourceVaultProfileUuid()));
            secret.getSyncVaultProfiles().add(currentSourceVaultProfile);
            secret.setSourceVaultProfile(updatedSourceVaultProfile);
            if (updatedSourceVaultProfile.getVaultInstance() != currentSourceVaultProfile.getVaultInstance()) {
                SecretVersion newVersion = new SecretVersion();
                newVersion.setSecret(secret);
                newVersion.setVersion(secret.getLatestVersion().getVersion() + 1);
                newVersion.setVaultInstance(currentSourceVaultProfile.getVaultInstance());
                // TODO: get version from new vault instance
                secretVersionRepository.save(newVersion);
                secret.setLatestVersion(newVersion);
                secret.getVersions().add(newVersion);
                secretRepository.save(secret);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.GET_SECRET_CONTENT)
    public String getResourceObjectContent(UUID uuid) throws NotFoundException {
        SecretContent contentDto = getSecretContent(uuid);
        switch (contentDto.getType()) {
            case BASIC_AUTH, KEY_STORE, KEY_VALUE -> {
                try {
                    return objectMapper.writeValueAsString((contentDto));
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Cannot serialize secret content of type %s. Secret UUID: %s. Error: %s".formatted(contentDto.getType(), uuid, e.getMessage()));
                }
            }
            case API_KEY -> {
                return ((ApiKeySecretContent) contentDto).getContent();
            }
            case JWT_TOKEN -> {
                return ((JwtTokenSecretContent) contentDto).getContent();
            }
            case SECRET_KEY -> {
                return ((SecretKeySecretContent) contentDto).getContent();
            }
            case PRIVATE_KEY -> {
                return ((PrivateKeySecretContent) contentDto).getContent();
            }
            case GENERIC -> {
                return ((GenericSecretContent) contentDto).getContent();
            }
        }

        return "";
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.DETAIL)
    public NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException {
        Secret secret = getSecretEntity(objectUuid);
        return new NameAndUuidDto(secret.getUuid(), secret.getName());
    }

    @Override
    @ExternalAuthorization(resource = Resource.SECRET, action = ResourceAction.LIST)
    // Or maybe just some projection would be more efficient here
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) {
        return getSecrets(filter, pagination.getPageNumber(), pagination.getItemsPerPage(), (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, filters))
                .stream()
                .map(secret -> new NameAndUuidDto(secret.getUuid(), secret.getName()))
                .toList();
    }

    @Override
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getSecretEntity(uuid.getValue());
    }
}
