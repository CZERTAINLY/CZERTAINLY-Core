package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AuthController;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.core.service.AuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateException;
import java.util.List;

@RestController
public class AuthControllerImpl implements AuthController {

    @Autowired
    private AuthService authService;

    @Override
    public UserDetailDto profile() throws NotFoundException, JsonProcessingException {
        return authService.getAuthProfile();
    }

    @Override
    public UserDetailDto updateUserProfile(UpdateUserRequestDto request) throws NotFoundException, JsonProcessingException, CertificateException {
        return authService.updateUserProfile(request);
    }

    @Override
    public List<ResourceDetailDto> getAllResources() throws NotFoundException {
        return authService.getAllResources();
    }


}

