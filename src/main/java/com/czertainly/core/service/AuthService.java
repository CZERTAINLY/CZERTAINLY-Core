package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.AuthenticationResponseDto;
import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.api.model.core.auth.UserDto;

import java.util.List;

public interface AuthService {
    UserDto getAuthProfile() throws NotFoundException;

    List<ResourceDetailDto> getAllResources();
}
