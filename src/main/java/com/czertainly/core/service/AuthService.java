package com.czertainly.core.service;

import com.czertainly.api.core.modal.AuthProfileDto;
import com.czertainly.api.core.modal.EditAuthProfileDto;
import com.czertainly.api.exception.NotFoundException;

public interface AuthService {
    AuthProfileDto getAuthProfile() throws NotFoundException;

    void editAuthProfile(EditAuthProfileDto authProfileDTO) throws NotFoundException;
}
