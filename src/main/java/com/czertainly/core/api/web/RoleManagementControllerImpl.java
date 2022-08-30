package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.RoleManagementController;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.auth.PermissionDto;
import com.czertainly.api.model.core.auth.RoleDetailDto;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.RoleRequestDto;
import com.czertainly.core.service.RoleManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

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
    public ResponseEntity<UuidDto> createRole(RoleRequestDto request) throws NotFoundException {
        RoleDto roleDto = roleManagementService.createRole(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(roleDto.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(roleDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public RoleDto updateRole(String roleUuid, RoleRequestDto request) throws NotFoundException {
        return roleManagementService.updateRole(roleUuid, request);
    }

    @Override
    public void deleteRole(String roleUuid) throws NotFoundException {
        roleManagementService.deleteRole(roleUuid);
    }

    @Override
    public List<PermissionDto> getRolePermissions(String userUuid) throws NotFoundException {
        return roleManagementService.getRolePermissions(userUuid);
    }

    @Override
    public RoleDetailDto addPermissions(String userUuid, PermissionDto request) throws NotFoundException {
        return roleManagementService.addPermissions(userUuid, request);
    }
}
