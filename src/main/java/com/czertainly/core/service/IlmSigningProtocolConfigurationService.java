package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationListDto;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolConfigurationRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface IlmSigningProtocolConfigurationService {

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    PaginationResponseDto<IlmSigningProtocolConfigurationListDto> listIlmSigningProtocolConfigurations(SearchRequestDto request, SecurityFilter filter);

    IlmSigningProtocolConfigurationDto getIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException;

    IlmSigningProtocolConfigurationDto createIlmSigningProtocolConfiguration(IlmSigningProtocolConfigurationRequestDto request) throws AttributeException, NotFoundException;

    IlmSigningProtocolConfigurationDto updateIlmSigningProtocolConfiguration(SecuredUUID uuid, IlmSigningProtocolConfigurationRequestDto request) throws NotFoundException, AttributeException;

    void deleteIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDeleteIlmSigningProtocolConfigurations(List<SecuredUUID> uuids);

    void enableIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkEnableIlmSigningProtocolConfigurations(List<SecuredUUID> uuids);

    void disableIlmSigningProtocolConfiguration(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDisableIlmSigningProtocolConfigurations(List<SecuredUUID> uuids);
}
