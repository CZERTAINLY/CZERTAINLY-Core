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
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface AuthorityInstanceService extends ResourceExtensionService {
    List<AuthorityInstanceDto> listAuthorityInstances(SecurityFilter filter);

    AuthorityInstanceDto getAuthorityInstance(SecuredUUID uuid) throws ConnectorException;

    AuthorityInstanceDto createAuthorityInstance(AuthorityInstanceRequestDto request) throws AlreadyExistException, ConnectorException;

    AuthorityInstanceDto editAuthorityInstance(SecuredUUID uuid, AuthorityInstanceUpdateRequestDto request) throws ConnectorException;

    void deleteAuthorityInstance(SecuredUUID uuid) throws ConnectorException;

    List<NameAndIdDto> listEndEntityProfiles(SecuredUUID uuid) throws ConnectorException;

    List<NameAndIdDto> listCertificateProfiles(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException;

    List<NameAndIdDto> listCAsInProfile(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException;

    List<BaseAttribute> listRAProfileAttributes(SecuredUUID uuid) throws ConnectorException;

    Boolean validateRAProfileAttributes(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException;

    List<BulkActionMessageDto> bulkDeleteAuthorityInstance(List<SecuredUUID> uuids) throws ValidationException, ConnectorException;

    List<BulkActionMessageDto> forceDeleteAuthorityInstance(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;
}
