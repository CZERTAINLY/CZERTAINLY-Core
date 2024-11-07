package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.ConnectorAuthController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.AuthType;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
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
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authBasic", affiliatedResource = Resource.CONNECTOR, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> getBasicAuthAttributes() {
        return connectorAuthService.getBasicAuthAttributes();
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authBasic", affiliatedResource = Resource.CONNECTOR, operation = Operation.VALIDATE_ATTRIBUTES)
    public void validateBasicAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateBasicAuthAttributes(attributes);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authCertificate", affiliatedResource = Resource.CONNECTOR, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> getCertificateAttributes() {
        return connectorAuthService.getCertificateAttributes();
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authCertificate", affiliatedResource = Resource.CONNECTOR, operation = Operation.VALIDATE_ATTRIBUTES)
    public void validateCertificateAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateCertificateAttributes(attributes);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authApiKey", affiliatedResource = Resource.CONNECTOR, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> getApiKeyAuthAttributes() {
        return connectorAuthService.getApiKeyAuthAttributes();
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authApiKey", affiliatedResource = Resource.CONNECTOR, operation = Operation.VALIDATE_ATTRIBUTES)
    public void validateApiKeyAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateApiKeyAuthAttributes(attributes);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authJwt", affiliatedResource = Resource.CONNECTOR, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> getJWTAuthAttributes() {
        return connectorAuthService.getJWTAuthAttributes();
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.ATTRIBUTE, name = "authJwt", affiliatedResource = Resource.CONNECTOR, operation = Operation.VALIDATE_ATTRIBUTES)
    public void validateJWTAuthAttributes(@RequestBody List<RequestAttributeDto> attributes) {
        connectorAuthService.validateJWTAuthAttributes(attributes);
    }
}
