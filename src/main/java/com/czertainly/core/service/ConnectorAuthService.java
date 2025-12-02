package com.czertainly.core.service;


import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.v3.DataAttributeV3;
import com.czertainly.api.model.core.connector.AuthType;

import java.util.List;
import java.util.Set;

public interface ConnectorAuthService {
    Set<AuthType> getAuthenticationTypes();

    List<DataAttributeV3> getAuthAttributes(AuthType authenticationType);

    boolean validateAuthAttributes(AuthType authenticationType, List<RequestAttribute> attributes);

    List<BaseAttribute> mergeAndValidateAuthAttributes(AuthType authenticationType, List<ResponseAttribute> attributes);

    List<DataAttributeV3> getBasicAuthAttributes();

    Boolean validateBasicAuthAttributes(List<RequestAttribute> attributes);

    List<DataAttributeV3> getCertificateAttributes();

    Boolean validateCertificateAttributes(List<RequestAttribute> attributes);

    List<DataAttributeV3> getApiKeyAuthAttributes();

    Boolean validateApiKeyAuthAttributes(List<RequestAttribute> attributes);

    List<DataAttributeV3> getJWTAuthAttributes();

    Boolean validateJWTAuthAttributes(List<RequestAttribute> attributes);
}
