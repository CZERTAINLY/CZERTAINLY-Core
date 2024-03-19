package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.auth.RoleRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.*;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authn.client.RoleManagementApiClient;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.RoleManagementService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RoleManagementServiceImpl implements RoleManagementService {
    private static final Logger logger = LoggerFactory.getLogger(RoleManagementServiceImpl.class);

    @Autowired
    private RoleManagementApiClient roleManagementApiClient;

    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.LIST)
    public List<RoleDto> listRoles() {
        return roleManagementApiClient.getRoles().getData();
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DETAIL)
    public RoleDetailDto getRole(String roleUuid) {
        RoleDetailDto dto = roleManagementApiClient.getRoleDetail(roleUuid);
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.ROLE, UUID.fromString(roleUuid)));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.CREATE)
    public RoleDetailDto createRole(RoleRequestDto request) throws NotFoundException, AttributeException {
        attributeEngine.validateCustomAttributesContent(Resource.ROLE, request.getCustomAttributes());
        com.czertainly.api.model.core.auth.RoleRequestDto requestDto = new com.czertainly.api.model.core.auth.RoleRequestDto();
        requestDto.setName(request.getName());
        requestDto.setDescription(request.getDescription());
        requestDto.setEmail(request.getEmail());
        requestDto.setSystemRole(false);
        RoleDetailDto dto = roleManagementApiClient.createRole(requestDto);
        dto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.ROLE, UUID.fromString(dto.getUuid()), request.getCustomAttributes()));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public RoleDetailDto updateRole(String roleUuid, RoleRequestDto request) throws NotFoundException, AttributeException {
        attributeEngine.validateCustomAttributesContent(Resource.ROLE, request.getCustomAttributes());
        com.czertainly.api.model.core.auth.RoleRequestDto requestDto = new com.czertainly.api.model.core.auth.RoleRequestDto();
        requestDto.setName(request.getName());
        requestDto.setDescription(request.getDescription());
        requestDto.setEmail(request.getEmail());
        requestDto.setSystemRole(false);
        RoleDetailDto dto = roleManagementApiClient.updateRole(roleUuid, requestDto);
        dto.setCustomAttributes(attributeEngine.updateObjectCustomAttributesContent(Resource.ROLE, UUID.fromString(dto.getUuid()), request.getCustomAttributes()));

        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DELETE)
    public void deleteRole(String roleUuid) {
        roleManagementApiClient.deleteRole(roleUuid);
        attributeEngine.deleteAllObjectAttributeContent(Resource.ROLE, UUID.fromString(roleUuid));
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DETAIL)
    public SubjectPermissionsDto getRolePermissions(String roleUuid) {
        return roleManagementApiClient.getPermissions(roleUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public SubjectPermissionsDto addPermissions(String roleUuid, RolePermissionsRequestDto request) {
        checkSystemRole(roleUuid);

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
        checkSystemRole(roleUuid);

        roleManagementApiClient.addResourcePermissionObjects(roleUuid, resourceUuid, request);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public void updateResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid, ObjectPermissionsRequestDto request) {
        checkSystemRole(roleUuid);

        roleManagementApiClient.updateResourcePermissionObjects(roleUuid, resourceUuid, objectUuid, request);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
    public void removeResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid) {
        checkSystemRole(roleUuid);

        roleManagementApiClient.removeResourcePermissionObjects(roleUuid, resourceUuid, objectUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.DETAIL)
    public List<UserDto> getRoleUsers(String roleUuid) {
        return roleManagementApiClient.getRoleUsers(roleUuid);
    }

    @Override
    @ExternalAuthorization(resource = Resource.ROLE, action = ResourceAction.UPDATE)
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

    private void checkSystemRole(String roleUuid) {
        RoleDetailDto roleDetailDto = roleManagementApiClient.getRoleDetail(roleUuid);
        if (roleDetailDto.getSystemRole()) {
            throw new ValidationException("Cannot edit permissions of system role: " + roleDetailDto.getName());
        }
    }
}
