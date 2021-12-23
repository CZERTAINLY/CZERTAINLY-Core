package com.czertainly.core.api.web;

import java.util.List;
import java.util.Set;

import com.czertainly.api.model.ClientAttributeDefinition;
import com.czertainly.api.model.connector.AuthType;
import com.czertainly.core.service.ConnectorAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.czertainly.api.core.interfaces.web.ConnectorAuthController;
import com.czertainly.api.model.AttributeDefinition;

@RestController
public class ConnectorAuthControllerImpl implements ConnectorAuthController {

    @Autowired
    private ConnectorAuthService connectorAuthService;

    @Override
    public Set<AuthType> getAuthenticationTypes() {
        return connectorAuthService.getAuthenticationTypes();
    }

    @Override
    public List<AttributeDefinition> getBasicAuthAttributes() {
        return connectorAuthService.getBasicAuthAttributes();
    }

    @Override
    public Boolean validateBasicAuthAttributes(@RequestBody List<ClientAttributeDefinition> attributes) {
        return connectorAuthService.validateBasicAuthAttributes(attributes);
    }

    @Override
    public List<AttributeDefinition> getCertificateAttributes() {
        return connectorAuthService.getCertificateAttributes();
    }

    @Override
    public Boolean validateCertificateAttributes(@RequestBody List<ClientAttributeDefinition> attributes) {
        return connectorAuthService.validateCertificateAttributes(attributes);
    }

    @Override
    public List<AttributeDefinition> getApiKeyAuthAttributes() {
        return connectorAuthService.getApiKeyAuthAttributes();
    }

    @Override
    public Boolean validateApiKeyAuthAttributes(@RequestBody List<ClientAttributeDefinition> attributes) {
        return connectorAuthService.validateApiKeyAuthAttributes(attributes);
    }

    @Override
    public List<AttributeDefinition> getJWTAuthAttributes() {
        return connectorAuthService.getJWTAuthAttributes();
    }

    @Override
    public Boolean validateJWTAuthAttributes(@RequestBody List<ClientAttributeDefinition> attributes) {
        return connectorAuthService.validateJWTAuthAttributes(attributes);
    }
}
