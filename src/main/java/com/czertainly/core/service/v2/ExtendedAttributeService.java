package com.czertainly.core.service.v2;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;

import java.util.List;

public interface ExtendedAttributeService {
    List<BaseAttribute> listIssueCertificateAttributes(
            RaProfile raProfile) throws ConnectorException, NotFoundException;

    boolean validateIssueCertificateAttributes(
            RaProfile raProfile,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException, NotFoundException;

    List<BaseAttribute> listRevokeCertificateAttributes(
            RaProfile raProfile) throws ConnectorException, NotFoundException;

    boolean validateRevokeCertificateAttributes(
            RaProfile raProfile,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException, NotFoundException;

    void mergeAndValidateIssueAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException, AttributeException;

    void mergeAndValidateRevokeAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException, AttributeException;

    void validateLegacyConnector(Connector connector) throws NotFoundException;
}
