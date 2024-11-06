package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.ConnectorAuthController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.core.service.ConnectorAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
public class ConnectorAuthControllerImpl implements ConnectorAuthController {

    private ConnectorAuthService connectorAuthService;

    @Autowired
    public void setConnectorAuthService(ConnectorAuthService connectorAuthService) {
        this.connectorAuthService = connectorAuthService;
    }

    @Override
    public Set<AuthType> getAuthenticationTypes() {
        return connectorAuthService.getAuthenticationTypes();
    }

    @Override
    public List<BaseAttribute> getBasicAuthAttributes() {
        return connectorAuthService.getBasicAuthAttributes();
    }

    @Override
    public void validateBasicAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateBasicAuthAttributes(attributes);
    }

    @Override
    public List<BaseAttribute> getCertificateAttributes() {
        return connectorAuthService.getCertificateAttributes();
    }

    @Override
    public void validateCertificateAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateCertificateAttributes(attributes);
    }

    @Override
    public List<BaseAttribute> getApiKeyAuthAttributes() {
        return connectorAuthService.getApiKeyAuthAttributes();
    }

    @Override
    public void validateApiKeyAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateApiKeyAuthAttributes(attributes);
    }

    @Override
    public List<BaseAttribute> getJWTAuthAttributes() {
        return connectorAuthService.getJWTAuthAttributes();
    }

    @Override
    public void validateJWTAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateJWTAuthAttributes(attributes);
    }
}
