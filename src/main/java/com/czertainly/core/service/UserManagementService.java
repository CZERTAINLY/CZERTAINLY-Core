package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.AddUserRequestDto;
import com.czertainly.api.model.core.auth.MergedPermissionsDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;

import java.security.cert.CertificateException;
import java.util.List;

public interface UserManagementService {
    List<UserDto> listUsers();

    UserDetailDto getUser(String userUuid) throws NotFoundException;

    UserDto createUser(AddUserRequestDto request) throws CertificateException, NotFoundException;

    UserDto updateUser(String userUuid, UserUpdateRequestDto request);

    void deleteUser(String userUuid);

    UserDetailDto updateRoles(String userUuid, List<String> roleUuids);

    UserDetailDto updateRole(String userUuid, String roleUuid);

    MergedPermissionsDto getPermissions(String userUuid);
}
