package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.tokenprofile.AddTokenProfileRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.EditTokenProfileRequestDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDto;
import com.czertainly.core.service.TokenProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TokenProfileServiceImpl implements TokenProfileService {

    private static final Logger logger = LoggerFactory.getLogger(TokenProfileServiceImpl.class);


    @Override
    public List<TokenProfileDto> listTokenProfiles(Optional<Boolean> enabled) {
        return null;
    }

    @Override
    public TokenProfileDetailDto getTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        return null;
    }

    @Override
    public TokenProfileDetailDto createTokenProfile(String tokenInstanceUuid, AddTokenProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        return null;
    }

    @Override
    public TokenProfileDetailDto editTokenProfile(String tokenInstanceUuid, String uuid, EditTokenProfileRequestDto request) throws ConnectorException {
        return null;
    }

    @Override
    public void deleteTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {

    }

    @Override
    public void deleteTokenProfile(String tokenProfileUuid) throws NotFoundException {

    }

    @Override
    public void disableTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {

    }

    @Override
    public void enableTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {

    }

    @Override
    public void bulkDeleteTokenProfile(List<String> uuids) throws NotFoundException, ValidationException {

    }

    @Override
    public void bulkDisableRaProfile(List<String> uuids) throws NotFoundException {

    }

    @Override
    public void bulkEnableRaProfile(List<String> uuids) throws NotFoundException {

    }
}
