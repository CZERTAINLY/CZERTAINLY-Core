package com.czertainly.core.service;

import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.connector.AuthType;

import java.util.List;
import java.util.Set;

public interface ConnectorAuthService {
    Set<AuthType> getAuthenticationTypes();

    List<AttributeDefinition> getAuthAttributes(AuthType authenticationType);

    boolean validateAuthAttributes(AuthType authenticationType, List<AttributeDefinition> attributes);

    List<AttributeDefinition> mergeAndValidateAuthAttributes(AuthType authenticationType, List<AttributeDefinition> attributes);

    List<AttributeDefinition> getBasicAuthAttributes();

    Boolean validateBasicAuthAttributes(List<AttributeDefinition> attributes);

    List<AttributeDefinition> getCertificateAttributes();

    Boolean validateCertificateAttributes(List<AttributeDefinition> attributes);

    List<AttributeDefinition> getApiKeyAuthAttributes();

    Boolean validateApiKeyAuthAttributes(List<AttributeDefinition> attributes);

    List<AttributeDefinition> getJWTAuthAttributes();

    Boolean validateJWTAuthAttributes(List<AttributeDefinition> attributes);
}
