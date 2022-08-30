package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.compliance.ComplianceGroupRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceGroupsListResponseDto;
import com.czertainly.api.model.client.compliance.ComplianceProfileComplianceCheckDto;
import com.czertainly.api.model.client.compliance.ComplianceProfileRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceRuleAdditionRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceRuleDeletionRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceRulesListResponseDto;
import com.czertainly.api.model.client.compliance.RaProfileAssociationRequestDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceProfilesListDto;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.Connector;

import java.util.List;
import java.util.Set;

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
     * @throws NotFoundException Thrown when a Rule or Group is not found
     * @throws ValidationException Thrown when the attributes validations are failed for a rule in the request
     */
    ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException;

    /**
     * Add a rule to a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters for adding a new rule to the compliance profile. See {@link ComplianceRuleAdditionRequestDto}
     * @throws AlreadyExistException Thrown when the rule is already tagged with the Compliance Profile
     * @throws NotFoundException Thrown when unable to find the rule with the provided details
     * @throws ValidationException Thrown when the attribute validation fails for the given rule
     * @return Compliance Profile Dto
     */
    ComplianceProfileDto addRule(String uuid, ComplianceRuleAdditionRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException;

    /**
     * Remove a rule from a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters required to remove a specific rule from the compliance profile
     * @throws NotFoundException Thrown when the rule is not found with the profile
     * @return Compliance Profile DTO
     */
    ComplianceProfileDto removeRule(String uuid, ComplianceRuleDeletionRequestDto request) throws NotFoundException;

    /**
     * Add a group to a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters for adding a new group to the compliance profile. See {@link ComplianceGroupRequestDto}
     * @throws AlreadyExistException Thrown when the selected group is already associated
     * @return
     */
    ComplianceProfileDto addGroup(String uuid, ComplianceGroupRequestDto request) throws AlreadyExistException, NotFoundException;

    /**
     * Delete a group from a compliance profile
     *
     * @param uuid Uuid of the compliance provider
     * @param request Parameters for deleting group to the compliance profile. See {@link ComplianceGroupRequestDto}
     * @throws NotFoundException Thrown when the selected group is not found associated with the compliance profile
     * @return Compliance Profile DTO
     */
    ComplianceProfileDto removeGroup(String uuid, ComplianceGroupRequestDto request) throws NotFoundException;

    /**
     * Get the list of associated RA Profile to the compliance profile
     * @param uuid Uuid of the compliance profile
     * @return List of RA Profiles associated with the compliance profile. {@link SimplifiedRaProfileDto}
     * @throws NotFoundException * @throws NotFoundException Thrown when a Rule or Group is not found
     */
    List<SimplifiedRaProfileDto> getAssociatedRAProfiles(String uuid) throws NotFoundException;

    /**
     * Delete a compliance profile
     *
     * @param uuid UUID of the compliance profile
     * @throws NotFoundException Thrown when the system is not able to find the compliance profile for the given UUID
     * @throws ValidationException Thrown when there are any RA Profile association for the selected compliance profile
     */
    void deleteComplianceProfile(String uuid) throws NotFoundException, ValidationException;

    /**
     *Remove multiple compliance profiles
     *
     * @param uuids List of Uuids of the profiles to be deleted
     *
     * @return  List of dependencies for profiles that has RA Profile associations. See {@link BulkActionMessageDto}
     * @throws ValidationException Thrown when the profiles are dependencies for other objects
     * @throws NotFoundException Thrown when a Rule or Group is not found
     */
    List<BulkActionMessageDto> bulkDeleteComplianceProfiles(List<String> uuids) throws NotFoundException, ValidationException;

    /**
     * Remove compliance profiles forcefully. This methods makes removes the object dependencies and set them null.
     *
     * @param uuids Uuids of the compliance profiles to be deleted forcefully.
     * @return
     */
    List<BulkActionMessageDto> forceDeleteComplianceProfiles(List<String> uuids);

    /**
     * List of all compliance rules for User Interface
     * @param complianceProviderUuid - UUID of the compliance provider
     * @param kind Kind of the compliance provider
     * @param certificateType  Type of the certificate for which the rules has to be fetched
     * @return List of the rules for given connector and its kind
     * @throws ConnectorException Thrown when there are issues related to connector communication
     */
    List<ComplianceRulesListResponseDto> getComplianceRules(String complianceProviderUuid, String kind, List<CertificateType> certificateType) throws NotFoundException;

    /**
     * List of all compliance groups from the compliance providers
     *
     * @param complianceProviderUuid Uuid of the compliance provider
     * @param kind Kind of the compliance provider
     * @return List of compliance groups
     * @throws ConnectorException Thrown when there are issues with the connector communication and operations
     */
    List<ComplianceGroupsListResponseDto> getComplianceGroups(String complianceProviderUuid, String kind) throws NotFoundException;

    /**
     * Associate a compliance profile to an RA Profile
     * @param uuid Uuid of the compliance profile
     * @param raProfiles Uuid of the RA Profile. See {{@link RaProfileAssociationRequestDto}}
     * @throws NotFoundException Thrown when either of the profiles are not found
     */
    void associateProfile(String uuid, RaProfileAssociationRequestDto raProfiles) throws NotFoundException;

    /**
     * Check the compliance for all the certificates associated with the compliance profiles
     * @param request Request parameter containing the list of UUIDs of the compliance profiles
     */
    void checkCompliance();

    /**
     * Disassociate Compliance Profiles from RA Profiles
     * @param uuid Compliance Profile UUID
     * @param raProfiles List of RA Profile UUIDs
     */
    void disassociateProfile(String uuid, RaProfileAssociationRequestDto raProfiles) throws NotFoundException;

    /**
     * Check if the compliance provider is associated with any compliance profiles
     * @param connector Connector Entity
     * @return Is the connector tagged with any compliance profiles
     */
    Set<String> isComplianceProviderAssociated(Connector connector);

    /**
     * Remove all the association from the connector to Compliance Group and Rule
     * @param connector Connector Entity
     */
    void nullifyComplianceProviderAssociation(Connector connector);

    /**
     * Removes the rules and groups tagged with a compliance connector
     * @param connector Connector Entity
     */
    void removeRulesAndGroupForEmptyConnector(Connector connector);
}
