package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.ConnectorAuthController;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.core.service.ConnectorAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

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
    public Boolean validateBasicAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        return connectorAuthService.validateBasicAuthAttributes(attributes);
    }

    @Override
    public List<AttributeDefinition> getCertificateAttributes() {
        return connectorAuthService.getCertificateAttributes();
    }

    @Override
    public Boolean validateCertificateAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        return connectorAuthService.validateCertificateAttributes(attributes);
    }

    @Override
    public List<AttributeDefinition> getApiKeyAuthAttributes() {
        return connectorAuthService.getApiKeyAuthAttributes();
    }

    @Override
    public Boolean validateApiKeyAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        return connectorAuthService.validateApiKeyAuthAttributes(attributes);
    }

    @Override
    public List<AttributeDefinition> getJWTAuthAttributes() {
        return connectorAuthService.getJWTAuthAttributes();
    }

    @Override
    public Boolean validateJWTAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        return connectorAuthService.validateJWTAuthAttributes(attributes);
    }
}
