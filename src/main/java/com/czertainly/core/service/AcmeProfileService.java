package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.acme.AcmeProfileEditRequestDto;
import com.czertainly.api.model.client.acme.AcmeProfileRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.acme.AcmeProfileDto;
import com.czertainly.api.model.core.acme.AcmeProfileListDto;

import java.util.List;

public interface AcmeProfileService {

    List<AcmeProfileListDto> listAcmeProfile();

    AcmeProfileDto getAcmeProfile(String uuid) throws NotFoundException;

    AcmeProfileDto createAcmeProfile(AcmeProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException;

    AcmeProfileDto updateAcmeProfile(String uuid, AcmeProfileEditRequestDto request) throws ConnectorException;

    void deleteAcmeProfile(String uuid) throws NotFoundException, ValidationException;

    void enableAcmeProfile(String uuid) throws NotFoundException;

    void disableAcmeProfile(String uuid) throws NotFoundException;

    void bulkEnableAcmeProfile(List<String> uuids);

    void bulkDisableAcmeProfile(List<String> uuids);

    List<BulkActionMessageDto> bulkDeleteAcmeProfile(List<String> uuids);

    void updateRaProfile(String uuid, String raProfileUuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkForceRemoveACMEProfiles(List<String> uuids) throws NotFoundException, ValidationException;
}
