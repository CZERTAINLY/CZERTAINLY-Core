package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.client.SimplifiedClientDto;
import com.czertainly.api.model.client.raprofile.ActivateAcmeForRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.client.raprofile.RaProfileAcmeDetailResponseDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.model.SecuredItem;
import com.czertainly.core.service.model.SecuredList;

import java.util.List;
import java.util.Optional;

public interface RaProfileService {

    List<RaProfileDto> listRaProfiles(SecurityFilter filter, Optional<Boolean> enabled);

    SecuredList<RaProfile> listRaProfilesAssociatedWithAcmeProfile(String acmeProfileUuid, SecurityFilter filter);

    RaProfileDto addRaProfile(SecuredUUID authorityUuid, AddRaProfileRequestDto dto) throws AlreadyExistException, ValidationException, NotFoundException, ConnectorException;

    RaProfileDto getRaProfile(SecuredUUID uuid) throws NotFoundException;

    RaProfile getRaProfileEntity(SecuredUUID uuid) throws NotFoundException;

    RaProfileDto editRaProfile(SecuredUUID authorityUuid, SecuredUUID raProfileUuid, EditRaProfileRequestDto dto) throws NotFoundException, ConnectorException;

    void deleteRaProfile(SecuredUUID uuid) throws NotFoundException;

    List<SimplifiedClientDto> listClients(SecuredUUID uuid) throws NotFoundException;

    void enableRaProfile(SecuredUUID uuid) throws NotFoundException;

    void disableRaProfile(SecuredUUID uuid) throws NotFoundException;

    void bulkDeleteRaProfile(List<SecuredUUID> uuids);

    void bulkDisableRaProfile(List<SecuredUUID> uuids);

    void bulkEnableRaProfile(List<SecuredUUID> uuids);

    void bulkRemoveAssociatedAcmeProfile(List<SecuredUUID> uuids);

    RaProfileAcmeDetailResponseDto getAcmeForRaProfile(SecuredUUID uuid) throws NotFoundException;

    RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(SecuredUUID uuid, SecuredUUID acmeProfileUuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException;

    void deactivateAcmeForRaProfile(SecuredUUID uuid) throws NotFoundException;

    List<AttributeDefinition> listRevokeCertificateAttributes(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    List<AttributeDefinition> listIssueCertificateAttributes(SecuredUUID uuid) throws NotFoundException, ConnectorException;

    /**
     * Save the RA Profile entity to the database
     * @param raProfile RA profile entity
     * @return RA Profile Entity
     */
    RaProfile updateRaProfileEntity(RaProfile raProfile);

    /**
     * Check the compliance for all the certificates associated with the RA Profile
     * @param uuids UUIDs for which the request has to be triggered
     */
    void checkCompliance(List<SecuredUUID> uuids);
}
