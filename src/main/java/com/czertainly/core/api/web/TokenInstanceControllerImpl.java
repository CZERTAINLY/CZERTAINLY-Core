package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.TokenInstanceController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.token.TokenInstanceRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.TokenInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TokenInstanceControllerImpl implements TokenInstanceController {

    private TokenInstanceService tokenInstanceService;

    @Autowired
    public void setTokenInstanceService(TokenInstanceService tokenInstanceService) {
        this.tokenInstanceService = tokenInstanceService;
    }


    @Override
    @AuthEndpoint(resourceName = Resource.TOKEN)
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.LIST)
    public List<TokenInstanceDto> listTokenInstances() {
        return tokenInstanceService.listTokenInstances(SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.DETAIL)
    public TokenInstanceDetailDto getTokenInstance(@LogResource(uuid = true) String uuid) throws ConnectorException, NotFoundException {
        return tokenInstanceService.getTokenInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.CREATE)
    public TokenInstanceDetailDto createTokenInstance(TokenInstanceRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException, NotFoundException {
        return tokenInstanceService.createTokenInstance(request);
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.UPDATE)
    public TokenInstanceDetailDto updateTokenInstance(@LogResource(uuid = true) String uuid, TokenInstanceRequestDto request) throws ConnectorException, ValidationException, AttributeException, NotFoundException {
        return tokenInstanceService.updateTokenInstance(SecuredUUID.fromString(uuid), request);
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.DELETE)
    public void deleteTokenInstance(@LogResource(uuid = true) String uuid) throws NotFoundException {
        tokenInstanceService.deleteTokenInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.ACTIVATE)
    public void activateTokenInstance(@LogResource(uuid = true) String uuid, List<RequestAttributeDto> attributes) throws ConnectorException, NotFoundException {
        tokenInstanceService.activateTokenInstance(SecuredUUID.fromString(uuid), attributes);
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.DEACTIVATE)
    public void deactivateTokenInstance(@LogResource(uuid = true) String uuid) throws ConnectorException, NotFoundException {
        tokenInstanceService.deactivateTokenInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.DELETE)
    public void deleteTokenInstance(@LogResource(uuid = true) List<String> uuids) {
        tokenInstanceService.deleteTokenInstance(SecuredUUID.fromList(uuids));
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.TOKEN, operation = Operation.GET_STATUS)
    public TokenInstanceDetailDto reloadStatus(@LogResource(uuid = true) String uuid) throws ConnectorException, NotFoundException {
        return tokenInstanceService.reloadStatus(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.ATTRIBUTE, name = "tokenProfile", affiliatedResource = Resource.TOKEN, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> listTokenProfileAttributes(@LogResource(uuid = true, affiliated = true) String uuid) throws ConnectorException, NotFoundException {
        return tokenInstanceService.listTokenProfileAttributes(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CRYPTOGRAPHIC_KEYS, resource = Resource.ATTRIBUTE, name = "activate", affiliatedResource = Resource.TOKEN, operation = Operation.LIST_ATTRIBUTES)
    public List<BaseAttribute> listTokenInstanceActivationAttributes(@LogResource(uuid = true, affiliated = true) String uuid) throws ConnectorException, NotFoundException {
        return tokenInstanceService.listTokenInstanceActivationAttributes(SecuredUUID.fromString(uuid));
    }
}
