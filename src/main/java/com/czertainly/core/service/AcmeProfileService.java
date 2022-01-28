package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;

import java.util.List;

public interface AcmeProfileService {

    List<AcmeProfileListDto> listAcmeProfile();

    AcmeProfileDto getAcmeProfile(String uuid) throws NotFoundException;

    AcmeProfileDto createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException;

    AcmeProfileDto updateAcmeProfile(String uuid, AcmeProfileRequestDto request) throws ConnectorException;

    List<ForceDeleteMessageDto> deleteAcmeProfile(String uuid) throws NotFoundException;

    void enableAcmeProfile(String uuid) throws NotFoundException;

    void disableAcmeProfile(String uuid) throws NotFoundException;

    void bulkEnableAcmeProfile(List<String> uuids);

    void bulkDisableAcmeProfile(List<String> uuids);

    List<ForceDeleteMessageDto> bulkDeleteAcmeProfile(List<String> uuids);

    void updateRaProfile(String uuid, String raProfileUuid) throws NotFoundException;

    void bulkForceRemoveACMEProfiles(List<String> uuids) throws NotFoundException, ValidationException;
}
