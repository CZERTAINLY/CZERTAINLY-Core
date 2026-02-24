package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.secret.SecretType;
import com.czertainly.api.model.core.vaultprofile.*;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.SecretRepository;
import com.czertainly.core.dao.repository.VaultInstanceRepository;
import com.czertainly.core.dao.repository.VaultProfileRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.VaultProfileService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.SearchHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class VaultProfileServiceImpl implements VaultProfileService {

    private VaultProfileRepository vaultProfileRepository;
    private VaultInstanceRepository vaultInstanceRepository;
    private AttributeEngine attributeEngine;
    private SecretRepository secretRepository;

    @Autowired
    public void setSecretRepository(SecretRepository secretRepository) {
        this.secretRepository = secretRepository;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setVaultProfileRepository(VaultProfileRepository vaultProfileRepository) {
        this.vaultProfileRepository = vaultProfileRepository;
    }

    @Autowired
    public void setVaultInstanceRepository(VaultInstanceRepository vaultInstanceRepository) {
        this.vaultInstanceRepository = vaultInstanceRepository;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.LIST, parentResource = Resource.VAULT, parentAction = ResourceAction.LIST)
    public VaultProfileListResponseDto listVaultProfiles(SearchRequestDto request, SecurityFilter securityFilter) {
        securityFilter.setParentRefProperty(VaultProfile_.VAULT_INSTANCE_UUID);

        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<VaultProfile>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<VaultProfileDto> vaultProfiles = vaultProfileRepository.findUsingSecurityFilter(securityFilter, List.of(), predicate, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED)))
                .stream()
                .map(VaultProfile::mapToDto)
                .toList();
        VaultProfileListResponseDto response = new VaultProfileListResponseDto();
        response.setVaultProfiles(vaultProfiles);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        response.setTotalItems(vaultProfileRepository.countUsingSecurityFilter(securityFilter,predicate));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public VaultProfileDetailDto getVaultProfileDetails(SecuredParentUUID vaultUuid, SecuredUUID vaultProfileUuid) throws NotFoundException {
        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(vaultProfileUuid).orElseThrow(() -> new NotFoundException(VaultProfile.class, vaultProfileUuid));
        VaultProfileDetailDto detailDto = vaultProfile.mapToDetailDto();
        detailDto.setAttributes(attributeEngine.getObjectDataAttributesContent(vaultProfile.getVaultInstance().getConnectorUuid(), null, Resource.VAULT_PROFILE, vaultProfile.getUuid()));
        detailDto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.VAULT_PROFILE, vaultProfileUuid.getValue()));
        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.UPDATE, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public VaultProfileDetailDto updateVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID, VaultProfileUpdateRequestDto request) throws NotFoundException {
        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(securedUUID).orElseThrow(() -> new NotFoundException(VaultProfile.class, securedUUID));
        // check that the vault profile is associated with the same vault instance?
        vaultProfile.setDescription(request.getDescription());
        attributeEngine.validateCustomAttributesContent(Resource.VAULT_PROFILE, request.getCustomAttributes());

        vaultProfileRepository.save(vaultProfile);
        VaultProfileDetailDto detailDto = vaultProfile.mapToDetailDto();
        detailDto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.VAULT_PROFILE, securedUUID.getValue()));
        detailDto.setAttributes(attributeEngine.getObjectDataAttributesContent(vaultProfile.getVaultInstance().getConnectorUuid(), null, Resource.VAULT_PROFILE, securedUUID.getValue()));

        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.DELETE, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public void deleteVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException {
        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(securedUUID).orElseThrow(() -> new NotFoundException(VaultProfile.class, securedUUID));
        List<String> secretsSource = secretRepository.findAllNamesBySourceVaultProfileUuid(securedUUID.getValue());
        if (!secretsSource.isEmpty()) {
            throw new ValidationException("Cannot delete vault profile %s set as source for secrets %s.".formatted(vaultProfile.getName(), secretsSource));
        }
        List<String> secretsSync = secretRepository.findAllNamesBySyncVaultProfileUuid(securedUUID.getValue());
        if (!secretsSync.isEmpty()) {
            throw new ValidationException("Cannot delete vault profile %s set as sync for secrets %s.".formatted(vaultProfile.getName(), secretsSync));
        }
        attributeEngine.deleteAllObjectAttributeContent(Resource.VAULT_PROFILE, securedUUID.getValue());
        vaultProfileRepository.delete(vaultProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.CREATE, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public VaultProfileDetailDto createVaultProfile(SecuredParentUUID securedParentUUID, VaultProfileRequestDto request) throws NotFoundException, ValidationException, AttributeException, AlreadyExistException {
        if (Boolean.TRUE.equals(vaultProfileRepository.existsByName(request.getName()))) {
            throw new AlreadyExistException("Vault Profile with name " + request.getName() + " already exists");
        }
        VaultInstance vaultInstance = vaultInstanceRepository.findByUuid(securedParentUUID).orElseThrow(() -> new NotFoundException(VaultInstance.class, securedParentUUID));
        attributeEngine.validateCustomAttributesContent(Resource.VAULT_PROFILE, request.getCustomAttributes());

        VaultProfile vaultProfile = new VaultProfile();
        vaultProfile.setName(request.getName());
        vaultProfile.setDescription(request.getDescription());
        vaultProfile.setVaultInstance(vaultInstance);
        vaultProfileRepository.save(vaultProfile);

        VaultProfileDetailDto detailDto = vaultProfile.mapToDetailDto();
        detailDto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.VAULT_PROFILE, vaultProfile.getUuid(), request.getCustomAttributes()));

        return detailDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public void enableVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException {
        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(securedUUID).orElseThrow(() -> new NotFoundException(VaultProfile.class, securedUUID));
        vaultProfile.setEnabled(true);
        vaultProfileRepository.save(vaultProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.ENABLE, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public void disableVaultProfile(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID) throws NotFoundException {
        VaultProfile vaultProfile = vaultProfileRepository.findByUuid(securedUUID).orElseThrow(() -> new NotFoundException(VaultProfile.class, securedUUID));
        vaultProfile.setEnabled(false);
        vaultProfileRepository.save(vaultProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.VAULT_PROFILE, action = ResourceAction.DETAIL, parentResource = Resource.VAULT, parentAction = ResourceAction.DETAIL)
    public List<BaseAttribute> getAttributesForCreatingSecret(SecuredParentUUID securedParentUUID, SecuredUUID securedUUID, SecretType secretType) {
        // TODO: call API client to get attributes
        return List.of();
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = attributeEngine.getResourceSearchableFields(Resource.CERTIFICATE, false);
        List<SearchFieldDataDto> fields = new ArrayList<>(List.of(
                SearchHelper.prepareSearch(FilterField.VAULT_PROFILE_NAME),
                SearchHelper.prepareSearch(FilterField.VAULT_PROFILE_VAULT_INSTANCE, vaultInstanceRepository.findAllNames())
        ));
        fields.sort(new SearchFieldDataComparator());
        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));
        return searchFieldDataByGroupDtos;
    }


}
