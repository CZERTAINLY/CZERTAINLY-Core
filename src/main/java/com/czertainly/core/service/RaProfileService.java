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
import com.czertainly.api.model.client.raprofile.RaProfileComplianceCheckDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.RaProfile;

import java.util.List;
import java.util.Optional;

public interface RaProfileService {

    List<RaProfileDto> listRaProfiles(Optional<Boolean> enabled);

    RaProfileDto addRaProfile(AddRaProfileRequestDto dto) throws AlreadyExistException, ValidationException, NotFoundException, ConnectorException;

    RaProfileDto getRaProfile(String uuid) throws NotFoundException;

    RaProfile getRaProfileEntity(String uuid) throws NotFoundException;

    RaProfileDto editRaProfile(String uuid, EditRaProfileRequestDto dto) throws NotFoundException, ConnectorException;

    void deleteRaProfile(String uuid) throws NotFoundException;

    List<SimplifiedClientDto> listClients(String uuid) throws NotFoundException;

    void enableRaProfile(String uuid) throws NotFoundException;

    void disableRaProfile(String uuid) throws NotFoundException;

    void bulkDeleteRaProfile(List<String> uuids);

    void bulkDisableRaProfile(List<String> uuids);

    void bulkEnableRaProfile(List<String> uuids);

    RaProfileAcmeDetailResponseDto getAcmeForRaProfile(String uuid) throws NotFoundException;

    RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(String uuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException;

    void deactivateAcmeForRaProfile(String uuid) throws NotFoundException;

    List<AttributeDefinition> listRevokeCertificateAttributes(String uuid) throws NotFoundException, ConnectorException;

    List<AttributeDefinition> listIssueCertificateAttributes(String uuid) throws NotFoundException, ConnectorException;

    /**
     * Save the RA Profile entity to the database
     * @param raProfile RA profile entity
     * @return RA Profile Entity
     */
    RaProfile updateRaProfileEntity(RaProfile raProfile);

    /**
     * Check the compliance for all the certificates associated with the RA Profile
     * @param request DTO containing the list of RA Profile UUIDs for which the request has to be triggered
     */
    void checkCompliance(RaProfileComplianceCheckDto request);
}
