package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.ConnectorAuthController;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
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
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.LIST_AUTHENTICATION_TYPES)
    public Set<AuthType> getAuthenticationTypes() {
        return connectorAuthService.getAuthenticationTypes();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.LIST_ATTRIBUTES)
    public List<AttributeDefinition> getBasicAuthAttributes() {
        return connectorAuthService.getBasicAuthAttributes();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.VALIDATE_ATTRIBUTES)
    public void validateBasicAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateBasicAuthAttributes(attributes);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.LIST_ATTRIBUTES)
    public List<AttributeDefinition> getCertificateAttributes() {
        return connectorAuthService.getCertificateAttributes();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.VALIDATE_ATTRIBUTES)
    public void validateCertificateAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateCertificateAttributes(attributes);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.LIST_ATTRIBUTES)
    public List<AttributeDefinition> getApiKeyAuthAttributes() {
        return connectorAuthService.getApiKeyAuthAttributes();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.VALIDATE_ATTRIBUTES)
    public void validateApiKeyAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateApiKeyAuthAttributes(attributes);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.LIST_ATTRIBUTES)
    public List<AttributeDefinition> getJWTAuthAttributes() {
        return connectorAuthService.getJWTAuthAttributes();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.CONNECTOR, actionName = ActionName.VALIDATE_ATTRIBUTES)
    public void validateJWTAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateJWTAuthAttributes(attributes);
    }
}
