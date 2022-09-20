package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.AuthenticationResponseDto;

public interface AuthService {
    AuthenticationResponseDto getAuthProfile() throws NotFoundException;

}
