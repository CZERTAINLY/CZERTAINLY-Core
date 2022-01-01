package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.GroupController;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
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

    @Autowired
    private GroupService groupService;

    @Override
    public List<GroupDto> listGroups() {
        return groupService.listGroups();
    }

    @Override
    public GroupDto getGroup(@PathVariable String uuid) throws NotFoundException {
        return groupService.getCertificateGroup(uuid);
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
        return groupService.updateGroup(uuid, request);
    }

    @Override
    public void removeGroup(@PathVariable String uuid) throws NotFoundException {
        groupService.removeGroup(uuid);
    }

    @Override
    public void bulkRemoveGroup(List<String> groupUuids) throws NotFoundException {
        groupService.bulkRemoveGroup(groupUuids);
    }
}
