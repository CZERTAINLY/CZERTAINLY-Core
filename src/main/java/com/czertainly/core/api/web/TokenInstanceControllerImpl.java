package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.TokenInstanceController;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.token.TokenInstanceRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
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
        return tokenInstanceService.listTokenInstances();
    }

    @Override
    public TokenInstanceDetailDto getTokenInstance(String uuid) throws NotFoundException {
        return tokenInstanceService.getTokenInstance(uuid);
    }

    @Override
    public TokenInstanceDetailDto createTokenInstance(TokenInstanceRequestDto request) throws AlreadyExistException, ValidationException {
        return tokenInstanceService.createTokenInstance(request);
    }

    @Override
    public TokenInstanceDetailDto updateTokenInstance(String uuid, TokenInstanceRequestDto request) throws NotFoundException, ValidationException {
        return tokenInstanceService.updateTokenInstance(uuid, request);
    }

    @Override
    public void deleteTokenInstance(String uuid) throws NotFoundException {
        tokenInstanceService.deleteTokenInstance(uuid);
    }

    @Override
    public void activateTokenInstance(String uuid, List<RequestAttributeDto> attributes) throws NotFoundException {
        tokenInstanceService.activateTokenInstance(uuid, attributes);
    }

    @Override
    public void deactivateTokenInstance(String uuid) throws NotFoundException {
        tokenInstanceService.deactivateTokenInstance(uuid);
    }

    @Override
    public void deleteTokenInstance(List<String> uuids) throws NotFoundException {
        tokenInstanceService.deleteTokenInstance(uuids);
    }

    @Override
    public TokenInstanceDetailDto reloadStatus(String uuid) throws NotFoundException {
        return tokenInstanceService.reloadStatus(uuid);
    }

    @Override
    public List<BaseAttribute> listTokenProfileAttributes(String uuid) throws NotFoundException {
        return tokenInstanceService.listTokenProfileAttributes(uuid);
    }

    @Override
    public List<BaseAttribute> listTokenInstanceActivationAttributes(String uuid) throws NotFoundException {
        return tokenInstanceService.listTokenInstanceActivationAttributes(uuid);
    }
}
