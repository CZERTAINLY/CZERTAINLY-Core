package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.LocationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.core.location.LocationDto;

import java.util.List;
import java.util.Optional;

public interface LocationService {

    /**
     * List all locations.
     * @return List of locations.
     */
    List<LocationDto> listLocation();

    /**
     * List all locations based on the status.
     * @param isEnabled If true, only enabled locations are returned.
     * @return List of locations.
     */
    List<LocationDto> listLocations(Optional<Boolean> enabled);

    /**
     * Add a new location.
     * @param addLocationRequestDto Request containing Attributes for the Location, see {@link AddLocationRequestDto}.
     * @return New Location created.
     * @throws AlreadyExistException when the Location with the same name already exists.
     * @throws LocationException when the Location failed to be created.
     * @throws NotFoundException when the Entity instance referred in the Location is not found.
     */
    LocationDto addLocation(AddLocationRequestDto addLocationRequestDto) throws AlreadyExistException, LocationException, NotFoundException;

    /**
     * Get existing Location by UUID.
     * @param locationUuid UUID of existing Location.
     * @return Location with the given UUID.
     * @throws NotFoundException when the Location with the given UUID is not found.
     */
    LocationDto getLocation(String locationUuid) throws NotFoundException;

    /**
     * Edit an existing Location.
     * @param locationUuid UUID of existing Location.
     * @param editLocationRequestDto Request containing Attributes for the Location, see {@link EditLocationRequestDto}.
     * @return Edited Location.
     * @throws NotFoundException when the Location with the given UUID is not found.
     * @throws LocationException when the Location failed to be edited.
     */
    LocationDto editLocation(String locationUuid, EditLocationRequestDto editLocationRequestDto) throws NotFoundException, LocationException;

    /**
     * Remove existing Location with the given UUID.
     * @param locationUuid UUID of existing Location.
     * @throws NotFoundException when the Location with the given UUID is not found.
     * @throws ValidationException when the Location contains associated Certificates.
     */
    void removeLocation(String locationUuid) throws NotFoundException;

    /**
     * Enable existing Location with the given UUID.
     * @param locationUuid UUID of existing Location.
     * @throws NotFoundException when the Location with the given UUID is not found.
     */
    void enableLocation(String locationUuid) throws NotFoundException;

    /**
     * Disable existing Location with the given UUID.
     * @param locationUuid UUID of existing Location.
     * @throws NotFoundException when the Location with the given UUID is not found.
     */
    void disableLocation(String locationUuid) throws NotFoundException;

    /**
     * List all push Attributes for the given Location.
     * @param locationUuid UUID of existing Location.
     * @return List of push Attributes.
     * @throws NotFoundException when the Location with the given UUID is not found.
     * @throws LocationException when the push Attributes failed to be retrieved from the Location.
     */
    List<AttributeDefinition> listPushAttributes(String locationUuid) throws NotFoundException, LocationException;

    /**
     * List all issue Attributes for the given Location.
     * @param locationUuid UUID of existing Location.
     * @return List of issue Attributes.
     * @throws NotFoundException when the Location with the given UUID is not found.
     * @throws LocationException when the issue Attributes failed to be retrieved from the Location.
     */
    List<AttributeDefinition> listCsrAttributes(String locationUuid) throws NotFoundException, LocationException;

    /**
     * Remove existing Certificate from the given Location.
     * @param locationUuid UUID of existing Location.
     * @param certificateUuid UUID of existing Certificate.
     * @return Location detail with the removed Certificate.
     * @throws NotFoundException when the Location or Certificate with the given UUID is not found.
     * @throws LocationException when the Certificate failed to be removed from the Location.
     */
    LocationDto removeCertificateFromLocation(String locationUuid, String certificateUuid) throws NotFoundException, LocationException;

    /**
     * Remove existing Certificate from all associated Locations.
     * @param certificateUuid UUID of existing Certificate.
     * @throws NotFoundException when the Certificate with the given UUID is not found.
     */
    void removeCertificateFromLocations(String certificateUuid) throws NotFoundException;

    /**
     * Push existing Certificate to the given Location.
     * @param locationUuid UUID of existing Location.
     * @param certificateUuid UUID of existing Certificate.
     * @param pushToLocationRequestDto Request containing information to push the Certificate, see {@link PushToLocationRequestDto}.
     * @return Location detail with the pushed Certificate.
     * @throws NotFoundException when the Location or Certificate with the given UUID is not found.
     * @throws LocationException when the Certificate failed to be pushed to the Location.
     */
    LocationDto pushCertificateToLocation(String locationUuid, String certificateUuid, PushToLocationRequestDto pushToLocationRequestDto) throws NotFoundException, LocationException;

    /**
     * Issue new Certificate to the given Location.
     * @param locationUuid UUID of existing Location.
     * @param issueToLocationRequestDto Request containing information to issue the Certificate, see {@link IssueToLocationRequestDto}.
     * @return Location detail with the issued Certificate.
     * @throws NotFoundException when the Location with the given UUID is not found.
     * @throws LocationException when the Certificate failed to be issued to the Location.
     */
    LocationDto issueCertificateToLocation(String locationUuid, IssueToLocationRequestDto issueToLocationRequestDto) throws NotFoundException, LocationException;

    /**
     * Synchronize and update content for the given Location.
     * @param locationUuid UUID of existing Location.
     * @return Location detail with the updated content.
     * @throws NotFoundException when the Location with the given UUID is not found.
     * @throws LocationException when the content failed to be synchronized from the Location.
     */
    LocationDto updateLocationContent(String locationUuid) throws NotFoundException, LocationException;

    /**
     * Renew the Certificate for the given Location.
     * @param locationUuid UUID of existing Location.
     * @param certificateUuid UUID of existing Certificate.
     * @return Location detail with the renewed Certificate.
     * @throws NotFoundException when the Location or Certificate with the given UUID is not found.
     * @throws LocationException when the Certificate failed to be renewed in the Location.
     */
    LocationDto renewCertificateInLocation(String locationUuid, String certificateUuid) throws NotFoundException, LocationException;

}
