package com.czertainly.core.service.impl;

import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.clients.LocationApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.client.location.IssueToLocationRequestDto;
import com.czertainly.api.model.client.location.PushToLocationRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.BaseAttributeDefinitionTypes;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.common.ResponseAttributeDto;
import com.czertainly.api.model.connector.entity.*;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateEventStatus;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationServiceImpl implements LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationServiceImpl.class);

    private static final List<BaseAttributeDefinitionTypes> TO_BE_MASKED = List.of(BaseAttributeDefinitionTypes.SECRET);

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
    public List<LocationDto> listLocations(Boolean isEnabled) {
        List<Location> locations = locationRepository.findByEnabled(isEnabled);
        return locations.stream().map(Location::mapToDtoSimple).collect(Collectors.toList());
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
        Location location = locationRepository.findByUuid(locationUuid)
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
        Location location = locationRepository.findByUuid(locationUuid)
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
        Location location = locationRepository.findByUuid(locationUuid)
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
    public List<Location> getCertificateLocations(String certificateUuid) throws NotFoundException {
        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);
        return certificate.getLocations().stream().map(CertificateLocation::getLocation).collect(Collectors.toList());
    }

    @Override
    public void removeCertificateFromLocations(String certificateUuid) throws NotFoundException {
        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

        Set<CertificateLocation> certificateLocations = certificate.getLocations();
        for (CertificateLocation cl : certificateLocations) {
            try {
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
        Location location = locationRepository.findByUuid(locationUuid)
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

        PushCertificateRequestDto pushCertificateRequestDto = new PushCertificateRequestDto();
        pushCertificateRequestDto.setCertificate(certificate.getCertificateContent().getContent());
        pushCertificateRequestDto.setCertificateType(CertificateType.X509);
        pushCertificateRequestDto.setLocationAttributes(location.getRequestAttributes());
        pushCertificateRequestDto.setPushAttributes(request.getAttributes());

        pushCertificate(
                pushCertificateRequestDto, location, certificate,
                request.getAttributes(), List.of()
        );

        logger.info("Certificate {} successfully pushed to Location {}", certificateUuid, location.getName());

        return maskSecret(location.mapToDto());
    }

    private void pushCertificate(PushCertificateRequestDto pushCertificateRequestDto, Location location, Certificate certificate,
                                                       List<RequestAttributeDto> pushAttributes, List<RequestAttributeDto> csrAttributes) throws LocationException {
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

        CertificateLocation certificateLocation = new CertificateLocation();
        certificateLocation.setLocation(location);
        certificateLocation.setCertificate(certificate);
        certificateLocation.setMetadata(pushCertificateResponseDto.getCertificateMetadata());
        certificateLocation.setPushAttributes(pushAttributes);
        certificateLocation.setCsrAttributes(csrAttributes);

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

    @Override
    public LocationDto issueCertificateToLocation(String locationUuid, IssueToLocationRequestDto request) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        if (!location.isSupportKeyManagement()) {
            logger.debug("Location {}, {} does not support key management", location.getName(), location.getUuid());
            throw new LocationException("Location " + location.getName() + " does not support key management");
        }
        if (!location.isSupportMultipleEntries() && location.getCertificates().size() >= 1) {
            logger.debug("Location {}, {} does not support multiple entries", location.getName(), location.getUuid());
            throw new LocationException("Location " + location.getName() + " does not support multiple entries");
        }

        GenerateCsrRequestDto generateCsrRequestDto = new GenerateCsrRequestDto();
        generateCsrRequestDto.setLocationAttributes(location.getRequestAttributes());
        generateCsrRequestDto.setCsrAttributes(request.getCsrAttributes());

        GenerateCsrResponseDto generateCsrResponseDto;
        try {
            generateCsrResponseDto = locationApiClient.generateCsrLocation(
                    location.getEntityInstanceReference().getConnector().mapToDto(),
                    location.getEntityInstanceReference().getEntityInstanceUuid(),
                    generateCsrRequestDto
            );
        } catch (ConnectorException e) {
            logger.debug("Failed to generate CSR for the Location " + location.getName() + ", " + location.getUuid() +
                    ", with Attributes " + request.getCsrAttributes() + ": " + e.getMessage());
            throw new LocationException("Failed to generate CSR for Location " + location.getName());
        }

        logger.info("Received certificate signing request from Location {}", location.getName());

        ClientCertificateSignRequestDto clientCertificateSignRequestDto = new ClientCertificateSignRequestDto();
        clientCertificateSignRequestDto.setAttributes(request.getIssueAttributes());
        // TODO: support for different types of certificate
        clientCertificateSignRequestDto.setPkcs10(generateCsrResponseDto.getCsr());

        ClientCertificateDataResponseDto clientCertificateDataResponseDto;
        try {
            clientCertificateDataResponseDto = clientOperationService.issueCertificate(
                    request.getRaProfileUuid(), clientCertificateSignRequestDto, true);
        } catch (ConnectorException | AlreadyExistException | java.security.cert.CertificateException e) {
            logger.debug("Failed to issue Certificate for Location " + location.getName() + ", " + location.getUuid() +
                    ": " + e.getMessage());
            throw new LocationException("Failed to issue Certificate for Location " + location.getName());
        }

        Certificate certificate = certificateService.getCertificateEntity(clientCertificateDataResponseDto.getUuid());

        PushCertificateRequestDto pushCertificateRequestDto = new PushCertificateRequestDto();
        pushCertificateRequestDto.setCertificate(clientCertificateDataResponseDto.getCertificateData());
        // TODO: support for different types of certificate
        pushCertificateRequestDto.setCertificateType(CertificateType.X509);
        pushCertificateRequestDto.setLocationAttributes(location.getRequestAttributes());
        pushCertificateRequestDto.setPushAttributes(generateCsrResponseDto.getPushAttributes());

        pushCertificate(
                pushCertificateRequestDto, location, certificate,
                generateCsrResponseDto.getPushAttributes(), request.getCsrAttributes()
        );

        logger.info("Certificate {} successfully issued and pushed to Location {}", clientCertificateDataResponseDto.getUuid(), location.getName());

        return maskSecret(location.mapToDto());
    }

    @Override
    public LocationDto updateLocationContent(String locationUuid) throws NotFoundException, LocationException {
        Location location = locationRepository.findByUuid(locationUuid)
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

        //Location updatedLocation = updateLocationContent(location, locationDetailResponseDto);

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

        try {
            removeCertificateFromLocation(certificateLocation);
        } catch (ConnectorException e) {
            logger.debug("Failed to remove Certificate {} from Location {}, {}: {}",
                    certificateLocation.getCertificate().getUuid(), certificateLocation.getLocation().getName(),
                    certificateLocation.getLocation().getUuid(), e.getMessage());
            throw new LocationException("Failed to remove Certificate " + certificateLocation.getCertificate().getUuid() +
                    " from Location " + certificateLocation.getLocation().getName());
        }

        GenerateCsrRequestDto generateCsrRequestDto = new GenerateCsrRequestDto();
        generateCsrRequestDto.setLocationAttributes(certificateLocation.getLocation().getRequestAttributes());
        generateCsrRequestDto.setCsrAttributes(certificateLocation.getCsrAttributes());

        GenerateCsrResponseDto generateCsrResponseDto;
        try {
            generateCsrResponseDto = locationApiClient.generateCsrLocation(
                    certificateLocation.getLocation().getEntityInstanceReference().getConnector().mapToDto(),
                    certificateLocation.getLocation().getEntityInstanceReference().getEntityInstanceUuid(),
                    generateCsrRequestDto
            );
        } catch (ConnectorException e) {
            logger.debug("Failed to generate CSR for the Location " + certificateLocation.getLocation().getName() +
                    ", " + certificateLocation.getLocation().getUuid() +
                    ", with Attributes " + certificateLocation.getCsrAttributes() + ": " + e.getMessage());
            throw new LocationException("Failed to generate CSR for Location " + certificateLocation.getLocation().getName());
        }

        logger.info("Received certificate signing request from Location {}", certificateLocation.getLocation().getName());

        ClientCertificateRenewRequestDto clientCertificateRenewRequestDto = new ClientCertificateRenewRequestDto();
        clientCertificateRenewRequestDto.setPkcs10(generateCsrResponseDto.getCsr());
        clientCertificateRenewRequestDto.setReplaceInLocations(false);

        ClientCertificateDataResponseDto clientCertificateDataResponseDto;
        try {
            clientCertificateDataResponseDto = clientOperationService.renewCertificate(
                    certificateLocation.getCertificate().getRaProfile().getUuid(),
                    certificateLocation.getCertificate().getUuid(),
                    clientCertificateRenewRequestDto
            );
        } catch (ConnectorException | AlreadyExistException | java.security.cert.CertificateException e) {
            logger.debug("Failed to renew Certificate for Location " + certificateLocation.getLocation().getName() +
                    ", " + certificateLocation.getLocation().getUuid() + ": " + e.getMessage());
            throw new LocationException("Failed to renew Certificate for Location " + certificateLocation.getLocation().getName());
        }

        Certificate certificate = certificateService.getCertificateEntity(clientCertificateDataResponseDto.getUuid());

        PushCertificateRequestDto pushCertificateRequestDto = new PushCertificateRequestDto();
        pushCertificateRequestDto.setCertificate(clientCertificateDataResponseDto.getCertificateData());
        // TODO: support for different types of certificate
        pushCertificateRequestDto.setCertificateType(CertificateType.X509);
        pushCertificateRequestDto.setLocationAttributes(certificateLocation.getLocation().getRequestAttributes());
        pushCertificateRequestDto.setPushAttributes(generateCsrResponseDto.getPushAttributes());

        PushCertificateResponseDto pushCertificateResponseDto;
        try {
            pushCertificateResponseDto = locationApiClient.pushCertificateToLocation(
                    certificateLocation.getLocation().getEntityInstanceReference().getConnector().mapToDto(),
                    certificateLocation.getLocation().getEntityInstanceReference().getEntityInstanceUuid(),
                    pushCertificateRequestDto
            );
        } catch (ConnectorException e) {
            // record event in the certificate history
            String message = "Failed to push to Location " + certificateLocation.getLocation().getName();
            HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put("locationUuid", certificateLocation.getLocation().getUuid());
            additionalInformation.put("cause", e.getMessage());
            certificateEventHistoryService.addEventHistory(
                    CertificateEvent.UPDATE_LOCATION,
                    CertificateEventStatus.FAILED,
                    message,
                    additionalInformation,
                    certificate
            );
            logger.debug("Failed to push Certificate {} to Location {}, {}: {}",
                    certificate.getUuid(), certificateLocation.getLocation().getName(), certificateLocation.getLocation().getUuid(), e.getMessage());
            throw new LocationException("Failed to push Certificate " + certificate.getUuid() +
                    " to Location " + certificateLocation.getLocation().getName());
        }

        CertificateLocation newCertificateLocation = new CertificateLocation();
        newCertificateLocation.setLocation(certificateLocation.getLocation());
        newCertificateLocation.setCertificate(certificate);
        newCertificateLocation.setMetadata(pushCertificateResponseDto.getCertificateMetadata());
        newCertificateLocation.setPushAttributes(generateCsrResponseDto.getPushAttributes());
        newCertificateLocation.setCsrAttributes(certificateLocation.getCsrAttributes());

        // TODO: response with the indication if the key is available for pushed certificate

        certificateLocationRepository.save(newCertificateLocation);

        // save record into the certificate history
        String message = "Pushed to Location " + certificateLocation.getLocation().getName();
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("locationUuid", certificateLocation.getLocation().getUuid());
        certificateEventHistoryService.addEventHistory(
                CertificateEvent.UPDATE_LOCATION,
                CertificateEventStatus.SUCCESS,
                message,
                additionalInformation,
                certificate
        );

        logger.info("Certificate {} successfully issued and pushed to Location {}", clientCertificateDataResponseDto.getUuid(), newCertificateLocation.getLocation().getName());

        return null;
    }

    // PRIVATE METHODS

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

        if (Boolean.FALSE.equals(entityInstanceApiClient.validateLocationAttributes(
                entityInstanceRef.getConnector().mapToDto(),
                entityInstanceRef.getEntityInstanceUuid(),
                attributes))) {

            throw new ValidationException("Location attributes validation failed.");
        }

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
                responseAttributeDto.setValue("************");
            }
        }
        return locationDto;
    }
}
