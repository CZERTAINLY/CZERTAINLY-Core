package com.czertainly.core.service.v2;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;

import java.util.List;

public interface ExtendedAttributeService {
    List<BaseAttribute> listIssueCertificateAttributes(
            RaProfile raProfileUuid) throws ConnectorException;

    boolean validateIssueCertificateAttributes(
            RaProfile raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    List<BaseAttribute> listRevokeCertificateAttributes(
            RaProfile raProfileUuid) throws ConnectorException;

    boolean validateRevokeCertificateAttributes(
            RaProfile raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    List<DataAttribute> mergeAndValidateIssueAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException;

    List<DataAttribute> mergeAndValidateRevokeAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException;

    void validateLegacyConnector(Connector connector) throws NotFoundException;
}
