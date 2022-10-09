package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.ResourceDetailDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.security.cert.CertificateException;
import java.util.List;

public interface AuthService {
    UserDetailDto getAuthProfile() throws NotFoundException, JsonProcessingException;

    List<ResourceDetailDto> getAllResources();

    UserDetailDto updateUserProfile(UpdateUserRequestDto request) throws NotFoundException, CertificateException;
}
