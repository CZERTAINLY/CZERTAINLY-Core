package com.czertainly.core.api.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.czertainly.core.service.AuthService;
import com.czertainly.api.core.interfaces.web.AuthController;
import com.czertainly.api.core.modal.AuthProfileDto;
import com.czertainly.api.core.modal.EditAuthProfileDto;
import com.czertainly.api.exception.NotFoundException;

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

