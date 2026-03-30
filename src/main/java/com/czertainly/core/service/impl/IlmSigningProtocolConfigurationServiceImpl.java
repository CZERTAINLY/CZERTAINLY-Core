package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Audited_;
import com.czertainly.core.dao.entity.signing.IlmSigningProtocolConfiguration;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.repository.signing.IlmSigningProtocolConfigurationRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.mapper.signing.IlmSigningProtocolConfigurationMapper;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.IlmSigningProtocolConfigurationService;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.model.SecuredList;
import com.czertainly.core.util.FilterPredicatesBuilder;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class IlmSigningProtocolConfigurationServiceImpl implements IlmSigningProtocolConfigurationService {

    private AttributeEngine attributeEngine;
    private IlmSigningProtocolConfigurationRepository ilmSigningProtocolConfigurationRepository;
    private SigningProfileRepository signingProfileRepository;
    private SigningProfileService signingProfileService;

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.LIST)
    @Transactional
    public PaginationResponseDto<IlmSigningProtocolConfigurationListDto> listIlmSigningProtocolConfigurations(SearchRequestDto request, SecurityFilter filter) {
        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<IlmSigningProtocolConfiguration>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<IlmSigningProtocolConfigurationListDto> configurations = ilmSigningProtocolConfigurationRepository.findUsingSecurityFilter(filter, List.of(), predicate, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED)))
                .stream()
                .map(IlmSigningProtocolConfigurationMapper::toListDto)
                .toList();
        PaginationResponseDto<IlmSigningProtocolConfigurationListDto> response = new PaginationResponseDto<>();
        response.setItems(configurations);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        response.setTotalItems(ilmSigningProtocolConfigurationRepository.countUsingSecurityFilter(filter, predicate));
        response.setTotalPages((int) Math.ceil((double) response.getTotalItems() / request.getItemsPerPage()));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.DETAIL)
    @Transactional
    public IlmSigningProtocolConfigurationDto getIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException {
        IlmSigningProtocolConfiguration configuration = getIlmSigningProtocolConfigurationEntity(uuid.getValue());
        List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, uuid.getValue());
        return IlmSigningProtocolConfigurationMapper.toDto(configuration, customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.CREATE)
    @Transactional
    public IlmSigningProtocolConfigurationDto createIlmSigningProtocolConfiguration(IlmSigningProtocolConfigurationRequestDto request) throws AttributeException, NotFoundException {
        SigningProfile defaultSigningProfile = validateCreateUpdateRequest(request);
        IlmSigningProtocolConfiguration configuration = new IlmSigningProtocolConfiguration();
        return updateAndMapToDto(configuration, request, defaultSigningProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.UPDATE)
    @Transactional
    public IlmSigningProtocolConfigurationDto updateIlmSigningProtocolConfiguration(SecuredUUID uuid, IlmSigningProtocolConfigurationRequestDto request) throws NotFoundException, AttributeException {
        IlmSigningProtocolConfiguration configuration = getIlmSigningProtocolConfigurationEntity(uuid.getValue());

        SigningProfile defaultSigningProfile = validateCreateUpdateRequest(request);
        return updateAndMapToDto(configuration, request, defaultSigningProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.DELETE)
    @Transactional
    public void deleteIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException {
        IlmSigningProtocolConfiguration configuration = getIlmSigningProtocolConfigurationEntity(uuid.getValue());
        deleteIlmSigningProtocolConfiguration(configuration);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.DELETE)
    @Transactional
    public List<BulkActionMessageDto> bulkDeleteIlmSigningProtocolConfigurations(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            IlmSigningProtocolConfiguration configuration = null;
            try {
                configuration = getIlmSigningProtocolConfigurationEntity(uuid.getValue());
                deleteIlmSigningProtocolConfiguration(configuration);
            } catch (Exception e) {
                log.error(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), configuration != null ? configuration.getName() : "", e.getMessage()));
            }
        }
        return messages;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Enable / disable
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public void enableIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException {
        IlmSigningProtocolConfiguration configuration = getIlmSigningProtocolConfigurationEntity(uuid.getValue());
        configuration.setEnabled(true);
        ilmSigningProtocolConfigurationRepository.save(configuration);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public List<BulkActionMessageDto> bulkEnableIlmSigningProtocolConfigurations(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            try {
                enableIlmSigningProtocolConfiguration(uuid);
            } catch (Exception e) {
                log.error(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), "", e.getMessage()));
            }
        }
        return messages;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public void disableIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException {
        IlmSigningProtocolConfiguration configuration = getIlmSigningProtocolConfigurationEntity(uuid.getValue());
        configuration.setEnabled(false);
        ilmSigningProtocolConfigurationRepository.save(configuration);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public List<BulkActionMessageDto> bulkDisableIlmSigningProtocolConfigurations(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            try {
                disableIlmSigningProtocolConfiguration(uuid);
            } catch (Exception e) {
                log.error(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), "", e.getMessage()));
            }
        }
        return messages;
    }

    private SigningProfile validateCreateUpdateRequest(IlmSigningProtocolConfigurationRequestDto request) throws NotFoundException, ValidationException {
        attributeEngine.validateCustomAttributesContent(Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, request.getCustomAttributes());

        SigningProfile defaultSigningProfile = null;
        if (request.getDefaultSigningProfileUuid() != null) {
            defaultSigningProfile = getSigningProfileEntity(request.getDefaultSigningProfileUuid());
        }

        return defaultSigningProfile;
    }

    private IlmSigningProtocolConfigurationDto updateAndMapToDto(IlmSigningProtocolConfiguration configuration, IlmSigningProtocolConfigurationRequestDto request, SigningProfile defaultSigningProfile) throws AttributeException, NotFoundException {
        configuration.setName(request.getName());
        configuration.setDescription(request.getDescription());
        configuration.setDefaultSigningProfile(defaultSigningProfile);
        IlmSigningProtocolConfiguration saved = ilmSigningProtocolConfigurationRepository.save(configuration);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, saved.getUuid(), request.getCustomAttributes());
        return IlmSigningProtocolConfigurationMapper.toDto(saved, customAttributes);

    }

    private void deleteIlmSigningProtocolConfiguration(IlmSigningProtocolConfiguration configuration) {
        SecuredList<SigningProfile> signingProfiles = signingProfileService.listSigningProfilesAssociatedWithIlmSigningProtocol(configuration.getUuid(), SecurityFilter.create());
        if (!signingProfiles.isEmpty()) {
            throw new ValidationException(
                    ValidationError.create(String.format(
                                    "Cannot delete ILM Signing Protocol Configuration: dependent Signing Profiles exist (%d): %s",
                                    signingProfiles.size(),
                                    signingProfiles.getAllowed().stream().map(SigningProfile::getName).collect(Collectors.joining(","))
                            )
                    )
            );
        }

        attributeEngine.deleteAllObjectAttributeContent(Resource.ILM_SIGNING_PROTOCOL_CONFIGURATION, configuration.getUuid());
        ilmSigningProtocolConfigurationRepository.delete(configuration);
    }

    private IlmSigningProtocolConfiguration getIlmSigningProtocolConfigurationEntity(UUID uuid) throws NotFoundException {
        return ilmSigningProtocolConfigurationRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("ILM Signing Protocol Configuration not found: " + uuid));
    }

    private SigningProfile getSigningProfileEntity(UUID signingProfileUuid) throws NotFoundException {
        return signingProfileRepository.findById(signingProfileUuid).orElseThrow(() -> new NotFoundException("Signing Profile not found: " + signingProfileUuid));

    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setIlmSigningProtocolConfigurationRepository(IlmSigningProtocolConfigurationRepository ilmSigningProtocolConfigurationRepository) {
        this.ilmSigningProtocolConfigurationRepository = ilmSigningProtocolConfigurationRepository;
    }

    @Autowired
    public void setSigningProfileRepository(SigningProfileRepository signingProfileRepository) {
        this.signingProfileRepository = signingProfileRepository;
    }

    @Autowired
    public void setSigningProfileService(SigningProfileService signingProfileService) {
        this.signingProfileService = signingProfileService;
    }
}
