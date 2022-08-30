package com.czertainly.core.service;

import com.czertainly.api.model.core.auth.PermissionDto;
import com.czertainly.api.model.core.auth.RoleDetailDto;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.RoleRequestDto;

import java.util.List;

public interface RoleManagementService {
    List<RoleDto> listRoles();

    RoleDetailDto getRole(String roleUuid);

    RoleDto createRole(RoleRequestDto request);

    RoleDto updateRole(String roleUuid, RoleRequestDto request);

    void deleteRole(String roleUuid);

    List<PermissionDto> getRolePermissions(String userUuid);

    RoleDetailDto addPermissions(String userUuid, PermissionDto request);
}
