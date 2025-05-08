package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.notification.RecipientType;
import com.czertainly.core.dao.entity.Group;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ResourceObjectAssociationService {
    List<UUID> getGroupUuids(Resource resource, UUID objectUuid);
    void addGroup(Resource resource, UUID objectUuid, UUID groupUuid) throws NotFoundException;
    Set<Group> setGroups(Resource resource, UUID objectUuid, Set<UUID> groupUuid) throws NotFoundException;
    void removeGroup(Resource resource, UUID objectUuid, UUID groupUuid);
    void removeGroupAssociations(UUID groupUuid);

    NameAndUuidDto getOwner(Resource resource, UUID objectUuid);
    NameAndUuidDto setOwner(Resource resource, UUID objectUuid, UUID ownerUuid) throws NotFoundException;
    void setOwnerFromProfile(Resource resource, UUID objectUuid);
    void removeOwnerAssociations(UUID ownerUuid);

    void removeObjectAssociations(Resource resource, UUID objectUuid);

    NameAndUuidDto getAssociationObjectInfo(RecipientType recipientType, UUID objectUuid) throws NotFoundException;
}
