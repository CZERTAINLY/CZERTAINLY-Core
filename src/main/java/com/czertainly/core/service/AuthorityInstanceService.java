package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;

import java.util.List;

public interface AuthorityInstanceService {
    List<AuthorityInstanceDto> listAuthorityInstances();

    AuthorityInstanceDto getAuthorityInstance(String uuid) throws NotFoundException, ConnectorException;

    AuthorityInstanceDto createAuthorityInstance(AuthorityInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException;

    AuthorityInstanceDto editAuthorityInstance(String uuid, AuthorityInstanceUpdateRequestDto request) throws NotFoundException, ConnectorException;

    void deleteAuthorityInstance(String uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listEndEntityProfiles(String uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCertificateProfiles(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCAsInProfile(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<AttributeDefinition> listRAProfileAttributes(String uuid) throws NotFoundException, ConnectorException;

    Boolean validateRAProfileAttributes(String uuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException;

    List<BulkActionMessageDto> bulkDeleteAuthorityInstance(List<String> uuids) throws NotFoundException, ValidationException, ConnectorException;

    List<BulkActionMessageDto> forceDeleteAuthorityInstance(List<String> uuids) throws ValidationException, NotFoundException;
}
