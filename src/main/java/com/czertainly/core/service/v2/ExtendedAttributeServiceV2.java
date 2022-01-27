package com.czertainly.core.service.v2;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;

import java.util.List;

public interface ExtendedAttributeServiceV2 {
    List<AttributeDefinition> listIssueCertificateAttributes(
            RaProfile raProfileUuid) throws NotFoundException, ConnectorException;

    boolean validateIssueCertificateAttributes(
            RaProfile raProfileUuid,
            List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException;

    List<AttributeDefinition> listRevokeCertificateAttributes(
            RaProfile raProfileUuid) throws NotFoundException, ConnectorException;

    boolean validateRevokeCertificateAttributes(
            RaProfile raProfileUuid,
            List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException;

    List<AttributeDefinition> mergeAndValidateIssueAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException;

    List<AttributeDefinition> mergeAndValidateRevokeAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException;

    void validateLegacyConnector(Connector connector) throws NotFoundException;
}
