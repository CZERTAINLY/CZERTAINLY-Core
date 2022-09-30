package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.UserManagementController;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.SubjectPermissionsDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.service.UserManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.cert.CertificateException;
import java.util.List;

@RestController
public class UserManagementControllerImpl implements UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    @Override
    @AuthEndpoint(resourceName = Resource.USER)
    public List<UserDto> listUsers() {
        return userManagementService.listUsers();
    }

    @Override
    public UserDetailDto getUser(String userUuid) throws NotFoundException {
        return userManagementService.getUser(userUuid);
    }

    @Override
    public ResponseEntity<UserDetailDto> createUser(AddUserRequestDto request) throws NotFoundException, CertificateException {
        UserDetailDto userDto = userManagementService.createUser(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(userDto.getUuid())
                .toUri();
        return ResponseEntity.created(location).body(userDto);
    }

    @Override
    public UserDetailDto updateUser(String userUuid, UpdateUserRequestDto request) throws NotFoundException, CertificateException {
        return userManagementService.updateUser(userUuid, request);
    }

    @Override
    public UserDetailDto enableUser(String userUuid) throws NotFoundException {
        return userManagementService.enableUser(userUuid);
    }

    @Override
    public UserDetailDto disableUser(String userUuid) throws NotFoundException {
        return userManagementService.disableUser(userUuid);
    }

    @Override
    public void deleteUser(String userUuid) throws NotFoundException {
        userManagementService.deleteUser(userUuid);
    }

    @Override
    public List<RoleDto> getUserRoles(String userUuid) throws NotFoundException {
        return userManagementService.getUserRoles(userUuid);
    }

    @Override
    public UserDetailDto updateRoles(String userUuid, List<String> roleUuids) throws NotFoundException {
        return userManagementService.updateRoles(userUuid, roleUuids);
    }

    @Override
    public UserDetailDto addRole(String userUuid, String roleUuid) throws NotFoundException {
        return userManagementService.updateRole(userUuid, roleUuid);
    }

    @Override
    public UserDetailDto removeRole(String userUuid, String roleUuid) throws NotFoundException {
        return userManagementService.removeRole(userUuid, roleUuid);
    }

    @Override
    public SubjectPermissionsDto getPermissions(String userUuid) throws NotFoundException {
        return userManagementService.getPermissions(userUuid);
    }
}
