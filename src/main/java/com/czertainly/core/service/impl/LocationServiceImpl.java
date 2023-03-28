package com.czertainly.core.service.impl;

import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.clients.LocationApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.connector.entity.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.location.CertificateInLocationDto;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.CertificateLocationRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.LocationRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationServiceImpl implements LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationServiceImpl.class);

    private static final List<AttributeContentType> TO_BE_MASKED = List.of(AttributeContentType.SECRET);
    private LocationRepository locationRepository;
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    private CertificateLocationRepository certificateLocationRepository;
    private RaProfileRepository raProfileRepository;
    private EntityInstanceApiClient entityInstanceApiClient;
    private LocationApiClient locationApiClient;
    private CertificateService certificateService;
    private ClientOperationService clientOperationService;
    private CertificateEventHistoryService certificateEventHistoryService;
    private MetadataService metadataService;
    private AttributeService attributeService;
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    public void setLocationRepository(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Autowired
    public void setEntityInstanceReferenceRepository(EntityInstanceReferenceRepository entityInstanceReferenceRepository) {
        this.entityInstanceReferenceRepository = entityInstanceReferenceRepository;
    }

    @Autowired
    public void setCertificateLocationRepository(CertificateLocationRepository certificateLocationRepository) {
        this.certificateLocationRepository = certificateLocationRepository;
    }

    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) {
        this.raProfileRepository = raProfileRepository;
    }

    @Autowired
    public void setEntityInstanceApiClient(EntityInstanceApiClient entityInstanceApiClient) {
        this.entityInstanceApiClient = entityInstanceApiClient;
    }

    @Autowired
    public void setLocationApiClient(LocationApiClient locationApiClient) {
        this.locationApiClient = locationApiClient;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) {
        this.clientOperationService = clientOperationService;
    }

    @Autowired
    public void setCertificateEventHistoryService(CertificateEventHistoryService certificateEventHistoryService) {
        this.certificateEventHistoryService = certificateEventHistoryService;
    }

    @Autowired
    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Autowired
    public void setAttributeService(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @Autowired
    public void setPermissionEvaluator(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.LIST, parentResource = Resource.ENTITY, parentAction = ResourceAction.LIST)
    public List<LocationDto> listLocation(SecurityFilter filter) {
        filter.setParentRefProperty("entityInstanceReferenceUuid");
        List<Location> locations = locationRepository.findUsingSecurityFilter(filter);
        return locations.stream().map(Location::mapToDtoSimple).collect(Collectors.toList());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.LIST)
    public List<LocationDto> listLocations(SecurityFilter filter, Optional<Boolean> enabled) {
        if (enabled == null || !enabled.isPresent()) {
            return locationRepository
                    .findUsingSecurityFilter(filter)
                    .stream()
                    .map(Location::mapToDtoSimple)
                    .collect(Collectors.toList());
        } else {
            return locationRepository
                    .findUsingSecurityFilter(filter, enabled.get())
                    .stream()
                    .map(Location::mapToDtoSimple)
                    .collect(Collectors.toList());
        }
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.CREATE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto addLocation(SecuredParentUUID entityUuid, AddLocationRequestDto dto) throws AlreadyExistException, LocationException, NotFoundException {
        if (StringUtils.isBlank(dto.getName())) {
            throw new ValidationException(ValidationError.create("Location name must not be empty"));
        }

        Optional<Location> o = locationRepository.findByName(dto.getName());
        if (o.isPresent()) {
            throw new AlreadyExistException(Location.class, dto.getName());
        }

        EntityInstanceReference entityInstanceRef = entityInstanceReferenceRepository.findByUuid(entityUuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, entityUuid));
        attributeService.validateCustomAttributes(dto.getCustomAttributes(), Resource.LOCATION);
        validateLocationCreation(entityInstanceRef, dto.getAttributes());
        List<DataAttribute> attributes = validateAttributes(entityInstanceRef, dto.getAttributes(), dto.getName());
        LocationDetailResponseDto locationDetailResponseDto = getLocationDetail(entityInstanceRef, dto.getAttributes(), dto.getName());
        Location location;
        try {
            location = createLocation(dto, attributes, entityInstanceRef, locationDetailResponseDto);
        } catch (CertificateException e) {
            logger.debug("Failed to create Location {}: {}", dto.getName(), e.getMessage());
            throw new LocationException("Failed to create Location " + dto.getName());
        }

        attributeService.createAttributeContent(location.getUuid(), dto.getCustomAttributes(), Resource.LOCATION);

        logger.info("Location with name {} and UUID {} created", location.getName(), location.getUuid());

        LocationDto locationDto = location.mapToDto();
        locationDto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(locationDto.getUuid()), Resource.LOCATION));
        });
        locationDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));

        return locationDto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.DETAIL, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto getLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));
        LocationDto dto = location.mapToDto();
        dto.setMetadata(metadataService.getFullMetadata(location.getUuid(), Resource.LOCATION, null, null));
        for (CertificateInLocationDto certificate : dto.getCertificates()) {
            certificate.setMetadata(metadataService.getFullMetadata(UUID.fromString(certificate.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(dto.getUuid()), Resource.LOCATION));
        }
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));
        dto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(dto.getUuid()), Resource.LOCATION));
        });
        return dto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto editLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid, EditLocationRequestDto dto) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        EntityInstanceReference entityInstanceRef;
        entityInstanceRef = entityInstanceReferenceRepository.findByUuid(entityUuid)
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, entityUuid));
        attributeService.validateCustomAttributes(dto.getCustomAttributes(), Resource.LOCATION);
        List<DataAttribute> attributes = validateAttributes(entityInstanceRef, dto.getAttributes(), location.getName());
        LocationDetailResponseDto locationDetailResponseDto = getLocationDetail(entityInstanceRef, dto.getAttributes(), location.getName());

        //Location updatedLocation = updateLocation(location, entityInstanceRef, dto, attributes, locationDetailResponseDto);

        try {
            updateLocation(location, entityInstanceRef, dto, attributes, locationDetailResponseDto);
        } catch (CertificateException e) {
            logger.debug("Failed to update Location {}, {} content: {}", location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to update Location content: " + location.getName() + ", " + location.getUuid());
        }
        attributeService.updateAttributeContent(location.getUuid(), dto.getCustomAttributes(), Resource.LOCATION);

        logger.info("Location with name {} and UUID {} updated", location.getName(), location.getUuid());

        LocationDto locationDto = location.mapToDto();
        locationDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));
        locationDto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(locationDto.getUuid()), Resource.LOCATION));
        });
        return locationDto;
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.DELETE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public void deleteLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        List<ValidationError> errors = new ArrayList<>();
        if (!location.getCertificates().isEmpty()) {
            errors.add(ValidationError.create("Location {} contains {} Certificate", location.getName(),
                    location.getCertificates().size()));
            location.getCertificates().forEach(c -> errors.add(ValidationError.create(c.getCertificate().getUuid().toString())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete Location", errors);
        }

        certificateLocationRepository.deleteAll(location.getCertificates());
        attributeService.deleteAttributeContent(location.getUuid(), Resource.LOCATION);
        locationRepository.delete(location);

        logger.info("Location {} was deleted", location.getName());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.ENABLE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public void enableLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        location.setEnabled(true);
        locationRepository.save(location);

        logger.info("Location {} enabled", location.getName());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.ENABLE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public void disableLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        location.setEnabled(false);
        locationRepository.save(location);

        logger.info("Location {} disabled", location.getName());
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.ANY, parentResource = Resource.ENTITY, parentAction = ResourceAction.ANY)
    public List<BaseAttribute> listPushAttributes(SecuredParentUUID entityUuid, SecuredUUID locationUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid.getValue())
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        try {
            return locationApiClient.listPushCertificateAttributes(
                    location.getEntityInstanceReference().getConnector().mapToDto(),
                    location.getEntityInstanceReference().getEntityInstanceUuid());
        } catch (ConnectorException e) {
            logger.debug("Failed to list push Attributes for Location {}, {}: {}",
                    location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to list push Attributes for the Location " + location.getName() + ". Reason: " + e.getMessage());
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.ANY, parentResource = Resource.ENTITY, parentAction = ResourceAction.ANY)
    public List<BaseAttribute> listCsrAttributes(SecuredParentUUID entityUuid, SecuredUUID locationUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid.getValue())
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        try {
            return locationApiClient.listGenerateCsrAttributes(
                    location.getEntityInstanceReference().getConnector().mapToDto(),
                    location.getEntityInstanceReference().getEntityInstanceUuid());
        } catch (ConnectorException e) {
            logger.debug("Failed to list CSR Attributes for Location {}, {}: {}",
                    location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to list CSR Attributes for the Location " + location.getName() + ". Reason: " + e.getMessage());
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto removeCertificateFromLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid, String certificateUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid.getValue())
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));

        CertificateLocationId clId = new CertificateLocationId(location.getUuid(), certificate.getUuid());
        CertificateLocation certificateInLocation = certificateLocationRepository.findById(clId)
                .orElseThrow(() -> new NotFoundException(CertificateLocation.class, clId));

        try {
            removeCertificateFromLocation(location, certificateInLocation);
        } catch (ConnectorException e) {
            // record event in the certificate history
            String message = "Remove from Location " + location.getName();
            HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put("locationUuid", location.getUuid());
            additionalInformation.put("cause", message);
            certificateEventHistoryService.addEventHistory(
                    CertificateEvent.UPDATE_LOCATION,
                    CertificateEventStatus.FAILED,
                    message,
                    additionalInformation,
                    certificate
            );
            logger.debug("Failed to remove Certificate {} from Location {},{}: {}", certificate.getUuid(),
                    location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to remove Certificate " + certificate.getUuid() +
                    " from Location " + location.getName() + ". Reason: " + e.getMessage());
        }

        // save record into the certificate history
        String message = "Removed from Location " + location.getName();
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("locationUuid", location.getUuid());
        certificateEventHistoryService.addEventHistory(
                CertificateEvent.UPDATE_LOCATION,
                CertificateEventStatus.SUCCESS,
                message,
                additionalInformation,
                certificate
        );

        logger.info("Certificate {} removed from Location {}", certificateUuid, location.getName());
        LocationDto locationDto = location.mapToDto();
        locationDto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(locationDto.getUuid()), Resource.LOCATION));
        });
        locationDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));
        return locationDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE)
    // TODO List locations using LocationService in CertificateService and then call removeCertificateFromLocations(SecuredUUID locationUUIDs, String certificateUUID) on location service
    public void removeCertificateFromLocations(SecuredUUID certificateUuid) throws NotFoundException {
        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

        Set<CertificateLocation> certificateLocations = certificate.getLocations();
        for (CertificateLocation cl : certificateLocations) {
            try {
                if (!cl.getLocation().getEnabled()) {
                    throw new NotFoundException(Location.class, cl.getLocation().getUuid());
                }

                removeCertificateFromLocation(cl);
                certificateLocations.remove(cl);

                // save record into the certificate history
                String message = "Removed from Location " + cl.getLocation().getName();
                HashMap<String, Object> additionalInformation = new HashMap<>();
                additionalInformation.put("locationUuid", cl.getLocation().getUuid());
                certificateEventHistoryService.addEventHistory(
                        CertificateEvent.UPDATE_LOCATION,
                        CertificateEventStatus.SUCCESS,
                        message,
                        additionalInformation,
                        certificate
                );

                logger.info("Certificate {} removed from Location {}", certificateUuid, cl.getLocation().getName());
            } catch (ConnectorException e) {
                // record event in the certificate history
                String message = "Remove from Location " + cl.getLocation().getName();
                HashMap<String, Object> additionalInformation = new HashMap<>();
                additionalInformation.put("locationUuid", cl.getLocation().getUuid());
                additionalInformation.put("cause", message);
                certificateEventHistoryService.addEventHistory(
                        CertificateEvent.UPDATE_LOCATION,
                        CertificateEventStatus.FAILED,
                        message,
                        additionalInformation,
                        certificate
                );
                logger.debug("Failed to remove Certificate {} from Location {}, {}: {}", certificate.getUuid(),
                        cl.getLocation().getName(), cl.getLocation().getUuid(), e.getMessage());
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto pushCertificateToLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid, String certificateUuid, PushToLocationRequestDto request) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid.getValue())
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));

        if (!location.isSupportMultipleEntries() && location.getCertificates().size() >= 1) {
            // record event in the certificate history
            String message = "Location " + location.getName() + " does not support multiple entries";
            HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put("locationUuid", location.getUuid());
            additionalInformation.put("cause", message);
            certificateEventHistoryService.addEventHistory(
                    CertificateEvent.UPDATE_LOCATION,
                    CertificateEventStatus.FAILED,
                    message,
                    additionalInformation,
                    certificate
            );
            logger.debug("Location {}, {} does not support multiple entries", location.getName(), location.getUuid());
            throw new LocationException("Location " + location.getName() + " does not support multiple entries");
        }

        pushCertificateToLocation(
                location, certificate,
                request.getAttributes(), List.of());

        logger.info("Certificate {} successfully pushed to Location {}", certificateUuid, location.getName());

        LocationDto dto = location.mapToDto();
        dto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(dto.getUuid()), Resource.LOCATION));
        });
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));
        return dto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto issueCertificateToLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid, String raProfileUuid, IssueToLocationRequestDto request) throws ConnectorException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid.getValue())
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        if (!location.isSupportKeyManagement()) {
            logger.debug("Location {}, {} does not support key management", location.getName(), location.getUuid());
            throw new LocationException("Location " + location.getName() + " does not support key management");
        }
        if (!location.isSupportMultipleEntries() && location.getCertificates().size() >= 1) {
            logger.debug("Location {}, {} does not support multiple entries", location.getName(), location.getUuid());
            throw new LocationException("Location " + location.getName() + " does not support multiple entries");
        }
        RaProfile raProfile = raProfileRepository.findByUuid(UUID.fromString(raProfileUuid)).orElseThrow(() -> new NotFoundException(raProfileUuid, RaProfile.class));
        authorityPreChecks(raProfile);
        // generate new CSR
        GenerateCsrResponseDto generateCsrResponseDto = generateCsrLocation(location, request.getCsrAttributes(), false);
        logger.info("Received certificate signing request from Location {}", location.getName());

        // issue new Certificate
        Certificate certificate = null;
        ClientCertificateDataResponseDto clientCertificateDataResponseDto = null;
        try {
            clientCertificateDataResponseDto = issueCertificateForLocation(
                    location, generateCsrResponseDto.getCsr(), request.getIssueAttributes(), raProfileUuid,
                    request.getCertificateCustomAttributes());
            certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(clientCertificateDataResponseDto.getUuid()));
        } catch (Exception e) {
            logger.error("Failed to issue Certificate", e.getMessage());
            removeStash(location, generateCsrResponseDto.getMetadata());
            throw new LocationException("Failed to issue certificate to the location. Error Issuing certificate from Authority. " + e.getMessage());
        }

        // push new Certificate to Location
        try {
            pushCertificateToLocation(
                    location, certificate,
                    generateCsrResponseDto.getPushAttributes(), request.getCsrAttributes());
        } catch (Exception e) {
            logger.error("Failed to push new certificate to location", e.getMessage());
            removeStash(location, generateCsrResponseDto.getMetadata());
            throw new LocationException("Failed to push new certificate to the location. Reason: " + e.getMessage());
        }

        logger.info("Certificate {} successfully issued and pushed to Location {}", clientCertificateDataResponseDto.getUuid(), location.getName());

        LocationDto locationDto = location.mapToDto();
        locationDto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(locationDto.getUuid()), Resource.LOCATION));
        });
        locationDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));
        return locationDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto updateLocationContent(SecuredParentUUID entityUuid, SecuredUUID locationUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid.getValue())
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        EntityInstanceReference entityInstanceRef = location.getEntityInstanceReference();

        LocationDetailRequestDto locationDetailRequestDto = new LocationDetailRequestDto();
        locationDetailRequestDto.setLocationAttributes(location.getRequestAttributes());

        LocationDetailResponseDto locationDetailResponseDto;
        try {
            locationDetailResponseDto = locationApiClient.getLocationDetail(
                    entityInstanceRef.getConnector().mapToDto(), entityInstanceRef.getEntityInstanceUuid(), locationDetailRequestDto);
        } catch (ConnectorException e) {
            logger.debug("Failed to get Location details: {}, {}, reason: {}", location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to get details for Location " + location.getName() + ". Reason: " + e.getMessage());
        }

        try {
            updateLocationContent(location, locationDetailResponseDto);
        } catch (CertificateException e) {
            logger.debug("Failed to update Location {}, {} content: {}", location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to update content for Location " + location.getName());
        }

        logger.info("Location with name {} and UUID {} synced", location.getName(), location.getUuid());

        LocationDto locationDto = location.mapToDto();
        locationDto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(locationDto.getUuid()), Resource.LOCATION));
        });
        locationDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));
        return locationDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE, parentResource = Resource.ENTITY, parentAction = ResourceAction.DETAIL)
    public LocationDto renewCertificateInLocation(SecuredParentUUID entityUuid, SecuredUUID locationUuid, String certificateUuid) throws ConnectorException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid.getValue())
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        CertificateLocation certificateLocation = getCertificateLocation(locationUuid.toString(), certificateUuid);

        // Check if everything is available to do the renewal
        if (certificateLocation.getPushAttributes() == null || certificateLocation.getPushAttributes().isEmpty()) {
            logger.debug("Renewal of the Certificate {} in the Location {}, {} is not possible because the push Attributes are not available",
                    certificateLocation.getCertificate().getUuid(), certificateLocation.getLocation().getName(), certificateLocation.getLocation().getUuid());
            throw new LocationException("Renewal of the Certificate " + certificateUuid + " in the Location " +
                    certificateLocation.getLocation().getName() + " is not possible because the push Attributes are not available");
        }
        if (certificateLocation.getCsrAttributes() == null || certificateLocation.getCsrAttributes().isEmpty()) {
            logger.debug("Renewal of the Certificate {} in the Location {}, {} is not possible because the CSR Attributes are not available",
                    certificateLocation.getCertificate().getUuid(), certificateLocation.getLocation().getName(), certificateLocation.getLocation().getUuid());
            throw new LocationException("Renewal of the certificate " + certificateUuid + " in the location " +
                    certificateLocation.getLocation().getName() + " is not possible because the CSR Attributes are not available");
        }
        if (!certificateLocation.getLocation().isSupportKeyManagement()) {
            logger.debug("Location {}, {} does not support key management", certificateLocation.getLocation().getName(), certificateLocation.getLocation().getUuid());
            throw new LocationException("Location " + certificateLocation.getLocation().getName() + " does not support key management");
        }

        Certificate certificateInScope = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));
        if (certificateInScope.getRaProfile() == null) {
            logger.debug("Certificate {} is not associated with any RA Profile. Cannot renew the certificate", certificateInScope.getCommonName());
            throw new LocationException("Certificate is not associated with any RA Profile. Cannot renew the certificate in the location");
        }
        //Prechecks for certificate renewal
        String raProfileUuid = certificateInScope.getRaProfile().getUuid().toString();
        RaProfile raProfile = raProfileRepository.findByUuid(UUID.fromString(raProfileUuid)).orElseThrow(() -> new NotFoundException(raProfileUuid, RaProfile.class));
        authorityPreChecks(raProfile);

        // generate new CSR
        GenerateCsrResponseDto generateCsrResponseDto = generateCsrLocation(certificateLocation.getLocation(), AttributeDefinitionUtils.getClientAttributes(certificateLocation.getCsrAttributes()), true);
        logger.info("Received certificate signing request from Location {}", certificateLocation.getLocation().getName());

        // renew existing Certificate
        ClientCertificateDataResponseDto clientCertificateDataResponseDto = null;
        Certificate certificate = null;

        try {
            clientCertificateDataResponseDto = renewCertificate(certificateLocation, generateCsrResponseDto.getCsr());
            certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(clientCertificateDataResponseDto.getUuid()));
        } catch (Exception e) {
            logger.error("Failed to renew Certificate", e.getMessage());
            throw new LocationException("Failed to renew certificate to the location. Error Issuing certificate from Authority. " + e.getMessage());
        }

        // push renewed Certificate to Location
        try {
            pushCertificateToLocation(
                    certificateLocation.getLocation(), certificate,
                    generateCsrResponseDto.getPushAttributes(), AttributeDefinitionUtils.getClientAttributes(certificateLocation.getCsrAttributes()));

            //Delete current certificate in location table
            CertificateLocationId clId = new CertificateLocationId(location.getUuid(), certificateInScope.getUuid());
            CertificateLocation certificateInLocation = certificateLocationRepository.findById(clId)
                    .orElse(null);

            if (certificateInLocation != null) {

                certificateLocationRepository.delete(certificateInLocation);
                location.getCertificates().remove(certificateLocation);
                locationRepository.save(location);
            }
        } catch (Exception e) {
            logger.error("Failed to Push new Certificate", e.getMessage());
            throw new LocationException("Failed to push the new certificate to the location. Reason: " + e.getMessage());
        }

        logger.info("Certificate {} successfully issued and pushed to Location {}", clientCertificateDataResponseDto.getUuid(), certificateLocation.getLocation().getName());

        LocationDto locationDto = location.mapToDto();
        locationDto.getCertificates().forEach(e -> {
            e.setMetadata(metadataService.getFullMetadata(UUID.fromString(e.getCertificateUuid()), Resource.CERTIFICATE, UUID.fromString(locationDto.getUuid()), Resource.LOCATION));
        });
        locationDto.setCustomAttributes(attributeService.getCustomAttributesWithValues(location.getUuid(), Resource.LOCATION));
        return locationDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.LIST)
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return locationRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(Location::mapToAccessControlObjects)
                .collect(Collectors.toList());
    }

    @Override
    @ExternalAuthorization(resource = Resource.LOCATION, action = ResourceAction.UPDATE)
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(Location.class, uuid));
        if (location.getEntityInstanceReference() == null) {
            return;
        }
        // Parent Permission evaluation - Entity Instance
        permissionEvaluator.authorityInstance(location.getEntityInstanceReference().getSecuredUuid());

    }

    // PRIVATE METHODS

    private GenerateCsrResponseDto generateCsrLocation(Location location, List<RequestAttributeDto> csrAttributes, Boolean isRenewalRequest) throws LocationException {
        GenerateCsrRequestDto generateCsrRequestDto = new GenerateCsrRequestDto();
        generateCsrRequestDto.setLocationAttributes(location.getRequestAttributes());
        generateCsrRequestDto.setCsrAttributes(csrAttributes);
        generateCsrRequestDto.setRenewal(isRenewalRequest);

        GenerateCsrResponseDto generateCsrResponseDto;
        try {
            generateCsrResponseDto = locationApiClient.generateCsrLocation(
                    location.getEntityInstanceReference().getConnector().mapToDto(),
                    location.getEntityInstanceReference().getEntityInstanceUuid(),
                    generateCsrRequestDto
            );
        } catch (ConnectorException e) {
            logger.debug("Failed to generate CSR for the Location " + location.getName() + ", " + location.getUuid() +
                    ", with Attributes " + csrAttributes + ": " + e.getMessage());
            throw new LocationException("Failed to generate CSR for Location " + location.getName() + ". Reason: " + e.getMessage());
        }
        return generateCsrResponseDto;
    }

    private ClientCertificateDataResponseDto issueCertificateForLocation(Location location, String csr, List<RequestAttributeDto> issueAttributes, String raProfileUuid, List<RequestAttributeDto> certificateCustomAttributes) throws LocationException {
        ClientCertificateSignRequestDto clientCertificateSignRequestDto = new ClientCertificateSignRequestDto();
        clientCertificateSignRequestDto.setAttributes(issueAttributes);
        // TODO: support for different types of certificate
        clientCertificateSignRequestDto.setPkcs10(csr);
        clientCertificateSignRequestDto.setCustomAttributes(certificateCustomAttributes);
        ClientCertificateDataResponseDto clientCertificateDataResponseDto;
        try {
            // TODO : introduces raProfileRepository, services probably need to be reorganized
            Optional<RaProfile> raProfile = raProfileRepository.findByUuid(UUID.fromString(raProfileUuid));
            if (raProfile.isEmpty() || raProfile.get().getAuthorityInstanceReferenceUuid() == null) {
                logger.debug("Failed to issue Certificate for Location " + location.getName() + ", " + location.getUuid() +
                        ". RA profile is not existing or does not have set authority");
                throw new LocationException("Failed to issue Certificate for Location " + location.getName() + ". RA profile is not existing or does not have set authority");
            }
            clientCertificateDataResponseDto = clientOperationService.issueCertificate(SecuredParentUUID.fromUUID(raProfile.get().getAuthorityInstanceReferenceUuid()), raProfile.get().getSecuredUuid(), clientCertificateSignRequestDto);
        } catch (ConnectorException | AlreadyExistException | java.security.cert.CertificateException | NoSuchAlgorithmException e) {
            logger.debug("Failed to issue Certificate for Location " + location.getName() + ", " + location.getUuid() +
                    ": " + e.getMessage());
            throw new LocationException("Failed to issue Certificate for Location " + location.getName() + ". Reason: " + e.getMessage());
        }
        return clientCertificateDataResponseDto;
    }

    private ClientCertificateDataResponseDto renewCertificate(CertificateLocation certificateLocation, String csr) throws LocationException {
        ClientCertificateRenewRequestDto clientCertificateRenewRequestDto = new ClientCertificateRenewRequestDto();
        clientCertificateRenewRequestDto.setPkcs10(csr);
        clientCertificateRenewRequestDto.setReplaceInLocations(false);

        ClientCertificateDataResponseDto clientCertificateDataResponseDto;
        try {
            clientCertificateDataResponseDto = clientOperationService.renewCertificate(
                    SecuredParentUUID.fromUUID(certificateLocation.getCertificate().getRaProfile().getAuthorityInstanceReferenceUuid()),
                    certificateLocation.getCertificate().getRaProfile().getSecuredUuid(),
                    certificateLocation.getCertificate().getSecuredUuid().toString(),
                    clientCertificateRenewRequestDto
            );
        } catch (ConnectorException | AlreadyExistException | java.security.cert.CertificateException |
                CertificateOperationException e) {
            logger.debug("Failed to renew Certificate for Location " + certificateLocation.getLocation().getName() +
                    ", " + certificateLocation.getLocation().getUuid() + ": " + e.getMessage());
            throw new LocationException("Failed to renew Certificate for Location " + certificateLocation.getLocation().getName() + ". Reason: " + e.getMessage());
        }
        return clientCertificateDataResponseDto;
    }

    private void pushCertificateToLocation(Location location, Certificate certificate,
                                           List<RequestAttributeDto> pushAttributes, List<RequestAttributeDto> csrAttributes
    ) throws LocationException {
        PushCertificateRequestDto pushCertificateRequestDto = new PushCertificateRequestDto();
        pushCertificateRequestDto.setCertificate(certificate.getCertificateContent().getContent());
        // TODO: support for different types of certificate
        pushCertificateRequestDto.setCertificateType(CertificateType.X509);
        pushCertificateRequestDto.setLocationAttributes(location.getRequestAttributes());
        pushCertificateRequestDto.setPushAttributes(pushAttributes);

        PushCertificateResponseDto pushCertificateResponseDto;
        try {
            pushCertificateResponseDto = locationApiClient.pushCertificateToLocation(
                    location.getEntityInstanceReference().getConnector().mapToDto(),
                    location.getEntityInstanceReference().getEntityInstanceUuid(),
                    pushCertificateRequestDto
            );
        } catch (ConnectorException e) {
            // record event in the certificate history
            String message = "Failed to push to Location " + location.getName();
            HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put("locationUuid", location.getUuid());
            additionalInformation.put("cause", e.getMessage());
            certificateEventHistoryService.addEventHistory(
                    CertificateEvent.UPDATE_LOCATION,
                    CertificateEventStatus.FAILED,
                    message,
                    additionalInformation,
                    certificate
            );
            logger.debug("Failed to push Certificate {} to Location {}, {}: {}",
                    certificate.getUuid(), location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to push Certificate " + certificate.getUuid() +
                    " to Location " + location.getName() + ". Reason: " + e.getMessage());
        }

        //Get the list of Push and CSR Attributes from the connector. This will then be merged with the user request and
        //stored in the database
        List<BaseAttribute> fullPushAttributes;
        List<BaseAttribute> fullCsrAttributes;
        try {
            fullPushAttributes = listPushAttributes(SecuredParentUUID.fromUUID(location.getEntityInstanceReferenceUuid()), SecuredUUID.fromString(location.getUuid().toString()));
            fullCsrAttributes = listCsrAttributes(SecuredParentUUID.fromUUID(location.getEntityInstanceReferenceUuid()), SecuredUUID.fromString(location.getUuid().toString()));
        } catch (NotFoundException e) {
            logger.error("Unable to find the location with uuid: {}", location.getUuid());
            throw new LocationException("Failed to get Attributes for Location: " + location.getName() + ". Location not found");
        }

        List<DataAttribute> mergedPushAttributes = AttributeDefinitionUtils.mergeAttributes(fullPushAttributes, pushAttributes);
        List<DataAttribute> mergedCsrAttributes = AttributeDefinitionUtils.mergeAttributes(fullCsrAttributes, csrAttributes);

        CertificateLocation certificateLocation = new CertificateLocation();
        certificateLocation.setLocation(location);
        certificateLocation.setCertificate(certificate);
        metadataService.createMetadataDefinitions(location.getEntityInstanceReference().getConnector().getUuid(), pushCertificateResponseDto.getCertificateMetadata());
        metadataService.createMetadata(location.getEntityInstanceReference().getConnector().getUuid(),
                certificate.getUuid(),
                location.getUuid(),
                location.getName(),
                pushCertificateResponseDto.getCertificateMetadata(),
                Resource.CERTIFICATE,
                Resource.LOCATION);
        certificateLocation.setWithKey(pushCertificateResponseDto.isWithKey());
        certificateLocation.setPushAttributes(mergedPushAttributes);
        certificateLocation.setCsrAttributes(mergedCsrAttributes);

        // TODO: response with the indication if the key is available for pushed certificate

        certificateLocationRepository.save(certificateLocation);
        location.getCertificates().add(certificateLocation);

        locationRepository.save(location);

        // save record into the certificate history
        String message = "Pushed to Location " + location.getName();
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("locationUuid", location.getUuid());
        certificateEventHistoryService.addEventHistory(
                CertificateEvent.UPDATE_LOCATION,
                CertificateEventStatus.SUCCESS,
                message,
                additionalInformation,
                certificate
        );
    }

    private List<DataAttribute> validateAttributes(EntityInstanceReference entityInstanceReference, List<RequestAttributeDto> requestAttributes, String locationName) throws LocationException {
        List<DataAttribute> attributes;
        try {
            attributes = mergeAndValidateAttributes(entityInstanceReference, requestAttributes);
        } catch (ConnectorException e) {
            // TODO: masking of the SECRET Attributes in the debug message?
            logger.debug("Failed to validate Attributes {} for the Location {}: {}", requestAttributes, locationName, e.getMessage());
            throw new LocationException("Failed to create Location: " + locationName + ". Reason: " + e.getMessage());
        }
        return attributes;
    }

    private LocationDetailResponseDto getLocationDetail(EntityInstanceReference entityInstanceReference, List<RequestAttributeDto> requestAttributes, String locationName) throws LocationException {
        LocationDetailRequestDto locationDetailRequestDto = new LocationDetailRequestDto();
        locationDetailRequestDto.setLocationAttributes(requestAttributes);
        LocationDetailResponseDto locationDetailResponseDto;
        try {
            locationDetailResponseDto = locationApiClient.getLocationDetail(
                    entityInstanceReference.getConnector().mapToDto(), entityInstanceReference.getEntityInstanceUuid(), locationDetailRequestDto);
        } catch (ConnectorException e) {
            logger.debug("Failed to get Location {} details: {}", locationName, e.getMessage());
            throw new LocationException("Failed to get details for Location " + locationName + ". Reason: " + e.getMessage());
        }
        return locationDetailResponseDto;
    }

    private CertificateLocation getCertificateLocation(String locationUuid, String certificateUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(UUID.fromString(locationUuid))
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(SecuredUUID.fromString(certificateUuid));

        CertificateLocationId clId = new CertificateLocationId(location.getUuid(), certificate.getUuid());
        return certificateLocationRepository.findById(clId)
                .orElseThrow(() -> new NotFoundException(CertificateLocation.class, clId));
    }

    private void removeCertificateFromLocation(CertificateLocation certificateLocation) throws ConnectorException {
        RemoveCertificateRequestDto removeCertificateRequestDto = new RemoveCertificateRequestDto();
        removeCertificateRequestDto.setLocationAttributes(certificateLocation.getLocation().getRequestAttributes());
        List<MetadataAttribute> metadata = metadataService.getMetadata(
                certificateLocation.getLocation().getEntityInstanceReference().getConnectorUuid(),
                certificateLocation.getCertificate().getUuid(),
                Resource.CERTIFICATE,
                certificateLocation.getLocation().getUuid(),
                Resource.LOCATION);
        removeCertificateRequestDto.setCertificateMetadata(metadata);
        attributeService.deleteAttributeContent(
                certificateLocation.getCertificate().getUuid(),
                Resource.CERTIFICATE,
                certificateLocation.getLocation().getUuid(),
                Resource.LOCATION,
                AttributeType.META
        );
        locationApiClient.removeCertificateFromLocation(
                certificateLocation.getLocation().getEntityInstanceReference().getConnector().mapToDto(),
                certificateLocation.getLocation().getEntityInstanceReference().getEntityInstanceUuid(),
                removeCertificateRequestDto
        );

        certificateLocationRepository.delete(certificateLocation);
    }

    private void removeCertificateFromLocation(Location entity, CertificateLocation certificateLocation) throws ConnectorException {
        RemoveCertificateRequestDto removeCertificateRequestDto = new RemoveCertificateRequestDto();
        removeCertificateRequestDto.setLocationAttributes(entity.getRequestAttributes());
        List<MetadataAttribute> metadata = metadataService.getMetadata(
                certificateLocation.getLocation().getEntityInstanceReference().getConnectorUuid(),
                certificateLocation.getCertificate().getUuid(),
                Resource.CERTIFICATE,
                certificateLocation.getLocation().getUuid(),
                Resource.LOCATION);
        removeCertificateRequestDto.setCertificateMetadata(metadata);

        locationApiClient.removeCertificateFromLocation(
                entity.getEntityInstanceReference().getConnector().mapToDto(),
                entity.getEntityInstanceReference().getEntityInstanceUuid(),
                removeCertificateRequestDto
        );

        certificateLocationRepository.delete(certificateLocation);

        attributeService.deleteAttributeContent(
                certificateLocation.getCertificate().getUuid(),
                Resource.CERTIFICATE,
                certificateLocation.getLocation().getUuid(),
                Resource.LOCATION,
                AttributeType.META
        );

        entity.getCertificates().remove(certificateLocation);

        locationRepository.save(entity);
    }

    private List<DataAttribute> mergeAndValidateAttributes(EntityInstanceReference entityInstanceRef, List<RequestAttributeDto> attributes) throws ConnectorException {
        List<BaseAttribute> definitions = entityInstanceApiClient.listLocationAttributes(
                entityInstanceRef.getConnector().mapToDto(),
                entityInstanceRef.getEntityInstanceUuid());

        List<String> existingAttributesFromConnector = definitions.stream().map(BaseAttribute::getName).collect(Collectors.toList());
        for (RequestAttributeDto requestAttributeDto : attributes) {
            if (!existingAttributesFromConnector.contains(requestAttributeDto.getName())) {
                DataAttribute referencedAttribute = attributeService.getReferenceAttribute(entityInstanceRef.getConnectorUuid(), requestAttributeDto.getName());
                if (referencedAttribute != null) {
                    definitions.add(referencedAttribute);
                }
            }
        }

        List<DataAttribute> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);

        entityInstanceApiClient.validateLocationAttributes(
                entityInstanceRef.getConnector().mapToDto(),
                entityInstanceRef.getEntityInstanceUuid(),
                attributes);

        return merged;
    }

    private Location createLocation(AddLocationRequestDto dto, List<DataAttribute> attributes,
                                    EntityInstanceReference entityInstanceRef, LocationDetailResponseDto locationDetailResponseDto) throws CertificateException {
        Location entity = new Location();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAttributes(attributes);
        entity.setEntityInstanceReference(entityInstanceRef);
        entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        entity.setEntityInstanceName(entityInstanceRef.getName());
        locationRepository.save(entity);
        updateContent(entity,
                locationDetailResponseDto.isMultipleEntries(),
                locationDetailResponseDto.isSupportKeyManagement(),
                locationDetailResponseDto.getMetadata(),
                locationDetailResponseDto.getCertificates());

        return entity;
    }

    private void updateLocation(Location entity, EntityInstanceReference entityInstanceRef, EditLocationRequestDto dto,
                                List<DataAttribute> attributes, LocationDetailResponseDto locationDetailResponseDto) throws CertificateException {
        entity.setDescription(dto.getDescription());
        entity.setAttributes(attributes);
        entity.setEntityInstanceReference(entityInstanceRef);
        if (dto.isEnabled() != null) {
            entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        }
        entity.setEntityInstanceName(entityInstanceRef.getName());

        updateContent(entity,
                locationDetailResponseDto.isMultipleEntries(),
                locationDetailResponseDto.isSupportKeyManagement(),
                locationDetailResponseDto.getMetadata(),
                locationDetailResponseDto.getCertificates());
    }

    private void updateLocationContent(Location entity, LocationDetailResponseDto locationDetailResponseDto) throws CertificateException {
        updateContent(entity,
                locationDetailResponseDto.isMultipleEntries(),
                locationDetailResponseDto.isSupportKeyManagement(),
                locationDetailResponseDto.getMetadata(),
                locationDetailResponseDto.getCertificates());
    }

    private void updateContent(Location entity, boolean supportMultipleEntries, boolean supportKeyManagement,
                               List<MetadataAttribute> metadata, List<CertificateLocationDto> certificates) throws CertificateException {
        entity.setSupportMultipleEntries(supportMultipleEntries);
        entity.setSupportKeyManagement(supportKeyManagement);
        metadataService.createMetadataDefinitions(entity.getEntityInstanceReference().getConnectorUuid(), metadata);
        metadataService.createMetadata(entity.getEntityInstanceReference().getConnectorUuid(),
                entity.getUuid(),
                null,
                null,
                metadata,
                Resource.LOCATION,
                null);
        Set<CertificateLocation> cls = new HashSet<>();
        for (CertificateLocationDto certificateLocationDto : certificates) {
            CertificateLocation cl = new CertificateLocation();
            cl.setWithKey(certificateLocationDto.isWithKey());
            cl.setCertificate(certificateService.createCertificate(certificateLocationDto.getCertificateData(), certificateLocationDto.getCertificateType()));
            metadataService.createMetadataDefinitions(entity.getEntityInstanceReference().getConnectorUuid(), certificateLocationDto.getMetadata());
            metadataService.createMetadata(entity.getEntityInstanceReference().getConnectorUuid(),
                    cl.getCertificate().getUuid(),
                    entity.getUuid(),
                    entity.getName(),
                    certificateLocationDto.getMetadata(),
                    Resource.CERTIFICATE,
                    Resource.LOCATION);
            cl.setLocation(entity);
            cl.setPushAttributes(certificateLocationDto.getPushAttributes());
            cl.setCsrAttributes(certificateLocationDto.getCsrAttributes());
            cls.add(cl);
        }

        Iterator<CertificateLocation> iterator = entity.getCertificates().iterator();
        while (iterator.hasNext()) {
            CertificateLocation cl = iterator.next();

            if (!cls.contains(cl)) {
                certificateLocationRepository.delete(cl);
                iterator.remove();
            } else {
                CertificateLocation lc = cls.stream().filter(e -> e.getCertificate().getUuid().equals(cl.getCertificate().getUuid())).findFirst().orElse(null);
                if (lc != null) {
                    cl.setCsrAttributes(lc.getCsrAttributes());
                    cl.setPushAttributes(lc.getPushAttributes());
                    cl.setWithKey(lc.isWithKey());
                    certificateLocationRepository.save(cl);
                }
            }
        }

        entity.getCertificates().addAll(cls);
        locationRepository.save(entity);
    }

    private void authorityPreChecks(RaProfile raProfile) throws ValidationException {
        //Check if RA Profile is enabled
        if (!raProfile.getEnabled()) {
            throw new ValidationException(ValidationError.create("RA Profile is disabled"));
        }

        //Check if RA Profile has Authority
        if (raProfile.getAuthorityInstanceReference() == null) {
            throw new ValidationException(ValidationError.create("RA Profile does not have proper authority associated"));
        }
        //Check if Authority is Enabled
        if (!raProfile.getAuthorityInstanceReference().getStatus().equals("connected")) {
            throw new ValidationException(ValidationError.create("Associated Authority is not connected"));
        }

        //Check if Authority has Connector
        if (raProfile.getAuthorityInstanceReference().getConnector() == null) {
            throw new ValidationException(ValidationError.create("Associated authority does not have Connector associated"));
        }

        if (!raProfile.getAuthorityInstanceReference().getConnector().getStatus().equals(ConnectorStatus.CONNECTED)) {
            throw new ValidationException(ValidationError.create("Authority Provider has Invalid State"));
        }

    }

    private void removeStash(Location location, List<MetadataAttribute> metadata) throws ConnectorException, LocationException {
        RemoveCertificateRequestDto removeCertificateRequestDto = new RemoveCertificateRequestDto();
        removeCertificateRequestDto.setLocationAttributes(location.getRequestAttributes());
        removeCertificateRequestDto.setCertificateMetadata(metadata);
        locationApiClient.removeCertificateFromLocation(location.getEntityInstanceReference().getConnector().mapToDto(),
                location.getEntityInstanceReference().getEntityInstanceUuid(),
                removeCertificateRequestDto);
    }

    private void validateLocationCreation(EntityInstanceReference entityInstance, List<RequestAttributeDto> requestDto) throws ValidationException {
        for (Location location : locationRepository.findByEntityInstanceReference(entityInstance)) {
            if (AttributeDefinitionUtils.checkAttributeEquality(requestDto, location.getAttributes())) {
                throw new ValidationException(ValidationError.create("Location with same attributes already exists"));
            }
        }
    }
}
