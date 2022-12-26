package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.TokenProfileController;
import com.czertainly.api.model.client.cryptography.tokenprofile.AddTokenProfileRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.EditTokenProfileRequestDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDto;
import com.czertainly.core.service.TokenProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
public class TokenProfileControllerImpl implements TokenProfileController {

    private TokenProfileService tokenProfileService;

    @Autowired
    public void setTokenProfileService(TokenProfileService tokenProfileService) {
        this.tokenProfileService = tokenProfileService;
    }

    @Override
    public List<TokenProfileDto> listTokenProfiles(Optional<Boolean> enabled) {
        return tokenProfileService.listTokenProfiles(enabled);
    }

    @Override
    public TokenProfileDetailDto getTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        return tokenProfileService.getTokenProfile(tokenInstanceUuid, uuid);
    }

    @Override
    public ResponseEntity<TokenProfileDetailDto> createTokenProfile(String tokenInstanceUuid, AddTokenProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException {
        TokenProfileDetailDto tokenProfileDetailDto = tokenProfileService.createTokenProfile(tokenInstanceUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/tokenInstances/{tokenInstanceUuid}/tokenProfiles/{uuid}")
                .buildAndExpand(tokenInstanceUuid, tokenProfileDetailDto.getUuid()).toUri();
        return ResponseEntity.created(location).body(tokenProfileDetailDto);
    }

    @Override
    public TokenProfileDetailDto editTokenProfile(String tokenInstanceUuid, String uuid, EditTokenProfileRequestDto request) throws ConnectorException {
        return tokenProfileService.editTokenProfile(tokenInstanceUuid, uuid, request);
    }

    @Override
    public void deleteTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        tokenProfileService.deleteTokenProfile(tokenInstanceUuid, uuid);
    }

    @Override
    public void deleteTokenProfile(String tokenProfileUuid) throws NotFoundException {
        tokenProfileService.deleteTokenProfile(tokenProfileUuid);
    }

    @Override
    public void disableTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        tokenProfileService.disableTokenProfile(tokenInstanceUuid, uuid);
    }

    @Override
    public void enableTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        tokenProfileService.enableTokenProfile(tokenInstanceUuid, uuid);
    }

    @Override
    public void bulkDeleteTokenProfile(List<String> uuids) throws NotFoundException, ValidationException {
        tokenProfileService.bulkDeleteTokenProfile(uuids);
    }

    @Override
    public void bulkDisableRaProfile(List<String> uuids) throws NotFoundException {
        tokenProfileService.bulkDisableRaProfile(uuids);
    }

    @Override
    public void bulkEnableRaProfile(List<String> uuids) throws NotFoundException {
        tokenProfileService.bulkEnableRaProfile(uuids);
    }
}
