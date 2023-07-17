package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.auth.RoleRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.RoleManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RoleManagementServiceImpl implements RoleManagementService {
    private static final Logger logger = LoggerFactory.getLogger(RoleManagementServiceImpl.class);

    @Autowired
    private RoleManagementApiClient roleManagementApiClient;

    @Autowired
    private AttributeService attributeService;

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.LIST)
    public List<RoleDto> listRoles() {
        return roleManagementApiClient.getRoles().getData();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DETAIL)
    public RoleDetailDto getRole(String roleUuid) {
        RoleDetailDto dto = roleManagementApiClient.getRoleDetail(roleUuid);
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(UUID.fromString(roleUuid), Resource.ROLE));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.CREATE)
    public RoleDetailDto createRole(RoleRequestDto request) {
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.ROLE);
        com.czertainly.api.model.core.auth.RoleRequestDto requestDto = new com.czertainly.api.model.core.auth.RoleRequestDto();
        requestDto.setName(request.getName());
        requestDto.setDescription(request.getDescription());
        requestDto.setEmail(request.getEmail());
        requestDto.setSystemRole(false);
        RoleDetailDto dto = roleManagementApiClient.createRole(requestDto);
        attributeService.createAttributeContent(UUID.fromString(dto.getUuid()), request.getCustomAttributes(), Resource.ROLE);
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(UUID.fromString(dto.getUuid()), Resource.ROLE));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public RoleDetailDto updateRole(String roleUuid, RoleRequestDto request) {
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.ROLE);
        com.czertainly.api.model.core.auth.RoleRequestDto requestDto = new com.czertainly.api.model.core.auth.RoleRequestDto();
        requestDto.setName(request.getName());
        requestDto.setDescription(request.getDescription());
        requestDto.setEmail(request.getEmail());
        requestDto.setSystemRole(false);
        RoleDetailDto dto = roleManagementApiClient.updateRole(roleUuid, requestDto);
        attributeService.updateAttributeContent(UUID.fromString(dto.getUuid()), request.getCustomAttributes(), Resource.ROLE);
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(UUID.fromString(dto.getUuid()), Resource.ROLE));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DELETE)
    public void deleteRole(String roleUuid) {
        roleManagementApiClient.deleteRole(roleUuid);
        attributeService.deleteAttributeContent(UUID.fromString(roleUuid), Resource.ROLE);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DETAIL)
    public SubjectPermissionsDto getRolePermissions(String roleUuid) {
        return roleManagementApiClient.getPermissions(roleUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public SubjectPermissionsDto addPermissions(String roleUuid, RolePermissionsRequestDto request) {
        return roleManagementApiClient.savePermissions(roleUuid, request);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DETAIL)
    public ResourcePermissionsDto getRoleResourcePermission(String roleUuid, String resourceUuid) {
        return roleManagementApiClient.getPermissionResource(roleUuid, resourceUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DETAIL)
    public List<ObjectPermissionsDto> getResourcePermissionObjects(String roleUuid, String resourceUuid) {
        return roleManagementApiClient.getResourcePermissionObjects(roleUuid, resourceUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public void addResourcePermissionObjects(String roleUuid, String resourceUuid, List<ObjectPermissionsRequestDto> request) {
        roleManagementApiClient.addResourcePermissionObjects(roleUuid, resourceUuid, request);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public void updateResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid, ObjectPermissionsRequestDto request) {
        roleManagementApiClient.updateResourcePermissionObjects(roleUuid, resourceUuid, objectUuid, request);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public void removeResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid) {
        roleManagementApiClient.removeResourcePermissionObjects(roleUuid, resourceUuid, objectUuid);
    }

    @Override
    public List<UserDto> getRoleUsers(String roleUuid) {
        return roleManagementApiClient.getRoleUsers(roleUuid);
    }

    @Override
    public RoleDetailDto updateUsers(String roleUuid, List<String> userUuids) {
        return roleManagementApiClient.updateUsers(roleUuid, userUuids);
    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        getRole(uuid.toString());
    }
}
