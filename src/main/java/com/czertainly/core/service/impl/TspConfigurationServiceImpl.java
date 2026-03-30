package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Audited_;
import com.czertainly.core.dao.entity.signing.TspConfiguration;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.repository.signing.TspConfigurationRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.mapper.signing.TspConfigurationMapper;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.TspConfigurationService;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.model.SecuredList;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.ValidatorUtil;
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
public class TspConfigurationServiceImpl implements TspConfigurationService {

    private AttributeEngine attributeEngine;
    private SigningProfileRepository signingProfileRepository;
    private SigningProfileService signingProfileService;
    private TspConfigurationRepository tspConfigurationRepository;

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.LIST)
    @Transactional
    public PaginationResponseDto<TspConfigurationListDto> listTspConfigurations(SearchRequestDto request, SecurityFilter filter) {
        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<TspConfiguration>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<TspConfigurationListDto> configurations = tspConfigurationRepository.findUsingSecurityFilter(filter, List.of(), predicate, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED)))
                .stream()
                .map(TspConfigurationMapper::toListDto)
                .toList();
        PaginationResponseDto<TspConfigurationListDto> response = new PaginationResponseDto<>();
        response.setItems(configurations);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        response.setTotalItems(tspConfigurationRepository.countUsingSecurityFilter(filter, predicate));
        response.setTotalPages((int) Math.ceil((double) response.getTotalItems() / request.getItemsPerPage()));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.DETAIL)
    @Transactional
    public TspConfigurationDto getTspConfiguration(SecuredUUID uuid) throws NotFoundException {
        TspConfiguration configuration = getTspConfigurationEntity(uuid.getValue());
        List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.TSP_CONFIGURATION, uuid.getValue());
        return TspConfigurationMapper.toDto(configuration, customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.CREATE)
    @Transactional
    public TspConfigurationDto createTspConfiguration(TspConfigurationRequestDto request) throws AttributeException, NotFoundException {
        SigningProfile defaultSigningProfile = validateCreateUpdateRequest(request);
        TspConfiguration configuration = new TspConfiguration();
        return updateAndMapToDto(configuration, request, defaultSigningProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.UPDATE)
    @Transactional
    public TspConfigurationDto updateTspConfiguration(SecuredUUID uuid, TspConfigurationRequestDto request) throws NotFoundException, AttributeException {
        TspConfiguration configuration = getTspConfigurationEntity(uuid.getValue());

        SigningProfile defaultSigningProfile = validateCreateUpdateRequest(request);
        return updateAndMapToDto(configuration, request, defaultSigningProfile);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.DELETE)
    @Transactional
    public void deleteTspConfiguration(SecuredUUID uuid) throws NotFoundException {
        TspConfiguration configuration = getTspConfigurationEntity(uuid.getValue());
        deleteTspConfiguration(configuration);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.DELETE)
    @Transactional
    public List<BulkActionMessageDto> bulkDeleteTspConfigurations(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            TspConfiguration configuration = null;
            try {
                configuration = getTspConfigurationEntity(uuid.getValue());
                deleteTspConfiguration(configuration);
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
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public void enableTspConfiguration(SecuredUUID uuid) throws NotFoundException {
        TspConfiguration configuration = getTspConfigurationEntity(uuid.getValue());
        configuration.setEnabled(true);
        tspConfigurationRepository.save(configuration);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public List<BulkActionMessageDto> bulkEnableTspConfigurations(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            try {
                enableTspConfiguration(uuid);
            } catch (Exception e) {
                log.error(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), "", e.getMessage()));
            }
        }
        return messages;
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public void disableTspConfiguration(SecuredUUID uuid) throws NotFoundException {
        TspConfiguration configuration = getTspConfigurationEntity(uuid.getValue());
        configuration.setEnabled(false);
        tspConfigurationRepository.save(configuration);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TSP_CONFIGURATION, action = ResourceAction.ENABLE)
    @Transactional
    public List<BulkActionMessageDto> bulkDisableTspConfigurations(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            try {
                disableTspConfiguration(uuid);
            } catch (Exception e) {
                log.error(e.getMessage());
                messages.add(new BulkActionMessageDto(uuid.toString(), "", e.getMessage()));
            }
        }
        return messages;
    }

    private SigningProfile validateCreateUpdateRequest(TspConfigurationRequestDto request) throws NotFoundException, ValidationException {
        if (ValidatorUtil.containsUnreservedCharacters(request.getName())) {
            throw new ValidationException(ValidationError.create("Name can contain only unreserved URI characters (alphanumeric, hyphen, period, underscore, and tilde)"));
        }

        attributeEngine.validateCustomAttributesContent(Resource.TSP_CONFIGURATION, request.getCustomAttributes());

        SigningProfile defaultSigningProfile = null;
        if (request.getDefaultSigningProfileUuid() != null) {
            defaultSigningProfile = getSigningProfileEntity(request.getDefaultSigningProfileUuid());
            if (ValidatorUtil.containsUnreservedCharacters(defaultSigningProfile.getName())) {
                throw new ValidationException(ValidationError.create("Signing Profile name can contain only unreserved URI characters (alphanumeric, hyphen, period, underscore, and tilde)"));
            }
        }

        return defaultSigningProfile;
    }

    private TspConfigurationDto updateAndMapToDto(TspConfiguration configuration, TspConfigurationRequestDto request, SigningProfile defaultSigningProfile) throws AttributeException, NotFoundException {
        configuration.setName(request.getName());
        configuration.setDescription(request.getDescription());
        configuration.setDefaultSigningProfile(defaultSigningProfile);
        TspConfiguration saved = tspConfigurationRepository.save(configuration);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.TSP_CONFIGURATION, saved.getUuid(), request.getCustomAttributes());
        return TspConfigurationMapper.toDto(saved, customAttributes);

    }

    private void deleteTspConfiguration(TspConfiguration configuration) {
        SecuredList<SigningProfile> signingProfiles = signingProfileService.listSigningProfilesAssociatedWithTsp(configuration.getUuid(), SecurityFilter.create());
        if (!signingProfiles.isEmpty()) {
            throw new ValidationException(
                    ValidationError.create(String.format(
                                    "Cannot delete TSP Configuration: associated with Signing Profiles (%d): %s",
                                    signingProfiles.size(),
                                    signingProfiles.getAllowed().stream().map(SigningProfile::getName).collect(Collectors.joining(","))
                            )
                    )
            );
        } else {
            attributeEngine.deleteAllObjectAttributeContent(Resource.TSP_CONFIGURATION, configuration.getUuid());
            tspConfigurationRepository.delete(configuration);
        }
    }


    private TspConfiguration getTspConfigurationEntity(UUID uuid) throws NotFoundException {
        return tspConfigurationRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("TSP Configuration not found: " + uuid));
    }

    private SigningProfile getSigningProfileEntity(UUID signingProfileUuid) throws NotFoundException {
        return signingProfileRepository.findById(signingProfileUuid).orElseThrow(() -> new NotFoundException("Signing Profile not found: " + signingProfileUuid));

    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setTspConfigurationRepository(TspConfigurationRepository TspConfigurationRepository) {
        this.tspConfigurationRepository = TspConfigurationRepository;
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
