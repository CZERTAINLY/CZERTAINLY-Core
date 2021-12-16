package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.NameAndIdDto;
import com.czertainly.api.model.ca.CAInstanceDto;
import com.czertainly.api.model.ca.CAInstanceRequestDto;
import com.czertainly.api.model.connector.ForceDeleteMessageDto;

import java.util.List;

public interface CAInstanceService {
    List<CAInstanceDto> listCAInstances();

    CAInstanceDto getCAInstance(String uuid) throws NotFoundException, ConnectorException;

    CAInstanceDto createCAInstance(CAInstanceRequestDto request) throws AlreadyExistException, NotFoundException, ConnectorException;

    CAInstanceDto updateCAInstance(String uuid, CAInstanceRequestDto request) throws NotFoundException, ConnectorException;

    void removeCAInstance(String uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listEndEntityProfiles(String uuid) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCertificateProfiles(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<NameAndIdDto> listCAsInProfile(String uuid, Integer endEntityProfileId) throws NotFoundException, ConnectorException;

    List<AttributeDefinition> listRAProfileAttributes(String uuid) throws NotFoundException, ConnectorException;

    Boolean validateRAProfileAttributes(String uuid, List<AttributeDefinition> attributes) throws NotFoundException, ConnectorException;

    List<ForceDeleteMessageDto> bulkRemoveCaInstance(List<String> uuids) throws NotFoundException, ValidationException, ConnectorException;

    void bulkForceRemoveCaInstance(List<String> uuids) throws ValidationException, NotFoundException;
}
