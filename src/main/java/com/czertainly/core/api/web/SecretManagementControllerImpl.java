package com.czertainly.core.api.web;

import com.czertainly.api.interfaces.core.web.SecretManagementController;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.secret.*;
import com.czertainly.api.model.core.secret.content.SecretContentDto;
import jdk.jfr.Registered;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;


@RestController
public class SecretManagementControllerImpl implements SecretManagementController {

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return List.of();
    }

    @Override
    public List<SecretListResponseDto> listSecrets(SearchRequestDto searchRequest) {
        return List.of();
    }

    @Override
    public SecretDetailDto getSecretDetails(UUID uuid) {
        return null;
    }

    @Override
    public List<SecretVersionDto> getSecretVersions(UUID uuid) {
        return List.of();
    }

    @Override
    public SecretContentDto getSecretContent(UUID uuid) {
        return null;
    }

    @Override
    public SecretDetailDto createSecret(SecretRequestDto secretRequest, UUID vaultProfileUuid, UUID vaultUuid) {
        return null;
    }

    @Override
    public SecretDetailDto updateSecret(UUID uuid, SecretUpdateRequestDto secretRequest) {
        return null;
    }

    @Override
    public void deleteSecret(UUID uuid) {

    }

    @Override
    public void enableSecret(UUID uuid) {

    }

    @Override
    public void disableSecret(UUID uuid) {

    }

    @Override
    public void addVaultProfileToSecret(UUID uuid, UUID vaultProfileUuid) {

    }

    @Override
    public void removeVaultProfileFromSecret(UUID uuid, UUID vaultProfileUuid) {

    }
}
