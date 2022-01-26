package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.client.SimplifiedClientDto;
import com.czertainly.api.model.client.raprofile.ActivateAcmeForRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.RaProfileAcmeDetailResponseDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;

import java.util.List;

public interface RaProfileService {

    List<RaProfileDto> listRaProfiles();

    List<RaProfileDto> listRaProfiles(Boolean isEnabled);

    RaProfileDto addRaProfile(AddRaProfileRequestDto dto) throws AlreadyExistException, ValidationException, NotFoundException, ConnectorException;

    RaProfileDto getRaProfile(String uuid) throws NotFoundException;

    RaProfileDto editRaProfile(String uuid, EditRaProfileRequestDto dto) throws NotFoundException, ConnectorException;

    void removeRaProfile(String uuid) throws NotFoundException;

    List<SimplifiedClientDto> listClients(String uuid) throws NotFoundException;

    void enableRaProfile(String uuid) throws NotFoundException;

    void disableRaProfile(String uuid) throws NotFoundException;

    void bulkRemoveRaProfile(List<String> uuids);

    void bulkDisableRaProfile(List<String> uuids);

    void bulkEnableRaProfile(List<String> uuids);

    RaProfileAcmeDetailResponseDto getAcmeForRaProfile(String uuid) throws NotFoundException;

    RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(String uuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException;

    void deactivateAcmeForRaProfile(String uuid) throws NotFoundException;
}
