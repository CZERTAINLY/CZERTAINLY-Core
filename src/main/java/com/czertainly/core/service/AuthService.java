package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.auth.EditAuthProfileDto;
import com.czertainly.api.model.core.auth.AuthProfileDto;

public interface AuthService {
    AuthProfileDto getAuthProfile() throws NotFoundException;

    void editAuthProfile(EditAuthProfileDto authProfileDTO) throws NotFoundException;
}
