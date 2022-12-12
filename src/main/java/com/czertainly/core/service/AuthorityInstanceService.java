package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface AuthorityInstanceService {
    List<AuthorityInstanceDto> listAuthorityInstances(SecurityFilter filter);

    AuthorityInstanceDto getAuthorityInstance(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    AuthorityInstanceDto createAuthorityInstance(AuthorityInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException;

    AuthorityInstanceDto editAuthorityInstance(SecuredUUID uuid, AuthorityInstanceUpdateRequestDto request) throws NotFoundException, ConnectorException;

    void deleteAuthorityInstance(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listEndEntityProfiles(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCertificateProfiles(SecuredUUID uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCAsInProfile(SecuredUUID uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<BaseAttribute> listRAProfileAttributes(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    Boolean validateRAProfileAttributes(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException;

    List<BulkActionMessageDto> bulkDeleteAuthorityInstance(List<SecuredUUID> uuids) throws NotFoundException, ValidationException, ConnectorException;

    List<BulkActionMessageDto> forceDeleteAuthorityInstance(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;

    /**
     * Function to get the list of name and uuid dto for the objects available in the database.
     * @return List of NameAndUuidDto
     */
    List<NameAndUuidDto> listResourceObjects(SecurityFilter filter);
}
