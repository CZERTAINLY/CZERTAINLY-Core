package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.cryptography.token.TokenInstanceRequestDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
import com.czertainly.core.service.TokenInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TokenInstanceServiceImpl implements TokenInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(TokenInstanceServiceImpl.class);

    @Override
    public List<TokenInstanceDto> listTokenInstances() {
        return null;
    }

    @Override
    public TokenInstanceDetailDto getTokenInstance(String uuid) throws NotFoundException {
        return null;
    }

    @Override
    public TokenInstanceDetailDto createTokenInstance(TokenInstanceRequestDto request) throws AlreadyExistException, ValidationException {
        return null;
    }

    @Override
    public TokenInstanceDetailDto updateTokenInstance(String uuid, TokenInstanceRequestDto request) throws NotFoundException, ValidationException {
        return null;
    }

    @Override
    public void deleteTokenInstance(String uuid) throws NotFoundException {

    }

    @Override
    public void activateTokenInstance(String uuid, List<RequestAttributeDto> attributes) throws NotFoundException {

    }

    @Override
    public void deactivateTokenInstance(String uuid) throws NotFoundException {

    }

    @Override
    public void deleteTokenInstance(List<String> uuids) throws NotFoundException {

    }

    @Override
    public TokenInstanceDetailDto reloadStatus(String uuid) throws NotFoundException {
        return null;
    }

    @Override
    public List<BaseAttribute> listTokenProfileAttributes(String uuid) throws NotFoundException {
        return null;
    }

    @Override
    public List<BaseAttribute> listTokenInstanceActivationAttributes(String uuid) throws NotFoundException {
        return null;
    }
}
