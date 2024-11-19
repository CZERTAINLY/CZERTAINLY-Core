package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.TokenProfileController;
import com.czertainly.api.model.client.cryptography.key.BulkKeyUsageRequestDto;
import com.czertainly.api.model.client.cryptography.key.UpdateKeyUsageRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.AddTokenProfileRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.BulkTokenProfileKeyUsageRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.EditTokenProfileRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.TokenProfileKeyUsageRequestDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.TokenProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class TokenProfileControllerImpl implements TokenProfileController {

    private TokenProfileService tokenProfileService;

    @Autowired
    public void setTokenProfileService(TokenProfileService tokenProfileService) {
        this.tokenProfileService = tokenProfileService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.TOKEN_PROFILE)
    public List<TokenProfileDto> listTokenProfiles(Optional<Boolean> enabled) {
        return tokenProfileService.listTokenProfiles(enabled, SecurityFilter.create());
    }

    @Override
    public TokenProfileDetailDto getTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        return tokenProfileService.getTokenProfile(SecuredParentUUID.fromString(tokenInstanceUuid), SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<TokenProfileDetailDto> createTokenProfile(String tokenInstanceUuid, AddTokenProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException, AttributeException {
        TokenProfileDetailDto tokenProfileDetailDto = tokenProfileService.createTokenProfile(SecuredParentUUID.fromString(tokenInstanceUuid), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/tokenInstances/{tokenInstanceUuid}/tokenProfiles/{uuid}")
                .buildAndExpand(tokenInstanceUuid, tokenProfileDetailDto.getUuid()).toUri();
        return ResponseEntity.created(location).body(tokenProfileDetailDto);
    }

    @Override
    public TokenProfileDetailDto editTokenProfile(String tokenInstanceUuid, String uuid, EditTokenProfileRequestDto request) throws ConnectorException, AttributeException {
        return tokenProfileService.editTokenProfile(SecuredParentUUID.fromString(tokenInstanceUuid), SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        tokenProfileService.deleteTokenProfile(SecuredParentUUID.fromString(tokenInstanceUuid), SecuredUUID.fromString(uuid));
    }

    @Override
    public void deleteTokenProfile(String uuid) throws NotFoundException {
        tokenProfileService.deleteTokenProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public void disableTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        tokenProfileService.disableTokenProfile(SecuredParentUUID.fromString(tokenInstanceUuid), SecuredUUID.fromString(uuid));
    }

    @Override
    public void enableTokenProfile(String tokenInstanceUuid, String uuid) throws NotFoundException {
        tokenProfileService.enableTokenProfile(SecuredParentUUID.fromString(tokenInstanceUuid), SecuredUUID.fromString(uuid));
    }

    @Override
    public void deleteTokenProfiles(List<String> uuids){
        tokenProfileService.deleteTokenProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void disableTokenProfiles(List<String> uuids) {
        tokenProfileService.disableTokenProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void enableTokenProfiles(List<String> uuids) {
        tokenProfileService.enableTokenProfile(SecuredUUID.fromList(uuids));
    }

    @Override
    public void updateKeyUsages(String tokenInstanceUuid, String tokenProfileUuid, TokenProfileKeyUsageRequestDto request) throws NotFoundException, ValidationException {
        tokenProfileService.updateKeyUsages(
                SecuredParentUUID.fromString(tokenInstanceUuid),
                SecuredUUID.fromString(tokenProfileUuid),
                request.getUsage()
        );
    }

    @Override
    public void updateKeysUsages(BulkTokenProfileKeyUsageRequestDto request){
        tokenProfileService.updateKeyUsages(
                SecuredUUID.fromUuidList(request.getUuids()),
                request.getUsage()
        );
    }
}
