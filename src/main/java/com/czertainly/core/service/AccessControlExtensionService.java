package com.czertainly.core.service;

import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface AccessControlExtensionService {
    /**
     * Function to get the list of name and uuid dto for the objects available in the database.
     * @return List of NameAndUuidDto
     */
    List<NameAndUuidDto> listResourceObjects(SecurityFilter filter);
}
