package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.tokenprofile.AddTokenProfileRequestDto;
import com.czertainly.api.model.client.cryptography.tokenprofile.EditTokenProfileRequestDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDetailDto;
import com.czertainly.api.model.core.cryptography.tokenprofile.TokenProfileDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;
import java.util.Optional;

public interface TokenProfileService {
    /**
     * Get the list of token profiles
     *
     * @param enabled - Boolean state if the profile is enabled or not
     * @param filter Security Filter for Access Control
     * @return List of Token Profiles {@Link TokenProfileDto}
     */
    List<TokenProfileDto> listTokenProfiles(Optional<Boolean> enabled, SecurityFilter filter);

    /**
     * Get the details of a token profile which has Token Instance association
     *
     * @param tokenInstanceUuid - UUID of the Token Instance
     * @param uuid              - UUID of the Token Profile
     * @return Details of the token Profile {@Link TokenProfileDetailDto}
     * @throws NotFoundException When the token instance or token profile is not found
     */
    TokenProfileDetailDto getTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException;

    /**
     * Create a new token profile
     *
     * @param tokenInstanceUuid UUID of the token instance on which the token profile has to be created
     * @param request           Request DTO containing the parameters for creating the new token profile. {@Link AddTokenProfileRequestDto}
     * @return Details of the newly created token profile
     * @throws AlreadyExistException when the token profile with same data already exists
     * @throws ValidationException   when there are issues with the attribute validation
     * @throws ConnectorException    when there are issues related with connector communication
     */
    TokenProfileDetailDto createTokenProfile(SecuredParentUUID tokenInstanceUuid, AddTokenProfileRequestDto request) throws AlreadyExistException, ValidationException, ConnectorException;

    /**
     * Update the token profile
     *
     * @param tokenInstanceUuid UUID of the token instance where the token profile is created
     * @param uuid              UUID of the concerned token profile
     * @param request           Request containing the update details. {@Link EditTokenProfileRequestDto}
     * @return Details of the updated token profile
     * @throws ConnectorException when there are issues with the connector communication
     */
    TokenProfileDetailDto editTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid, EditTokenProfileRequestDto request) throws ConnectorException;

    /**
     * Delete a token profile
     *
     * @param tokenInstanceUuid UUID of the token instance where the token profile is created
     * @param uuid              UUID of the concerned token profile
     * @throws NotFoundException when the token profile is not found
     */
    void deleteTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException;

    /**
     * @param tokenProfileUuid UUID of the concerned token profile. Use this method when the profile is not associated with any instance
     * @throws NotFoundException when the token profile is not found
     */
    void deleteTokenProfile(SecuredUUID tokenProfileUuid) throws NotFoundException;

    /**
     * @param tokenInstanceUuid UUID of the token instance where the token profile is created
     * @param uuid              UUID of the concerned token profile
     * @throws NotFoundException when the token profile is not found
     */
    void disableTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException;

    /**
     * @param tokenInstanceUuid UUID of the token instance where the token profile is created
     * @param uuid              UUID of the concerned token profile
     * @throws NotFoundException when the token profile is not found
     */
    void enableTokenProfile(SecuredParentUUID tokenInstanceUuid, SecuredUUID uuid) throws NotFoundException;

    /**
     * @param uuids UUIDs of the token profile
     * @throws NotFoundException   when the token profile is not found
     * @throws ValidationException
     */
    void bulkDeleteTokenProfile(List<SecuredUUID> uuids) throws NotFoundException, ValidationException;

    /**
     * @param uuids UUIDs of the token profile
     * @throws NotFoundException when the token profile is not found
     */
    void bulkDisableRaProfile(List<SecuredUUID> uuids) throws NotFoundException;

    /**
     * @param uuids UUIDs of the token profile
     * @throws NotFoundException when the token profile is not found
     */
    void bulkEnableRaProfile(List<SecuredUUID> uuids) throws NotFoundException;

}
