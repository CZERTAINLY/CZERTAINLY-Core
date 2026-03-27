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
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.TimeQualityConfigurationService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class TimeQualityConfigurationServiceImpl implements TimeQualityConfigurationService {

    private TimeQualityConfigurationRepository timeQualityConfigurationRepository;
    private SigningProfileRepository signingProfileRepository;
    private AttributeEngine attributeEngine;

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

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return new ArrayList<>();
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.LIST)
    public PaginationResponseDto<TimeQualityConfigurationListDto> listTimeQualityConfigurations(SearchRequestDto request, SecurityFilter filter) {
        List<TimeQualityConfiguration> configurations = timeQualityConfigurationRepository.findUsingSecurityFilter(filter);
        List<TimeQualityConfigurationListDto> dtos = configurations.stream()
                .map(TimeQualityConfiguration::mapToListDto)
                .toList();
        PaginationResponseDto<TimeQualityConfigurationListDto> response = new PaginationResponseDto<>();
        // :TODO: this is completely wrong
        response.setItemsPerPage(dtos.size());
        response.setPageNumber(1);
        response.setTotalItems(dtos.size());
        response.setTotalPages(1);
        response.setItems(dtos);
        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.DETAIL)
    public TimeQualityConfigurationDto getTimeQualityConfiguration(SecuredUUID uuid) throws NotFoundException {
        TimeQualityConfiguration configuration = timeQualityConfigurationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Time Quality Configuration not found"));
        List<ResponseAttribute> customAttributes = attributeEngine.getObjectCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, configuration.getUuid());
        return configuration.mapToDto(customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.CREATE)
    public TimeQualityConfigurationDto createTimeQualityConfiguration(TimeQualityConfigurationCreateRequestDto request) throws AttributeException, NotFoundException {
        attributeEngine.validateCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, request.getCustomAttributes());

        TimeQualityConfiguration configuration = new TimeQualityConfiguration();
        configuration.setName(request.getName());
        configuration.setNtpServers(request.getNtpServers());
        configuration.setNtpCheckInterval(request.getNtpCheckInterval());
        configuration.setNtpSamplesPerServer(request.getNtpSamplesPerServer());
        configuration.setNtpCheckTimeout(request.getNtpCheckTimeout());
        configuration.setMinReachable(request.getMinReachable());
        configuration.setMaxDrift(request.getMaxDrift());
        configuration.setLeapSecondGuard(request.isLeapSecondGuard());
        TimeQualityConfiguration saved = timeQualityConfigurationRepository.save(configuration);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, saved.getUuid(), request.getCustomAttributes());
        return saved.mapToDto(customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.UPDATE)
    public TimeQualityConfigurationDto updateTimeQualityConfiguration(SecuredUUID uuid, TimeQualityConfigurationUpdateRequestDto request) throws NotFoundException, AttributeException {
        TimeQualityConfiguration configuration = timeQualityConfigurationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Time Quality Configuration not found"));
        attributeEngine.validateCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, request.getCustomAttributes());

        configuration.setName(request.getName());
        configuration.setNtpServers(request.getNtpServers());
        configuration.setNtpCheckInterval(request.getNtpCheckInterval());
        configuration.setNtpSamplesPerServer(request.getNtpSamplesPerServer());
        configuration.setNtpCheckTimeout(request.getNtpCheckTimeout());
        configuration.setMinReachable(request.getMinReachable());
        configuration.setMaxDrift(request.getMaxDrift());
        configuration.setLeapSecondGuard(request.isLeapSecondGuard());
        TimeQualityConfiguration saved = timeQualityConfigurationRepository.save(configuration);

        List<ResponseAttribute> customAttributes = attributeEngine.updateObjectCustomAttributesContent(Resource.TIME_QUALITY_CONFIGURATION, saved.getUuid(), request.getCustomAttributes());
        return saved.mapToDto(customAttributes);
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.DELETE)
    public void deleteTimeQualityConfiguration(SecuredUUID uuid) throws NotFoundException {
        TimeQualityConfiguration configuration = timeQualityConfigurationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Time Quality Configuration not found"));

        List<SigningProfile> profiles = signingProfileRepository.findAllByTimeQualityConfigurationUuid(configuration.getUuid());
        if (!profiles.isEmpty()) {
            // :TODO: report at least one Signing Profile
            throw new ValidationException("Cannot delete Time Quality Configuration: it is in use by " + profiles.size() + " signing profile(s)");
        }

        timeQualityConfigurationRepository.delete(configuration);
        attributeEngine.deleteAllObjectAttributeContent(Resource.TIME_QUALITY_CONFIGURATION, configuration.getUuid());
    }

    @Override
    @ExternalAuthorization(resource = Resource.TIME_QUALITY_CONFIGURATION, action = ResourceAction.DELETE)
    public List<BulkActionMessageDto> bulkDeleteTimeQualityConfigurations(List<SecuredUUID> uuids) {
        // :TODO: use actual bulk delete in batches
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
}
