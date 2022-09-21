package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AuthController;
import com.czertainly.api.model.core.auth.AuthenticationResponseDto;
import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.core.security.authn.client.ResourceApiClient;
import com.czertainly.core.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthControllerImpl implements AuthController {

    @Autowired
    private AuthService authService;

    @Override
    public UserDto profile() throws NotFoundException {
        return authService.getAuthProfile();
    }

    @Override
    public List<ResourceDetailDto> getAllResources() throws NotFoundException {
        return authService.getAllResources();
    }


}

