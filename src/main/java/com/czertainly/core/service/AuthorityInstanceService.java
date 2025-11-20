package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceRequestDto;
import com.czertainly.api.model.client.authority.AuthorityInstanceUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndIdDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface AuthorityInstanceService extends ResourceExtensionService {
    List<AuthorityInstanceDto> listAuthorityInstances(SecurityFilter filter);

    AuthorityInstanceDto getAuthorityInstance(SecuredUUID uuid) throws ConnectorException, NotFoundException;

    AuthorityInstanceDto createAuthorityInstance(AuthorityInstanceRequestDto request) throws AlreadyExistException, ConnectorException, AttributeException, NotFoundException;

    AuthorityInstanceDto editAuthorityInstance(SecuredUUID uuid, AuthorityInstanceUpdateRequestDto request) throws ConnectorException, AttributeException, NotFoundException;

    void deleteAuthorityInstance(SecuredUUID uuid) throws ConnectorException, NotFoundException;

    List<NameAndIdDto> listEndEntityProfiles(SecuredUUID uuid) throws ConnectorException, NotFoundException;

    List<NameAndIdDto> listCertificateProfiles(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException, NotFoundException;

    List<NameAndIdDto> listCAsInProfile(SecuredUUID uuid, Integer endEntityProfileId) throws ConnectorException, NotFoundException;

    List<BaseAttribute> listRAProfileAttributes(SecuredUUID uuid) throws ConnectorException, NotFoundException;

    Boolean validateRAProfileAttributes(SecuredUUID uuid, List<RequestAttributeDto> attributes) throws ConnectorException, NotFoundException;

    List<BulkActionMessageDto> bulkDeleteAuthorityInstance(List<SecuredUUID> uuids) throws ValidationException, ConnectorException;

    List<BulkActionMessageDto> forceDeleteAuthorityInstance(List<SecuredUUID> uuids) throws ValidationException, NotFoundException;
}
