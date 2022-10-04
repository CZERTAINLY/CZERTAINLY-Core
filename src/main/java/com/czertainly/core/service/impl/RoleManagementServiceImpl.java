package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.auth.RoleRequestDto;
import com.czertainly.api.model.core.auth.*;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.service.RoleManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class RoleManagementServiceImpl implements RoleManagementService {
    private static final Logger logger = LoggerFactory.getLogger(RoleManagementServiceImpl.class);

    @Autowired
    private RoleManagementApiClient roleManagementApiClient;


    @Override
    public List<RoleDto> listRoles() {
        return roleManagementApiClient.getRoles().getData();
    }

    @Override
    public RoleDetailDto getRole(String roleUuid) {
        return roleManagementApiClient.getRoleDetail(roleUuid);
    }

    @Override
    public RoleDetailDto createRole(RoleRequestDto request) {
        com.czertainly.api.model.core.auth.RoleRequestDto requestDto = new com.czertainly.api.model.core.auth.RoleRequestDto();
        requestDto.setName(request.getName());
        requestDto.setDescription(request.getDescription());
        requestDto.setSystemRole(false);
        return roleManagementApiClient.createRole(requestDto);
    }

    @Override
    public RoleDetailDto updateRole(String roleUuid, RoleRequestDto request) {
        com.czertainly.api.model.core.auth.RoleRequestDto requestDto = new com.czertainly.api.model.core.auth.RoleRequestDto();
        requestDto.setName(request.getName());
        requestDto.setDescription(request.getDescription());
        requestDto.setSystemRole(false);
        return roleManagementApiClient.updateRole(roleUuid, requestDto);
    }

    @Override
    public void deleteRole(String roleUuid) {
        roleManagementApiClient.deleteRole(roleUuid);
    }

    @Override
    public SubjectPermissionsDto getRolePermissions(String roleUuid) {
        return roleManagementApiClient.getPermissions(roleUuid);
    }

    @Override
    public SubjectPermissionsDto addPermissions(String roleUuid, RolePermissionsRequestDto request) {
        return roleManagementApiClient.savePermissions(roleUuid, request);
    }

    @Override
    public ResourcePermissionsDto getRoleResourcePermission(String roleUuid, String resourceUuid) {
        return roleManagementApiClient.getPermissionResource(roleUuid, resourceUuid);
    }

    @Override
    public List<ObjectPermissionsDto> getResourcePermissionObjects(String roleUuid, String resourceUuid) {
        return roleManagementApiClient.getResourcePermissionObjects(roleUuid, resourceUuid);
    }

    @Override
    public void addResourcePermissionObjects(String roleUuid, String resourceUuid, List<ObjectPermissionsRequestDto> request) {
        roleManagementApiClient.addResourcePermissionObjects(roleUuid, resourceUuid, request);
    }

    @Override
    public void updateResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid, ObjectPermissionsRequestDto request) {
        roleManagementApiClient.updateResourcePermissionObjects(roleUuid, resourceUuid, objectUuid, request);
    }

    @Override
    public void removeResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid) {
        roleManagementApiClient.removeResourcePermissionObjects(roleUuid, resourceUuid, objectUuid);
    }
}
