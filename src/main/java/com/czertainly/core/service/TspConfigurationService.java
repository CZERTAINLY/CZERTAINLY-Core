package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface TspConfigurationService {

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    PaginationResponseDto<TspConfigurationListDto> listTspConfigurations(SearchRequestDto request, SecurityFilter filter);

    TspConfigurationDto getTspConfiguration(SecuredUUID uuid) throws NotFoundException;

    TspConfigurationDto createTspConfiguration(TspConfigurationRequestDto request) throws AttributeException, NotFoundException;

    TspConfigurationDto updateTspConfiguration(SecuredUUID uuid, TspConfigurationRequestDto request) throws NotFoundException, AttributeException;

    void deleteTspConfiguration(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDeleteTspConfigurations(List<SecuredUUID> uuids);

    void enableTspConfiguration(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkEnableTspConfigurations(List<SecuredUUID> uuids);

    void disableTspConfiguration(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDisableTspConfigurations(List<SecuredUUID> uuids);
}
