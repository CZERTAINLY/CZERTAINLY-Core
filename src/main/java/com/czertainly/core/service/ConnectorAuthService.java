package com.czertainly.core.service;


import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.BaseAttributeV2;
import com.czertainly.api.model.common.attribute.v2.DataAttributeV2;
import com.czertainly.api.model.common.attribute.v3.DataAttributeV3;
import com.czertainly.api.model.core.connector.AuthType;

import java.util.List;
import java.util.Set;

public interface ConnectorAuthService {
    Set<AuthType> getAuthenticationTypes();

    List<DataAttributeV3> getAuthAttributes(AuthType authenticationType);

    boolean validateAuthAttributes(AuthType authenticationType, List<RequestAttributeDto> attributes);

    List<BaseAttribute> mergeAndValidateAuthAttributes(AuthType authenticationType, List<ResponseAttributeDto> attributes);

    List<DataAttributeV3> getBasicAuthAttributes();

    Boolean validateBasicAuthAttributes(List<RequestAttributeDto> attributes);

    List<DataAttributeV3> getCertificateAttributes();

    Boolean validateCertificateAttributes(List<RequestAttributeDto> attributes);

    List<DataAttributeV3> getApiKeyAuthAttributes();

    Boolean validateApiKeyAuthAttributes(List<RequestAttributeDto> attributes);

    List<DataAttributeV3> getJWTAuthAttributes();

    Boolean validateJWTAuthAttributes(List<RequestAttributeDto> attributes);
}
