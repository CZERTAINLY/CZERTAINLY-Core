package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.RoleManagementController;
import com.czertainly.api.model.client.auth.RoleRequestDto;
import com.czertainly.api.model.core.auth.*;
import com.czertainly.core.service.RoleManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class RoleManagementControllerImpl implements RoleManagementController {

    @Autowired
    private RoleManagementService roleManagementService;

    @Override
    public List<RoleDto> listRoles() {
        return roleManagementService.listRoles();
    }

    @Override
    public RoleDetailDto getRole(String roleUuid) throws NotFoundException {
        return roleManagementService.getRole(roleUuid);
    }

    @Override
    public ResponseEntity<RoleDetailDto> createRole(com.czertainly.api.model.client.auth.RoleRequestDto request) throws NotFoundException {
        RoleDetailDto dto =  roleManagementService.createRole(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(dto.getUuid())
                .toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public RoleDetailDto updateRole(String roleUuid, RoleRequestDto request) throws NotFoundException {
        return roleManagementService.updateRole(roleUuid, request);
    }

    @Override
    public void deleteRole(String roleUuid) throws NotFoundException {
        roleManagementService.deleteRole(roleUuid);
    }

    @Override
    public List<UserDto> getRoleUsers(String roleUuid) throws NotFoundException {
        return roleManagementService.getRoleUsers(roleUuid);
    }

    @Override
    public RoleDetailDto updateUsers(String roleUuid, List<String> userUuids) throws NotFoundException {
        return roleManagementService.updateUsers(roleUuid, userUuids);
    }

    @Override
    public SubjectPermissionsDto getRolePermissions(String roleUuid) throws NotFoundException {
        return roleManagementService.getRolePermissions(roleUuid);
    }

    @Override
    public SubjectPermissionsDto savePermissions(String roleUuid, RolePermissionsRequestDto request) throws NotFoundException {
        return roleManagementService.addPermissions(roleUuid, request);
    }

    @Override
    public ResourcePermissionsDto getRoleResourcePermissions(String roleUuid, String resourceUuid) throws NotFoundException {
        return roleManagementService.getRoleResourcePermission(roleUuid, resourceUuid);
    }

    @Override
    public List<ObjectPermissionsDto> getResourcePermissionObjects(String roleUuid, String resourceUuid) throws NotFoundException {
        return roleManagementService.getResourcePermissionObjects(roleUuid, resourceUuid);
    }

    @Override
    public void addResourcePermissionObjects(String roleUuid, String resourceUuid, List<ObjectPermissionsRequestDto> request) throws NotFoundException {
        roleManagementService.addResourcePermissionObjects(roleUuid, resourceUuid, request);
    }

    @Override
    public void updateResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid, ObjectPermissionsRequestDto request) throws NotFoundException {
        roleManagementService.updateResourcePermissionObjects(roleUuid, resourceUuid, objectUuid, request);
    }

    @Override
    public void removeResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid) throws NotFoundException {
        roleManagementService.removeResourcePermissionObjects(roleUuid, resourceUuid, objectUuid);
    }
}
