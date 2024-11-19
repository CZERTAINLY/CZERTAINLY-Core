package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.GroupController;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class GroupControllerImpl implements GroupController {

    private GroupService groupService;

    @Autowired
    public void setGroupService(GroupService groupService) {
        this.groupService = groupService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.GROUP)
    @AuditLogged(module = Module.CORE, resource = Resource.GROUP, operation = Operation.LIST)
    public List<GroupDto> listGroups() {
        return groupService.listGroups(SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.GROUP, operation = Operation.DETAIL)
    public GroupDto getGroup(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException {
        return groupService.getGroup(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.GROUP, operation = Operation.CREATE)
    public ResponseEntity<?> createGroup(@RequestBody GroupRequestDto request) throws ValidationException, AlreadyExistException, NotFoundException, AttributeException {
        GroupDto groupDto = groupService.createGroup(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(groupDto.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(groupDto.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.GROUP, operation = Operation.UPDATE)
    public GroupDto editGroup(@LogResource(uuid = true) @PathVariable String uuid, @RequestBody GroupRequestDto request) throws NotFoundException, AttributeException {
        return groupService.editGroup(SecuredUUID.fromString(uuid), request);
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.GROUP, operation = Operation.DELETE)
    public void deleteGroup(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException {
        groupService.deleteGroup(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.GROUP, operation = Operation.DELETE)
    public void bulkDeleteGroup(@LogResource(uuid = true) List<String> groupUuids) throws NotFoundException {
        groupService.bulkDeleteGroup(SecuredUUID.fromList(groupUuids));
    }
}
