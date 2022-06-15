package com.czertainly.core.service.impl;

import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.clients.LocationApiClient;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CertificateException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.LocationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.AttributeType;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.ResponseAttributeDto;
import com.czertainly.api.model.common.attribute.content.BaseAttributeContent;
import com.czertainly.api.model.connector.entity.CertificateLocationDto;
import com.czertainly.api.model.connector.entity.GenerateCsrRequestDto;
import com.czertainly.api.model.connector.entity.GenerateCsrResponseDto;
import com.czertainly.api.model.connector.entity.LocationDetailRequestDto;
import com.czertainly.api.model.connector.entity.LocationDetailResponseDto;
import com.czertainly.api.model.connector.entity.PushCertificateRequestDto;
import com.czertainly.api.model.connector.entity.PushCertificateResponseDto;
import com.czertainly.api.model.connector.entity.RemoveCertificateRequestDto;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateLocation;
import com.czertainly.core.dao.entity.CertificateLocationId;
import com.czertainly.core.dao.entity.EntityInstanceReference;
import com.czertainly.core.dao.entity.Location;
import com.czertainly.core.dao.repository.CertificateLocationRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.LocationRepository;
import com.czertainly.core.service.CertificateEventHistoryService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.LocationService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationServiceImpl implements LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationServiceImpl.class);

    private static final List<AttributeType> TO_BE_MASKED = List.of(AttributeType.SECRET);

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

    private LocationRepository locationRepository;
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    private CertificateLocationRepository certificateLocationRepository;
    private EntityInstanceApiClient entityInstanceApiClient;
    private LocationApiClient locationApiClient;
    private CertificateService certificateService;
    private ClientOperationService clientOperationService;
    private CertificateEventHistoryService certificateEventHistoryService;

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public List<LocationDto> listLocation() {
        List<Location> locations = locationRepository.findAll();
        return locations.stream().map(Location::mapToDtoSimple).collect(Collectors.toList());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public List<LocationDto> listLocations(Optional<Boolean> enabled) {
        return enabled.map(aBoolean -> locationRepository.findByEnabled(aBoolean).stream().map(Location::mapToDtoSimple).collect(Collectors.toList())).orElseGet(this::listLocation);
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CREATE)
    public LocationDto addLocation(AddLocationRequestDto dto) throws AlreadyExistException, LocationException, NotFoundException {
        if (StringUtils.isBlank(dto.getName())) {
            throw new ValidationException("Location name must not be empty");
        }

        Optional<Location> o = locationRepository.findByName(dto.getName());
        if (o.isPresent()) {
            throw new AlreadyExistException(Location.class, dto.getName());
        }

        EntityInstanceReference entityInstanceRef = entityInstanceReferenceRepository.findByUuid(dto.getEntityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, dto.getEntityInstanceUuid()));

        List<AttributeDefinition> attributes = validateAttributes(entityInstanceRef, dto.getAttributes(), dto.getName());
        LocationDetailResponseDto locationDetailResponseDto = getLocationDetail(entityInstanceRef, dto.getAttributes(), dto.getName());

        Location location;
        try {
            location = createLocation(dto, attributes, entityInstanceRef, locationDetailResponseDto);
        } catch (CertificateException e) {
            logger.debug("Failed to create Location {}: {}", dto.getName(), e.getMessage());
            throw new LocationException("Failed to create Location " + dto.getName());
        }

        logger.info("Location with name {} and UUID {} created", location.getName(), location.getUuid());

        return maskSecret(location.mapToDto());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public LocationDto getLocation(String locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        return maskSecret(location.mapToDto());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CHANGE)
    public LocationDto editLocation(String locationUuid, EditLocationRequestDto dto) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        EntityInstanceReference entityInstanceRef;
        entityInstanceRef = entityInstanceReferenceRepository.findByUuid(dto.getEntityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, dto.getEntityInstanceUuid()));

        List<AttributeDefinition> attributes = validateAttributes(entityInstanceRef, dto.getAttributes(), location.getName());
        LocationDetailResponseDto locationDetailResponseDto = getLocationDetail(entityInstanceRef, dto.getAttributes(), location.getName());

        //Location updatedLocation = updateLocation(location, entityInstanceRef, dto, attributes, locationDetailResponseDto);

        try {
            updateLocation(location, entityInstanceRef, dto, attributes, locationDetailResponseDto);
        } catch (CertificateException e) {
            logger.debug("Failed to update Location {}, {} content: {}", location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to update Location content: " + location.getName() + ", " + location.getUuid());
        }

        logger.info("Location {} with UUID {} updated", location.getName(), location.getUuid());

        return maskSecret(location.mapToDto());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DELETE)
    public void removeLocation(String locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        List<ValidationError> errors = new ArrayList<>();
        if (!location.getCertificates().isEmpty()) {
            errors.add(ValidationError.create("Location {} contains {} Certificate", location.getName(),
                    location.getCertificates().size()));
            location.getCertificates().forEach(c -> errors.add(ValidationError.create(c.getCertificate().getUuid())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete Location", errors);
        }

        certificateLocationRepository.deleteAll(location.getCertificates());
        locationRepository.delete(location);

        logger.info("Location {} was deleted", location.getName());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.ENABLE)
    public void enableLocation(String locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        location.setEnabled(true);
        locationRepository.save(location);

        logger.info("Location {} enabled", location.getName());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DISABLE)
    public void disableLocation(String locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        location.setEnabled(false);
        locationRepository.save(location);

        logger.info("Location {} disabled", location.getName());
    }

    @Override
    public List<AttributeDefinition> listPushAttributes(String locationUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        try {
            return locationApiClient.listPushCertificateAttributes(
                    location.getEntityInstanceReference().getConnector().mapToDto(),
                    location.getEntityInstanceReference().getEntityInstanceUuid());
        } catch (ConnectorException e) {
            logger.debug("Failed to list push Attributes for Location {}, {}: {}",
                    location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to list push Attributes for the Location " + location.getName());
        }
    }

    @Override
    public List<AttributeDefinition> listCsrAttributes(String locationUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        try {
            return locationApiClient.listGenerateCsrAttributes(
                    location.getEntityInstanceReference().getConnector().mapToDto(),
                    location.getEntityInstanceReference().getEntityInstanceUuid());
        } catch (ConnectorException e) {
            logger.debug("Failed to list CSR Attributes for Location {}, {}: {}",
                    location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to list CSR Attributes for the Location " + location.getName());
        }
    }

    @Override
    public LocationDto removeCertificateFromLocation(String locationUuid, String certificateUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

        CertificateLocationId clId = new CertificateLocationId(location.getId(), certificate.getId());
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
                    " from Location " + location.getName());
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

        return maskSecret(location.mapToDto());
    }

    @Override
    public void removeCertificateFromLocations(String certificateUuid) throws NotFoundException {
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
    public LocationDto pushCertificateToLocation(String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

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
                request.getAttributes(), List.of()
        );

        logger.info("Certificate {} successfully pushed to Location {}", certificateUuid, location.getName());

        return maskSecret(location.mapToDto());
    }

    @Override
    public LocationDto issueCertificateToLocation(String locationUuid, IssueToLocationRequestDto request) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        if (!location.isSupportKeyManagement()) {
            logger.debug("Location {}, {} does not support key management", location.getName(), location.getUuid());
            throw new LocationException("Location " + location.getName() + " does not support key management");
        }
        if (!location.isSupportMultipleEntries() && location.getCertificates().size() >= 1) {
            logger.debug("Location {}, {} does not support multiple entries", location.getName(), location.getUuid());
            throw new LocationException("Location " + location.getName() + " does not support multiple entries");
        }

        // generate new CSR
        GenerateCsrResponseDto generateCsrResponseDto = generateCsrLocation(location, request.getCsrAttributes());
        logger.info("Received certificate signing request from Location {}", location.getName());

        // issue new Certificate
        ClientCertificateDataResponseDto clientCertificateDataResponseDto = issueCertificateForLocation(
                location, generateCsrResponseDto.getCsr(), request.getIssueAttributes(), request.getRaProfileUuid());
        Certificate certificate = certificateService.getCertificateEntity(clientCertificateDataResponseDto.getUuid());

        // push new Certificate to Location
        pushCertificateToLocation(
                location, certificate,
                generateCsrResponseDto.getPushAttributes(), request.getCsrAttributes()
        );

        logger.info("Certificate {} successfully issued and pushed to Location {}", clientCertificateDataResponseDto.getUuid(), location.getName());

        return maskSecret(location.mapToDto());
    }

    @Override
    public LocationDto updateLocationContent(String locationUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuidAndEnabledIsTrue(locationUuid)
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
            throw new LocationException("Failed to get details for Location " + location.getName());
        }

        try {
            updateLocationContent(location, locationDetailResponseDto);
        } catch (CertificateException e) {
            logger.debug("Failed to update Location {}, {} content: {}", location.getName(), location.getUuid(), e.getMessage());
            throw new LocationException("Failed to update content for Location " + location.getName());
        }

        logger.info("Location with name {} and UUID {} synced", location.getName(), location.getUuid());

        return maskSecret(location.mapToDto());
    }

    @Override
    public LocationDto renewCertificateInLocation(String locationUuid, String certificateUuid) throws NotFoundException, LocationException {
        locationRepository.findByUuidAndEnabledIsTrue(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        CertificateLocation certificateLocation = getCertificateLocation(locationUuid, certificateUuid);

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

        // remove the current certificate from location
        removeCertificateFromLocation(certificateLocation.getLocation().getUuid(), certificateLocation.getCertificate().getUuid());

        // generate new CSR
        GenerateCsrResponseDto generateCsrResponseDto = generateCsrLocation(certificateLocation.getLocation(), AttributeDefinitionUtils.getClientAttributes(certificateLocation.getCsrAttributes()));
        logger.info("Received certificate signing request from Location {}", certificateLocation.getLocation().getName());

        // renew existing Certificate
        ClientCertificateDataResponseDto clientCertificateDataResponseDto = renewCertificate(certificateLocation, generateCsrResponseDto.getCsr());
        Certificate certificate = certificateService.getCertificateEntity(clientCertificateDataResponseDto.getUuid());

        // push renewed Certificate to Location
        pushCertificateToLocation(
                certificateLocation.getLocation(), certificate,
                generateCsrResponseDto.getPushAttributes(), AttributeDefinitionUtils.getClientAttributes(certificateLocation.getCsrAttributes())
        );

        logger.info("Certificate {} successfully issued and pushed to Location {}", clientCertificateDataResponseDto.getUuid(), certificateLocation.getLocation().getName());

        Location location = certificateLocation.getLocation();

        return maskSecret(location.mapToDto());
    }

    // PRIVATE METHODS

    private GenerateCsrResponseDto generateCsrLocation(Location location, List<RequestAttributeDto> csrAttributes) throws LocationException {
        GenerateCsrRequestDto generateCsrRequestDto = new GenerateCsrRequestDto();
        generateCsrRequestDto.setLocationAttributes(location.getRequestAttributes());
        generateCsrRequestDto.setCsrAttributes(csrAttributes);

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
            throw new LocationException("Failed to generate CSR for Location " + location.getName());
        }
        return generateCsrResponseDto;
    }

    private ClientCertificateDataResponseDto issueCertificateForLocation(Location location, String csr, List<RequestAttributeDto> issueAttributes, String raProfileUuid) throws LocationException {
        ClientCertificateSignRequestDto clientCertificateSignRequestDto = new ClientCertificateSignRequestDto();
        clientCertificateSignRequestDto.setAttributes(issueAttributes);
        // TODO: support for different types of certificate
        clientCertificateSignRequestDto.setPkcs10(csr);

        ClientCertificateDataResponseDto clientCertificateDataResponseDto;
        try {
            clientCertificateDataResponseDto = clientOperationService.issueCertificate(
                    raProfileUuid, clientCertificateSignRequestDto, true);
        } catch (ConnectorException | AlreadyExistException | java.security.cert.CertificateException e) {
            logger.debug("Failed to issue Certificate for Location " + location.getName() + ", " + location.getUuid() +
                    ": " + e.getMessage());
            throw new LocationException("Failed to issue Certificate for Location " + location.getName());
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
                    certificateLocation.getCertificate().getRaProfile().getUuid(),
                    certificateLocation.getCertificate().getUuid(),
                    clientCertificateRenewRequestDto, true
            );
        } catch (ConnectorException | AlreadyExistException | java.security.cert.CertificateException e) {
            logger.debug("Failed to renew Certificate for Location " + certificateLocation.getLocation().getName() +
                    ", " + certificateLocation.getLocation().getUuid() + ": " + e.getMessage());
            throw new LocationException("Failed to renew Certificate for Location " + certificateLocation.getLocation().getName());
        }
        return clientCertificateDataResponseDto;
    }

    private void pushCertificateToLocation(Location location, Certificate certificate,
                                 List<RequestAttributeDto> pushAttributes, List<RequestAttributeDto> csrAttributes) throws LocationException {
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
                    " to Location " + location.getName());
        }

        //Get the list of Push and CSR Attributes from the connector. This will then be merged with the user request and
        //stored in the database
        List<AttributeDefinition> fullPushAttributes;
        List<AttributeDefinition> fullCsrAttributes;
        try {
            fullPushAttributes = listPushAttributes(location.getUuid());
            fullCsrAttributes = listCsrAttributes(location.getUuid());
        } catch (NotFoundException e) {
            logger.error("Unable to find the location with uuid: {}", location.getUuid());
            throw new LocationException("Failed to get Attributes for Location: " + location.getName());
        }

        List<AttributeDefinition> mergedPushAttributes = AttributeDefinitionUtils.mergeAttributes(fullPushAttributes, pushAttributes);
        List<AttributeDefinition> mergedCsrAttributes = AttributeDefinitionUtils.mergeAttributes(fullCsrAttributes, csrAttributes);

        CertificateLocation certificateLocation = new CertificateLocation();
        certificateLocation.setLocation(location);
        certificateLocation.setCertificate(certificate);
        certificateLocation.setMetadata(pushCertificateResponseDto.getCertificateMetadata());
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

    private List<AttributeDefinition> validateAttributes(EntityInstanceReference entityInstanceReference, List<RequestAttributeDto> requestAttributes, String locationName) throws LocationException {
        List<AttributeDefinition> attributes;
        try {
            attributes = mergeAndValidateAttributes(entityInstanceReference, requestAttributes);
        } catch (ConnectorException e) {
            // TODO: masking of the SECRET Attributes in the debug message?
            logger.debug("Failed to validate Attributes {} for the Location {}: {}", requestAttributes, locationName, e.getMessage());
            throw new LocationException("Failed to create Location: " + locationName);
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
            throw new LocationException("Failed to get details for Location " + locationName);
        }
        return locationDetailResponseDto;
    }

    private CertificateLocation getCertificateLocation(String locationUuid, String certificateUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

        CertificateLocationId clId = new CertificateLocationId(location.getId(), certificate.getId());
        return certificateLocationRepository.findById(clId)
                .orElseThrow(() -> new NotFoundException(CertificateLocation.class, clId));
    }

    private void removeCertificateFromLocation(CertificateLocation certificateLocation) throws ConnectorException {
        RemoveCertificateRequestDto removeCertificateRequestDto = new RemoveCertificateRequestDto();
        removeCertificateRequestDto.setLocationAttributes(certificateLocation.getLocation().getRequestAttributes());
        removeCertificateRequestDto.setCertificateMetadata(certificateLocation.getMetadata());

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
        removeCertificateRequestDto.setCertificateMetadata(certificateLocation.getMetadata());

        locationApiClient.removeCertificateFromLocation(
                entity.getEntityInstanceReference().getConnector().mapToDto(),
                entity.getEntityInstanceReference().getEntityInstanceUuid(),
                removeCertificateRequestDto
        );

        certificateLocationRepository.delete(certificateLocation);
        entity.getCertificates().remove(certificateLocation);

        locationRepository.save(entity);
    }

    private List<AttributeDefinition> mergeAndValidateAttributes(EntityInstanceReference entityInstanceRef, List<RequestAttributeDto> attributes) throws ConnectorException {
        List<AttributeDefinition> definitions = entityInstanceApiClient.listLocationAttributes(
                entityInstanceRef.getConnector().mapToDto(),
                entityInstanceRef.getEntityInstanceUuid());
        List<AttributeDefinition> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);

        entityInstanceApiClient.validateLocationAttributes(
                entityInstanceRef.getConnector().mapToDto(),
                entityInstanceRef.getEntityInstanceUuid(),
                attributes);

        return merged;
    }

    private Location createLocation(AddLocationRequestDto dto, List<AttributeDefinition> attributes,
                                    EntityInstanceReference entityInstanceRef, LocationDetailResponseDto locationDetailResponseDto) throws CertificateException {
        Location entity = new Location();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAttributes(attributes);
        entity.setEntityInstanceReference(entityInstanceRef);
        entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        entity.setEntityInstanceName(entityInstanceRef.getName());

        updateContent(entity,
                locationDetailResponseDto.isMultipleEntries(),
                locationDetailResponseDto.isSupportKeyManagement(),
                locationDetailResponseDto.getMetadata(),
                locationDetailResponseDto.getCertificates());

        return entity;
    }

    private void updateLocation(Location entity, EntityInstanceReference entityInstanceRef, EditLocationRequestDto dto,
                                    List<AttributeDefinition> attributes, LocationDetailResponseDto locationDetailResponseDto) throws CertificateException {
        entity.setDescription(dto.getDescription());
        entity.setAttributes(attributes);
        entity.setEntityInstanceReference(entityInstanceRef);
        if(dto.isEnabled() != null) {
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
                               Map<String, Object> metadata, List<CertificateLocationDto> certificates) throws CertificateException {
        entity.setSupportMultipleEntries(supportMultipleEntries);
        entity.setSupportKeyManagement(supportKeyManagement);
        entity.setMetadata(metadata);

        Set<CertificateLocation> cls = new HashSet<>();
        for (CertificateLocationDto certificateLocationDto : certificates) {
            CertificateLocation cl = new CertificateLocation();
            cl.setWithKey(certificateLocationDto.isWithKey());
            cl.setCertificate(certificateService.createCertificate(certificateLocationDto.getCertificateData(), certificateLocationDto.getCertificateType()));
            cl.setMetadata(certificateLocationDto.getMetadata());
            cl.setLocation(entity);
            cl.setPushAttributes(certificateLocationDto.getPushAttributes());
            cl.setCsrAttributes(certificateLocationDto.getCsrAttributes());
            cls.add(cl);
        }

        Iterator<CertificateLocation> iterator = entity.getCertificates().iterator();
        while(iterator.hasNext()) {
            CertificateLocation cl = iterator.next();
            if (!cls.contains(cl)) {
                certificateLocationRepository.delete(cl);
                iterator.remove();
            }
        }

        entity.getCertificates().addAll(cls);
        locationRepository.save(entity);
    }

    private LocationDto maskSecret(LocationDto locationDto){
        for(ResponseAttributeDto responseAttributeDto: locationDto.getAttributes()){
            if(TO_BE_MASKED.contains(responseAttributeDto.getType())){
                responseAttributeDto.setContent(new BaseAttributeContent<String>(){{setValue("************");}});
            }
        }
        return locationDto;
    }

    private List<AttributeDefinition> listPushAttributes(ConnectorDto connectorDto, String entityInstanceUuid) throws LocationException {

        try {
            return locationApiClient.listPushCertificateAttributes(
                    connectorDto,
                    entityInstanceUuid);
        } catch (ConnectorException e) {
            logger.debug("Failed to list push Attributes for Entity Instance {}: {}",
                    entityInstanceUuid, e.getMessage());
            throw new LocationException("Failed to list push Attributes for the Entity " + entityInstanceUuid);
        }
    }

    private List<AttributeDefinition> listCsrAttributes(ConnectorDto connectorDto, String entityInstanceUuid) throws LocationException {

        try {
            return locationApiClient.listGenerateCsrAttributes(
                    connectorDto,
                    entityInstanceUuid);
        } catch (ConnectorException e) {
            logger.debug("Failed to list CSR Attributes for Entity Instance {}: {}",
                    entityInstanceUuid, e.getMessage());
            throw new LocationException("Failed to list CSR Attributes for the Entity " + entityInstanceUuid);
        }
    }
}
