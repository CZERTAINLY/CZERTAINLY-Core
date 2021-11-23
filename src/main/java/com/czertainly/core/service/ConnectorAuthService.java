package com.czertainly.core.service;

import com.czertainly.api.model.AttributeDefinition;

import java.util.List;

public interface ConnectorAuthService {
    List<String> getAuthenticationTypes();

    List<AttributeDefinition> getBasicAuthAttributes();

    Boolean validateBasicAuthAttributes(List<AttributeDefinition> attributes);

    List<AttributeDefinition> getCertificateAttributes();

    Boolean validateCertificateAttributes(List<AttributeDefinition> attributes);

    List<AttributeDefinition> getApiKeyAuthAttributes();

    Boolean validateApiKeyAuthAttributes(List<AttributeDefinition> attributes);

    List<AttributeDefinition> getJWTAuthAttributes();

    Boolean validateJWTAuthAttributes(List<AttributeDefinition> attributes);
}
