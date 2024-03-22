package com.czertainly.core.service.v2;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;

import java.util.List;
import java.util.UUID;

public interface ExtendedAttributeService {
    List<BaseAttribute> listIssueCertificateAttributes(
            RaProfile raProfile) throws ConnectorException;

    boolean validateIssueCertificateAttributes(
            RaProfile raProfile,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    List<BaseAttribute> listRevokeCertificateAttributes(
            RaProfile raProfile) throws ConnectorException;

    boolean validateRevokeCertificateAttributes(
            RaProfile raProfile,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    void mergeAndValidateIssueAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException, AttributeException;

    void mergeAndValidateRevokeAttributes(RaProfile raProfile, List<RequestAttributeDto> attributes) throws ConnectorException, AttributeException;

    void validateLegacyConnector(Connector connector) throws NotFoundException;
}
