package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.AuthController;
import com.czertainly.api.model.client.auth.EditAuthProfileDto;
import com.czertainly.api.model.core.auth.AuthProfileDto;
import com.czertainly.core.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthControllerImpl implements AuthController{

    @Autowired
    private AuthService authService;

    @Override
    public AuthProfileDto profile() throws NotFoundException {
        return authService.getAuthProfile();
    }

    @Override
    public void editProfile(@RequestBody EditAuthProfileDto authProfileDTO) throws NotFoundException {
        authService.editAuthProfile(authProfileDTO);
    }
}

