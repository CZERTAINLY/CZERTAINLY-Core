package com.czertainly.core.service;

import com.czertainly.api.model.core.auth.ObjectPermissionsDto;
import com.czertainly.api.model.core.auth.ResourcePermissionsDto;
import com.czertainly.api.model.core.auth.RoleDetailDto;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.RolePermissionsRequestDto;
import com.czertainly.api.model.core.auth.RoleRequestDto;
import com.czertainly.api.model.core.auth.SubjectPermissionsDto;

import java.util.List;

public interface RoleManagementService {
    List<RoleDto> listRoles();

    RoleDetailDto getRole(String roleUuid);

    RoleDetailDto createRole(RoleRequestDto request);

    RoleDetailDto updateRole(String roleUuid, RoleRequestDto request);

    void deleteRole(String roleUuid);

    SubjectPermissionsDto getRolePermissions(String roleUuid);

    SubjectPermissionsDto addPermissions(String roleUuid, RolePermissionsRequestDto request);

    ResourcePermissionsDto getRoleResourcePermission(String roleUuid, String resourceUuid);

    List<ObjectPermissionsDto> getResourcePermissionObjects(String roleUuid, String resourceUuid);

    void addResourcePermissionObjects(String roleUuid, String resourceUuid, List<ObjectPermissionsDto> request);

    void updateResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid, List<ObjectPermissionsDto> request);

    void removeResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid);
}
