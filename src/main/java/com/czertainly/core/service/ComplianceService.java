package com.czertainly.core.service;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckResultDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceResultDto;
import com.czertainly.core.security.authz.SecuredUUID;

import java.util.List;
import java.util.UUID;

public interface ComplianceService {

    ComplianceCheckResultDto getComplianceCheckResult(ComplianceResultDto complianceResult);

    /**
     * Check the compliance for all objects associated with the compliance profiles
     *
     * @param uuids List of UUIDs of the compliance profiles
     * @param resource Filter checked objects by resource
     * @param type Filter checked objects by resource type
     */
    void checkCompliance(List<SecuredUUID> uuids, Resource resource, String type);

    /**
     * Check compliance on specified resource object
     *
     * @param resource Resource of objects checked by compliance
     * @param objectUuid UUID of object to be checked
     */
    void checkResourceObjectCompliance(Resource resource, UUID objectUuid);

    /**
     * Check compliance on specified resource objects
     *
     * @param resource Resource of objects checked by compliance
     * @param objectUuids UUIDs of objects to be checked
     */
    void checkResourceObjectsCompliance(Resource resource, List<UUID> objectUuids);

}
