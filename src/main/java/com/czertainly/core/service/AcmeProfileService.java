package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface AcmeProfileService {

    List<AcmeProfileListDto> listAcmeProfile(SecurityFilter filter);

    AcmeProfileDto getAcmeProfile(SecuredUUID uuid) throws NotFoundException;

    AcmeProfileDto createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException;

    AcmeProfileDto updateAcmeProfile(SecuredUUID uuid, AcmeProfileEditRequestDto request) throws ConnectorException;

    List<ForceDeleteMessageDto> deleteAcmeProfile(SecuredUUID uuid) throws NotFoundException;

    void enableAcmeProfile(SecuredUUID uuid) throws NotFoundException;

    void disableAcmeProfile(SecuredUUID uuid) throws NotFoundException;

    void bulkEnableAcmeProfile(List<SecuredUUID> uuids);

    void bulkDisableAcmeProfile(List<SecuredUUID> uuids);

    List<ForceDeleteMessageDto> bulkDeleteAcmeProfile(List<SecuredUUID> uuids);

    void updateRaProfile(SecuredUUID uuid, String raProfileUuid) throws NotFoundException;

    void bulkForceRemoveACMEProfiles(List<SecuredUUID> uuids) throws NotFoundException, ValidationException;
}
