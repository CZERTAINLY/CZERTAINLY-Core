package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.GroupController;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.core.security.authz.*;
import com.czertainly.core.service.impl.GroupServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class GroupControllerImpl implements GroupController {

    @Autowired
    private GroupServiceImpl groupService;

    @Override
    public List<GroupDto> listGroups() {
        return groupService.listGroups(SecurityFilter.create());
    }

    @Override
    public GroupDto getGroup(@PathVariable String uuid) throws NotFoundException {
        return groupService.getCertificateGroup(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<?> createGroup(@RequestBody GroupRequestDto request) throws ValidationException, AlreadyExistException {
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
    public GroupDto updateGroup(@PathVariable String uuid, @RequestBody GroupRequestDto request) throws NotFoundException {
        SecuredUUID id = SecuredUUID.fromString(uuid);
        return groupService.updateGroup(id, request);
    }

    @Override
    public void removeGroup(@PathVariable String uuid) throws NotFoundException {
        groupService.removeGroup(SecuredUUID.fromString(uuid));
    }

    @Override
    public void bulkRemoveGroup(List<String> groupUuids) throws NotFoundException {
        groupService.bulkRemoveGroup(SecuredUUID.fromList(groupUuids));
    }
}
