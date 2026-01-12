package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.UUID;

public interface ResourceExtensionService {

    NameAndUuidDto getResourceObject(UUID objectUuid) throws NotFoundException;

    /**
     * Function to get the list of name and uuid dto for the objects available in the database.
     * @return List of NameAndUuidDto
     */
    List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters);

    /**
     * Function to evaluate the permission for the objects and its parents based on the UUID
     * @param uuid UUID of the object
     * @throws NotFoundException when the object with the given UUID is not found
     */
    void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException;
}
