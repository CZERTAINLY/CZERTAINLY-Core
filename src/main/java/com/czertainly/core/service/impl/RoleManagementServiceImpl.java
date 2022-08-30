package com.czertainly.core.service.impl;

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
    public RoleDto createRole(RoleRequestDto request) {
        return roleManagementApiClient.createRole(request);
    }

    @Override
    public RoleDto updateRole(String roleUuid, RoleRequestDto request) {
        return roleManagementApiClient.updateRole(roleUuid,request);
    }

    @Override
    public void deleteRole(String roleUuid) {
        roleManagementApiClient.deleteRole(roleUuid);
    }

    @Override
    public List<PermissionDto> getRolePermissions(String userUuid) {
        return null;
    }

    @Override
    public RoleDetailDto addPermissions(String userUuid, PermissionDto request) {
        return null;
    }
}
