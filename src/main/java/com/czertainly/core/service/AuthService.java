package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;

public interface AuthService {
    UserDto getAuthProfile() throws NotFoundException, JsonProcessingException;

    List<ResourceDetailDto> getAllResources();
}
