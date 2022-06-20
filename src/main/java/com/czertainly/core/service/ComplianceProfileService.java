package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.compliance.*;
import com.czertainly.api.model.client.connector.ForceDeleteMessageDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceProfilesListDto;
import com.czertainly.core.dao.entity.ComplianceProfile;

import java.util.List;

public interface ComplianceProfileService {
    /**
     * List of all Compliance Profiles available in the system
     *
     * @return List of compliance profiles
     */
    List<ComplianceProfilesListDto> listComplianceProfiles();

    /**
     * Get the details of a compliance profile
     *
     * @param uuid Uuid of the compliance profile
     * @return Compliance Profile DTO
     * @throws NotFoundException Thrown when the system cannot find the compliance profile for the given Uuid
     */
    ComplianceProfileDto getComplianceProfile(String uuid) throws NotFoundException;

    /**
     * Get the details of a compliance profile
     *
     * @param uuid Uuid of the compliance profile
     * @return Compliance Profile Entity
     * @throws NotFoundException Thrown when the system cannot find the compliance profile for the given Uuid
     */
    ComplianceProfile getComplianceProfileEntity(String uuid) throws NotFoundException;

    /**
     * Create a new compliance profile
     *
     * @param request Request containing the attributes to create a new compliance profile. See {@link ComplianceProfileRequestDto}
     * @return DTO of the new compliance profile that was created
     * @throws AlreadyExistException Thrown when an existing compliance profile is found with the same name
     * @throws ConnectorException Thrown when there are problem related to connector communication
     */
    ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, ConnectorException;

    /**
     * Add a rule to a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters for adding a new rule to the compliance profile. See {@link ComplianceRuleAdditionRequestDto}
     * @throws AlreadyExistException Thrown when the rule is already tagged with the Compliance Profile
     * @throws NotFoundException Thrown when unable to find the rule with the provided details
     */
    void addRule(String uuid, ComplianceRuleAdditionRequestDto request) throws AlreadyExistException, NotFoundException;

    /**
     * Remove a rule from a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters required to remove a specific rule from the compliance profile
     * @throws NotFoundException Thrown when the rule is not found with the profile
     */
    void removeRule(String uuid, ComplianceRuleDeletionRequestDto request) throws NotFoundException;

    /**
     * Add a group to a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters for adding a new group to the compliance profile. See {@link ComplianceGroupRequestDto}
     * @throws AlreadyExistException Thrown when the selected group is already associated
     */
    void addGroup(String uuid, ComplianceGroupRequestDto request) throws AlreadyExistException, NotFoundException;

    /**
     * Delete a group from a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters for deleting group to the compliance profile. See {@link ComplianceGroupRequestDto}
     * @throws NotFoundException Thrown when the selected group is not found associated with the compliance profile
     */
    void removeGroup(String uuid, ComplianceGroupRequestDto request) throws NotFoundException;

    /**
     * Get the list of associated RA Profile to the compliance profile
     * @param uuid Uuid of the compliance profile
     * @return List of RA Profiles associated with the compliance profile. {@link SimplifiedRaProfileDto}
     * @throws NotFoundException
     */
    List<SimplifiedRaProfileDto> getAssociatedRAProfiles(String uuid) throws NotFoundException;

    /**
     * Delete a compliance profile
     *
     * @param uuid UUID of the compliance profile
     * @throws NotFoundException Thrown when the system is not able to find the compliance profile for the given UUID
     * @throws ValidationException Thrown when there are any RA Profile association for the selected compliance profile
     */
    void removeComplianceProfile(String uuid) throws NotFoundException, ValidationException;

    /**
     *Remove multiple compliance profiles
     *
     * @param uuids List of Uuids of the profiles to be deleted
     *
     * @return  List of dependencies for profiles that has RA Profile associations. See {@link ForceDeleteMessageDto}
     * @throws ValidationException Thrown when the profiles are dependencies for other objects
     */
    List<ForceDeleteMessageDto> bulkRemoveComplianceProfiles(List<String> uuids) throws NotFoundException, ValidationException;

    /**
     * Remove compliance profiles forcefully. This methods makes removes the object dependencies and set them null.
     *
     * @param uuids Uuids of the compliance profiles to be deleted forcefully.
     */
    void bulkForceRemoveComplianceProfiles(List<String> uuids);

    /**
     * List of all compliance rules for User Interface
     * @param complianceProviderUuid - UUID of the compliance provider
     * @param kind Kind of the compliance provider
     * @param certificateType  Type of the certificate for which the rules has to be fetched
     * @return List of the rules for given connector and its kind
     * @throws ConnectorException Thrown when there are issues related to connector communication
     */
    List<ComplianceRulesListResponseDto> getComplianceRules(String complianceProviderUuid, String kind, List<CertificateType> certificateType) throws ConnectorException;

    /**
     * List of all compliance groups from the compliance providers
     *
     * @param complianceProviderUuid Uuid of the compliance provider
     * @param kind Kind of the compliance provider
     * @return List of compliance groups
     * @throws ConnectorException Thrown when there are issues with the connector communication and operations
     */
    List<ComplianceGroupsListResponseDto> getComplianceGroups(String complianceProviderUuid, String kind) throws ConnectorException;

    /**
     * Associate a compliance profile to an RA Profile
     * @param uuid Uuid of the compliance profile
     * @param raProfiles Uuid of the RA Profile. See {{@link RaProfileAssociationRequestDto}}
     * @throws NotFoundException Thrown when either of the profiles are not found
     */
    void associateProfile(String uuid, RaProfileAssociationRequestDto raProfiles) throws NotFoundException;

}
