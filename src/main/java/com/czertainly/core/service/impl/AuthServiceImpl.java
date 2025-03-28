package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.AuthResourceDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserProfileDto;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.AuthService;
import com.czertainly.core.service.UserManagementService;
import com.czertainly.core.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;
import java.util.List;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private UserManagementApiClient userManagementApiClient;
    private ResourceApiClient resourceApiClient;
    private UserManagementService userManagementService;

    @Autowired
    public void setUserManagementApiClient(UserManagementApiClient userManagementApiClient) {
        this.userManagementApiClient = userManagementApiClient;
    }

    @Autowired
    public void setResourceApiClient(ResourceApiClient resourceApiClient) {
        this.resourceApiClient = resourceApiClient;
    }

    @Autowired
    public void setUserManagementService(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @Override
    public UserDetailDto getAuthProfile() {
        UserProfileDto userProfileDto = AuthHelper.getUserProfile();
        return userManagementApiClient.getUserDetail(userProfileDto.getUser().getUuid());
    }

    @Override
    public List<AuthResourceDto> getAuthResources() {
        return resourceApiClient.getAuthResources();
    }

    @Override
    public UserDetailDto updateUserProfile(UpdateUserRequestDto request) throws NotFoundException, CertificateException {
        UserProfileDto userProfileDto = AuthHelper.getUserProfile();
        UserDetailDto detail = userManagementApiClient.getUserDetail(userProfileDto.getUser().getUuid());
        String certificateUuid = "";
        String certificateFingerprint = "";
        if(detail.getCertificate() != null) {
            if(detail.getCertificate().getUuid() != null) certificateUuid = detail.getCertificate().getUuid();
            if(detail.getCertificate().getFingerprint() != null) certificateFingerprint = detail.getCertificate().getFingerprint();
        }
        return userManagementService.updateUserInternal(userProfileDto.getUser().getUuid(), request, certificateUuid, certificateFingerprint);
    }

}
