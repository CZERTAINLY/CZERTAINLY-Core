package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface GroupService extends ResourceExtensionService {

    List<GroupDto> listGroups(SecurityFilter filter);
    GroupDto getGroup(SecuredUUID uuid) throws NotFoundException;

    GroupDto createGroup(GroupRequestDto request) throws ValidationException, AlreadyExistException, NotFoundException, AttributeException;
    GroupDto editGroup(SecuredUUID uuid, GroupRequestDto request) throws NotFoundException, AttributeException;

    void deleteGroup(SecuredUUID uuid) throws NotFoundException;
    void bulkDeleteGroup(List<SecuredUUID> groupUuids);

    /**
     * Get the number of groups per user for dashboard
     * @return Number of groups
     */
    Long statisticsGroupCount(SecurityFilter filter);
}
