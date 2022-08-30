package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.UserManagementController;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.auth.UserRequestDto;
import com.czertainly.api.model.core.auth.UserUpdateRequestDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.Resource;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.service.UserManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class UserManagementControllerImpl implements UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    @Override
    @AuthEndpoint(resourceName = Resource.USER, actionName = ResourceAction.LIST, isListingEndPoint = true)
    public List<UserDto> listUsers() {
        return userManagementService.listUsers();
    }

    @Override
    @AuthEndpoint(resourceName = Resource.USER, actionName = ResourceAction.DETAIL)
    public UserDetailDto getUser(String userUuid) throws NotFoundException {
        return userManagementService.getUser(userUuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.USER, actionName = ResourceAction.CREATE)
    public ResponseEntity<UuidDto> createUser(UserRequestDto request) throws NotFoundException {
        UserDto userDto = userManagementService.createGroup(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(userDto.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(userDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.USER, actionName = ResourceAction.UPDATE)
    public UserDto updateUser(String userUuid, UserUpdateRequestDto request) throws NotFoundException {
        return userManagementService.updateUser(userUuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.USER, actionName = ResourceAction.DELETE)
    public void deleteUser(String userUuid) throws NotFoundException {
        userManagementService.deleteUser(userUuid);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.USER, actionName = ResourceAction.UPDATE)
    public UserDetailDto updateRoles(String userUuid, List<String> roleUuids) throws NotFoundException {
        return userManagementService.updateRoles(userUuid, roleUuids);
    }

    @Override
    @AuthEndpoint(resourceName = Resource.USER, actionName = ResourceAction.UPDATE)
    public UserDetailDto addRole(String userUuid, String roleUuid) throws NotFoundException {
        return userManagementService.updateRole(userUuid, roleUuid);
    }
}
