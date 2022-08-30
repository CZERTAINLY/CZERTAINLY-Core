package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.MergedPermissionsDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.service.UserManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class UserManagementServiceImpl implements UserManagementService {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementServiceImpl.class);

    @Autowired
    private UserManagementApiClient userManagementApiClient;

    @Override
    public List<UserDto> listUsers() {
        return userManagementApiClient.getUsers().getData();
    }

    @Override
    public UserDetailDto getUser(String userUuid) throws NotFoundException {
        return userManagementApiClient.getUserDetail(userUuid);
    }

    @Override
    public UserDto createGroup(UserRequestDto request) {
        return userManagementApiClient.createUser(request);
    }

    @Override
    public UserDto updateUser(String userUuid, UserUpdateRequestDto request) {
        return userManagementApiClient.updateUser(userUuid, request);
    }

    @Override
    public void deleteUser(String userUuid) {
        userManagementApiClient.removeUser(userUuid);
    }

    @Override
    public UserDetailDto updateRoles(String userUuid, List<String> roleUuids) {
        return userManagementApiClient.updateRoles(userUuid, roleUuids);
    }

    @Override
    public UserDetailDto updateRole(String userUuid, String roleUuid) {
        return userManagementApiClient.updateRole(userUuid, roleUuid);
    }

    @Override
    public MergedPermissionsDto getPermissions(String userUuid) {
        return userManagementApiClient.getPermissions(userUuid);
    }
}
