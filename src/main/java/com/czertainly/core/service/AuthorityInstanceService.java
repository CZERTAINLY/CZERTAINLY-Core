package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;

import java.util.List;

public interface AuthorityInstanceService {
    List<AuthorityInstanceDto> listAuthorityInstances();

    AuthorityInstanceDto getAuthorityInstance(String uuid) throws NotFoundException, ConnectorException;

    AuthorityInstanceDto createAuthorityInstance(AuthorityInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException;

    AuthorityInstanceDto updateAuthorityInstance(String uuid, AuthorityInstanceRequestDto request) throws NotFoundException, ConnectorException;

    void removeAuthorityInstance(String uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listEndEntityProfiles(String uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCertificateProfiles(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCAsInProfile(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<AttributeDefinition> listRAProfileAttributes(String uuid) throws NotFoundException, ConnectorException;

    Boolean validateRAProfileAttributes(String uuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException;

    List<ForceDeleteMessageDto> bulkRemoveAuthorityInstance(List<String> uuids) throws NotFoundException, ValidationException, ConnectorException;

    void bulkForceRemoveAuthorityInstance(List<String> uuids) throws ValidationException, NotFoundException;
}
