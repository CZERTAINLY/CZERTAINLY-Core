package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface AcmeProfileService extends ResourceExtensionService {

    List<AcmeProfileListDto> listAcmeProfile(SecurityFilter filter);

    AcmeProfileDto getAcmeProfile(SecuredUUID uuid) throws NotFoundException;

    AcmeProfileDto createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException;

    AcmeProfileDto editAcmeProfile(SecuredUUID uuid, AcmeProfileEditRequestDto request) throws ConnectorException, AttributeException;

    void deleteAcmeProfile(SecuredUUID uuid) throws NotFoundException, ValidationException;

    void enableAcmeProfile(SecuredUUID uuid) throws NotFoundException;

    void disableAcmeProfile(SecuredUUID uuid) throws NotFoundException;

    void bulkEnableAcmeProfile(List<SecuredUUID> uuids);

    void bulkDisableAcmeProfile(List<SecuredUUID> uuids);

    List<BulkActionMessageDto> bulkDeleteAcmeProfile(List<SecuredUUID> uuids);

    void updateRaProfile(SecuredUUID uuid, String raProfileUuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkForceRemoveACMEProfiles(List<SecuredUUID> uuids) throws NotFoundException, ValidationException;
}
