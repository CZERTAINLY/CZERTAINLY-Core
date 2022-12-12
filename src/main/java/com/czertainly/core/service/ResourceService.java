package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;

import java.util.List;

public interface ResourceService {
    /**
     * Function to get the list of objects available to be displayed for object level access for Access Control
     *
     * @param resourceName Name of the resource to
     * @return List of NameAndUuidDto
     */
    List<NameAndUuidDto> getObjectsForResource(Resource resourceName) throws NotFoundException;
}
