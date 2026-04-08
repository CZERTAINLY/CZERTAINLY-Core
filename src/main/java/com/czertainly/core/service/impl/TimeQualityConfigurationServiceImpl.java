package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationCreateRequestDto;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationDto;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationListDto;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.Audited_;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration_;

import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.mapper.signing.TimeQualityConfigurationMapper;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.TimeQualityConfigurationService;
import com.czertainly.core.util.FilterPredicatesBuilder;
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
import java.util.UUID;

@Service(Resource.Codes.TIME_QUALITY_CONFIGURATION)
@Transactional
public class TimeQualityConfigurationServiceImpl implements TimeQualityConfigurationService {

    private TimeQualityConfigurationRepository timeQualityConfigurationRepository;
    private SigningProfileRepository signingProfileRepository;
    private AttributeEngine attributeEngine;

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.LIST)
    public PaginationResponseDto<TimeQualityConfigurationListDto> listTimeQualityConfigurations(SearchRequestDto request, SecurityFilter filter) {
        Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());
        TriFunction<Root<TimeQualityConfiguration>, CriteriaBuilder, CriteriaQuery<?>, Predicate> predicate = (root, cb, cq) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cq, root, request.getFilters());
        List<TimeQualityConfigurationListDto> configurations = timeQualityConfigurationRepository.findUsingSecurityFilter(filter, List.of(), predicate, p, (root, cb) -> cb.desc(root.get(Audited_.CREATED)))
                .stream()
                .map(TimeQualityConfigurationMapper::toListDto)
                .toList();
        PaginationResponseDto<TimeQualityConfigurationListDto> response = new PaginationResponseDto<>();
        response.setItems(configurations);
        response.setPageNumber(request.getPageNumber());
        response.setItemsPerPage(request.getItemsPerPage());
        response.setTotalItems(timeQualityConfigurationRepository.countUsingSecurityFilter(filter, predicate));
        response.setTotalPages((int) Math.ceil((double) response.getTotalItems() / request.getItemsPerPage()));
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.DETAIL)
    public TimeQualityConfigurationDto getTimeQualityConfiguration(SecuredUUID uuid) throws NotFoundException {
        TimeQualityConfiguration configuration = timeQualityConfigurationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Time Quality Configuration not found"));
        List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, configuration.getUuid());
        return TimeQualityConfigurationMapper.toDto(configuration, customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.CREATE)
    public TimeQualityConfigurationDto createTimeQualityConfiguration(TimeQualityConfigurationCreateRequestDto request) throws AttributeException, NotFoundException {
        attributeEngine.validateCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, request.getCustomAttributes());

        TimeQualityConfiguration configuration = new TimeQualityConfiguration();
        configuration.setName(request.getName());
        configuration.setAccuracy(request.getAccuracy());
        configuration.setNtpServers(request.getNtpServers());
        configuration.setNtpCheckInterval(request.getNtpCheckInterval());
        configuration.setNtpSamplesPerServer(request.getNtpSamplesPerServer());
        configuration.setNtpCheckTimeout(request.getNtpCheckTimeout());
        configuration.setNtpServersMinReachable(request.getNtpServersMinReachable());
        configuration.setMaxClockDrift(request.getMaxClockDrift());
        configuration.setLeapSecondGuard(request.isLeapSecondGuard());
        TimeQualityConfiguration saved = timeQualityConfigurationRepository.save(configuration);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, saved.getUuid(), request.getCustomAttributes());
        return TimeQualityConfigurationMapper.toDto(saved, customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.UPDATE)
    public TimeQualityConfigurationDto updateTimeQualityConfiguration(SecuredUUID uuid, TimeQualityConfigurationUpdateRequestDto request) throws NotFoundException, AttributeException {
        TimeQualityConfiguration configuration = timeQualityConfigurationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Time Quality Configuration not found"));
        attributeEngine.validateCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, request.getCustomAttributes());

        configuration.setName(request.getName());
        configuration.setAccuracy(request.getAccuracy());
        configuration.setNtpServers(request.getNtpServers());
        configuration.setNtpCheckInterval(request.getNtpCheckInterval());
        configuration.setNtpSamplesPerServer(request.getNtpSamplesPerServer());
        configuration.setNtpCheckTimeout(request.getNtpCheckTimeout());
        configuration.setNtpServersMinReachable(request.getNtpServersMinReachable());
        configuration.setMaxClockDrift(request.getMaxClockDrift());
        configuration.setLeapSecondGuard(request.isLeapSecondGuard());
        TimeQualityConfiguration saved = timeQualityConfigurationRepository.save(configuration);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, saved.getUuid(), request.getCustomAttributes());
        return TimeQualityConfigurationMapper.toDto(saved, customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.DELETE)
    public void deleteTimeQualityConfiguration(SecuredUUID uuid) throws NotFoundException {
        TimeQualityConfiguration configuration = timeQualityConfigurationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Time Quality Configuration not found"));

        List<SigningProfile> profiles = signingProfileRepository.findAllByTimeQualityConfigurationUuid(configuration.getUuid());
        if (!profiles.isEmpty()) {
            // :TODO: report at least one Signing Profile. Use the same approach as in TSP Configuration service.
            throw new ValidationException("Cannot delete Time Quality Configuration: it is in use by " + profiles.size() + " signing profile(s)");
        }

        timeQualityConfigurationRepository.delete(configuration);
        attributeEngine.deleteAllObjectAttributeContent(Resource.TIME_QUALITY_CONFIGURATION, configuration.getUuid());
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> bulkDeleteTimeQualityConfigurations(List<SecuredUUID> uuids) {
        List<BulkActionMessageDto> messages = new ArrayList<>();
        for (SecuredUUID uuid : uuids) {
            try {
                deleteTimeQualityConfiguration(uuid);
            } catch (Exception e) {
                BulkActionMessageDto message = new BulkActionMessageDto();
                message.setUuid(uuid.getValue().toString());
                message.setMessage(e.getMessage());
                messages.add(message);
            }
        }
        return messages;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ResourceExtensionService
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException {
        return timeQualityConfigurationRepository.findResourceObject(objectUuid, TimeQualityConfiguration_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.DETAIL)
    public NameAndUuidDto getResourceObjectExternal(SecuredUUID objectUuid) throws NotFoundException {
        return timeQualityConfigurationRepository.findResourceObject(objectUuid.getValue(), TimeQualityConfiguration_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination) {
        return timeQualityConfigurationRepository.listResourceObjects(filter, TimeQualityConfiguration_.name);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        timeQualityConfigurationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Time Quality Configuration not found"));
    }

    @Autowired
    public void setTimeQualityConfigurationRepository(TimeQualityConfigurationRepository timeQualityConfigurationRepository) {
        this.timeQualityConfigurationRepository = timeQualityConfigurationRepository;
    }

    @Autowired
    public void setSigningProfileRepository(SigningProfileRepository signingProfileRepository) {
        this.signingProfileRepository = signingProfileRepository;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }
}
