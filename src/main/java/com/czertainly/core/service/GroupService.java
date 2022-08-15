package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface GroupService {

    List<GroupDto> listGroups(SecurityFilter filter);
    GroupDto getCertificateGroup(SecuredUUID uuid) throws NotFoundException;

    GroupDto createGroup(GroupRequestDto request) throws ValidationException, AlreadyExistException;
    GroupDto updateGroup(SecuredUUID uuid, GroupRequestDto request) throws NotFoundException;

    void removeGroup(SecuredUUID uuid) throws NotFoundException;
    void bulkRemoveGroup(List<SecuredUUID> groupUuids);
}
