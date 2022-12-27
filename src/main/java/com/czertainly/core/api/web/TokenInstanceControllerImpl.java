package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.TokenInstanceController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.token.TokenInstanceRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
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
    public List<TokenInstanceDto> listTokenInstances() {
        return tokenInstanceService.listTokenInstances(SecurityFilter.create());
    }

    @Override
    public TokenInstanceDetailDto getTokenInstance(String uuid) throws ConnectorException {
        return tokenInstanceService.getTokenInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    public TokenInstanceDetailDto createTokenInstance(TokenInstanceRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        return tokenInstanceService.createTokenInstance(request);
    }

    @Override
    public TokenInstanceDetailDto updateTokenInstance(String uuid, TokenInstanceRequestDto request) throws ConnectorException, ValidationException {
        return tokenInstanceService.updateTokenInstance(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteTokenInstance(String uuid) throws NotFoundException {
        tokenInstanceService.deleteTokenInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    public void activateTokenInstance(String uuid, List<RequestAttributeDto> attributes) throws ConnectorException {
        tokenInstanceService.activateTokenInstance(SecuredUUID.fromString(uuid), attributes);
    }

    @Override
    public void deactivateTokenInstance(String uuid) throws ConnectorException {
        tokenInstanceService.deactivateTokenInstance(SecuredUUID.fromString(uuid));
    }

    @Override
    public void deleteTokenInstance(List<String> uuids) {
        tokenInstanceService.deleteTokenInstance(SecuredUUID.fromList(uuids));
    }

    @Override
    public TokenInstanceDetailDto reloadStatus(String uuid) throws ConnectorException {
        return tokenInstanceService.reloadStatus(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<BaseAttribute> listTokenProfileAttributes(String uuid) throws ConnectorException {
        return tokenInstanceService.listTokenProfileAttributes(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<BaseAttribute> listTokenInstanceActivationAttributes(String uuid) throws ConnectorException {
        return tokenInstanceService.listTokenInstanceActivationAttributes(SecuredUUID.fromString(uuid));
    }
}
