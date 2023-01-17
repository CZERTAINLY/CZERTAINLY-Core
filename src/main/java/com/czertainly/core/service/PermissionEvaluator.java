package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.security.authz.SecuredUUID;

import java.util.List;

public interface PermissionEvaluator {
    /**
     * Function to evaluate the permission for the Token Profiles
     * @param uuid UUID of the token Profile
     * @throws NotFoundException when the Token profile with the requested UUID is not found
     */
    void tokenProfile(SecuredUUID uuid) throws NotFoundException;

    /**
     * Function to evaluate the permission for list of token profiles
     * @param uuids UUIDs of the token profile
     */
    void tokenProfiles(List<SecuredUUID> uuids);

}
