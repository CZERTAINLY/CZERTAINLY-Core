package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;

import java.util.List;

public interface GroupService {

    List<GroupDto> listGroups();
    GroupDto getCertificateGroup(String uuid) throws NotFoundException;

    GroupDto createGroup(GroupRequestDto request) throws ValidationException, AlreadyExistException;
    GroupDto updateGroup(String uuid, GroupRequestDto request) throws NotFoundException;

    void removeGroup(String uuid) throws NotFoundException;
    void bulkRemoveGroup(List<String> groupUuids);
}
