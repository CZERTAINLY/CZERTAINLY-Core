package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.compliance.SimplifiedComplianceProfileDto;
import com.czertainly.api.model.client.raprofile.ActivateAcmeForRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.RaProfileAcmeDetailResponseDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.model.SecuredList;

import java.util.List;
import java.util.Optional;

public interface RaProfileService {

    List<RaProfileDto> listRaProfiles(SecurityFilter filter, Optional<Boolean> enabled);

    SecuredList<RaProfile> listRaProfilesAssociatedWithAcmeProfile(String acmeProfileUuid, SecurityFilter filter);

    RaProfileDto addRaProfile(SecuredParentUUID authorityUuid, AddRaProfileRequestDto dto) throws AlreadyExistException, ValidationException, ConnectorException;

    RaProfileDto getRaProfile(SecuredUUID uuid) throws NotFoundException;

    RaProfileDto getRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException;

    RaProfile getRaProfileEntity(SecuredUUID uuid) throws NotFoundException;

    RaProfileDto editRaProfile(SecuredParentUUID authorityUuid, SecuredUUID raProfileUuid, EditRaProfileRequestDto dto) throws ConnectorException;

    void deleteRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException;

    void deleteRaProfile(SecuredUUID uuid) throws NotFoundException;

    void enableRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException;

    void disableRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException;

    void bulkDeleteRaProfile(List<SecuredUUID> uuids);

    void bulkDisableRaProfile(List<SecuredUUID> uuids);

    void bulkEnableRaProfile(List<SecuredUUID> uuids);

    void bulkRemoveAssociatedAcmeProfile(List<SecuredUUID> uuids);

    RaProfileAcmeDetailResponseDto getAcmeForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException;

    RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid, SecuredUUID acmeProfileUuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException;

    void deactivateAcmeForRaProfile(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws NotFoundException;

    List<BaseAttribute> listRevokeCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws ConnectorException;

    List<BaseAttribute> listIssueCertificateAttributes(SecuredParentUUID authorityUuid, SecuredUUID uuid) throws ConnectorException;

    /**
     * Save the RA Profile entity to the database
     *
     * @param raProfile RA profile entity
     * @return RA Profile Entity
     */
    RaProfile updateRaProfileEntity(RaProfile raProfile);

    /**
     * Check the compliance for all the certificates associated with the RA Profile
     *
     * @param uuids UUIDs for which the request has to be triggered
     */
    void checkCompliance(List<SecuredUUID> uuids);

    /**
     * Get the number of ra profiles per user for dashboard
     * @return Number of raprofiles
     */
    Long statisticsRaProfilesCount(SecurityFilter filter);

    /**
     *Function to get the list of RA Compliance Profiles from RA Profiles
     * @param authorityUuid UUID of the authority
     * @param raProfileUuid UUID of the RA Profile
     * @return
     */
    List<SimplifiedComplianceProfileDto> getComplianceProfiles(String authorityUuid, String raProfileUuid, SecurityFilter filter) throws NotFoundException;

    /**
     * Function to check if an user has All RA profile Access
     * @param filter Security Filter
     * @return Boolean of permission
     */
    Boolean evaluateNullableRaPermissions(SecurityFilter filter);
}
