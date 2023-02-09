package com.czertainly.core.service;


import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.core.connector.AuthType;

import java.util.List;
import java.util.Set;

public interface ConnectorAuthService {
    Set<AuthType> getAuthenticationTypes();

    List<BaseAttribute> getAuthAttributes(AuthType authenticationType);

    boolean validateAuthAttributes(AuthType authenticationType, List<RequestAttributeDto> attributes);

    List<DataAttribute> mergeAndValidateAuthAttributes(AuthType authenticationType, List<ResponseAttributeDto> attributes);

    List<BaseAttribute> getBasicAuthAttributes();

    Boolean validateBasicAuthAttributes(List<RequestAttributeDto> attributes);

    List<BaseAttribute> getCertificateAttributes();

    Boolean validateCertificateAttributes(List<RequestAttributeDto> attributes);

    List<BaseAttribute> getApiKeyAuthAttributes();

    Boolean validateApiKeyAuthAttributes(List<RequestAttributeDto> attributes);

    List<BaseAttribute> getJWTAuthAttributes();

    Boolean validateJWTAuthAttributes(List<RequestAttributeDto> attributes);
}
