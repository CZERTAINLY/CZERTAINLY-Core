package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckResultDto;
import com.czertainly.core.model.compliance.ComplianceResultDto;
import com.czertainly.core.security.authz.SecuredUUID;

import java.util.List;
import java.util.UUID;

public interface ComplianceService {


    /**
     * Get the latest compliance check result for the specified resource object
     *
     * @param resource Resource of the object
     * @param objectUuid UUID of the object
     * @return ComplianceCheckResultDto containing the result of the latest compliance check
     * @throws NotFoundException if the resource object is not found
     */
    ComplianceCheckResultDto getComplianceCheckResult(Resource resource, UUID objectUuid) throws NotFoundException;

    /**
     * Get the latest compliance check result for the specified resource object using the provided compliance result
     *
     * @param resource Resource of the object
     * @param objectUuid UUID of the
     * @param complianceResult ComplianceResultDto containing the compliance check result data
     * @return ComplianceCheckResultDto containing the result of the latest compliance check
     */
    ComplianceCheckResultDto getComplianceCheckResult(Resource resource, UUID objectUuid, ComplianceResultDto complianceResult);


    /**
     * Validate Check compliance request for specified compliance profiles
     *
     * @param uuids List of UUIDs of the compliance profiles
     * @param resource Filter checked objects by resource
     * @param type Filter checked objects by resource type
     * @throws ValidationException if validation fails
     * @throws NotFoundException if compliance profile is not found
     */
    void checkComplianceValidation(List<SecuredUUID> uuids, Resource resource, String type) throws ValidationException, NotFoundException;

    /**
     * Check the compliance for all objects associated with the compliance profiles in asynchronous way
     *
     * @param uuids List of UUIDs of the compliance profiles
     * @param resource Filter checked objects by resource
     * @param type Filter checked objects by resource type
     */
    void checkComplianceAsync(List<SecuredUUID> uuids, Resource resource, String type);


    /**
     * Check the compliance for all objects associated with the compliance profiles
     *
     * @param uuids List of UUIDs of the compliance profiles
     * @param resource Filter checked objects by resource
     * @param type Filter checked objects by resource type
     */
    void checkCompliance(List<SecuredUUID> uuids, Resource resource, String type);

    /**
     * Validate Check compliance request for specified resource objects
     *
     * @param resource Resource of objects checked by compliance
     * @param objectUuids UUIDs of objects to be checked
     * @throws ValidationException if validation fails
     * @throws NotFoundException if resource object is not found
     */
    void checkResourceObjectsComplianceValidation(Resource resource, List<UUID> objectUuids) throws ValidationException, NotFoundException;

    /**
     * Check compliance on specified resource object
     *
     * @param resource Resource of objects checked by compliance
     * @param objectUuid UUID of object to be checked
     */
    void checkResourceObjectComplianceAsync(Resource resource, UUID objectUuid);

    /**
     * Check compliance on specified resource objects
     *
     * @param resource Resource of objects checked by compliance
     * @param objectUuids UUIDs of objects to be checked
     */
    void checkResourceObjectsComplianceAsync(Resource resource, List<UUID> objectUuids);


    /**
     * Check compliance on specified resource object
     *
     * @param resource Resource of objects checked by compliance
     * @param objectUuid UUID of object to be checked
     */
    void checkResourceObjectCompliance(Resource resource, UUID objectUuid);


}
