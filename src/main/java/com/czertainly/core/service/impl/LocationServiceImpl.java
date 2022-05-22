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
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.CertificateLocationRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.LocationRepository;
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

    private LocationRepository locationRepository;
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    private CertificateLocationRepository certificateLocationRepository;
    private EntityInstanceApiClient entityInstanceApiClient;
    private LocationApiClient locationApiClient;
    private CertificateService certificateService;
    private ClientOperationService clientOperationService;

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
    public LocationDto addLocation(AddLocationRequestDto dto) throws AlreadyExistException, ValidationException, ConnectorException, CertificateException {
        if (StringUtils.isBlank(dto.getName())) {
            throw new ValidationException("Location name must not be empty");
        }

        Optional<Location> o = locationRepository.findByName(dto.getName());
        if (o.isPresent()) {
            throw new AlreadyExistException(Location.class, dto.getName());
        }

        EntityInstanceReference entityInstanceRef = entityInstanceReferenceRepository.findByUuid(dto.getEntityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, dto.getEntityInstanceUuid()));

        List<AttributeDefinition> attributes = mergeAndValidateAttributes(entityInstanceRef, dto.getAttributes());

        LocationDetailRequestDto locationDetailRequestDto = new LocationDetailRequestDto();
        locationDetailRequestDto.setLocationAttributes(dto.getAttributes());

        LocationDetailResponseDto locationDetailResponseDto = locationApiClient.getLocationDetail(
                entityInstanceRef.getConnector().mapToDto(), entityInstanceRef.getEntityInstanceUuid(), locationDetailRequestDto);

        Location location = createLocation(dto, attributes, entityInstanceRef, locationDetailResponseDto);

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
    public LocationDto editLocation(String locationUuid, EditLocationRequestDto dto) throws ConnectorException, CertificateException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        EntityInstanceReference entityInstanceRef;
        entityInstanceRef = entityInstanceReferenceRepository.findByUuid(dto.getEntityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, dto.getEntityInstanceUuid()));

        List<AttributeDefinition> attributes = mergeAndValidateAttributes(entityInstanceRef, dto.getAttributes());

        LocationDetailRequestDto locationDetailRequestDto = new LocationDetailRequestDto();
        locationDetailRequestDto.setLocationAttributes(dto.getAttributes());

        LocationDetailResponseDto locationDetailResponseDto = locationApiClient.getLocationDetail(
                entityInstanceRef.getConnector().mapToDto(), entityInstanceRef.getUuid(), locationDetailRequestDto);

        //Location updatedLocation = updateLocation(location, entityInstanceRef, dto, attributes, locationDetailResponseDto);

        updateLocation(location, entityInstanceRef, dto, attributes, locationDetailResponseDto);

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
    public List<AttributeDefinition> listPushAttributes(String locationUuid) throws ConnectorException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        return locationApiClient.listPushCertificateAttributes(
                location.getEntityInstanceReference().getConnector().mapToDto(),
                location.getEntityInstanceReference().getEntityInstanceUuid());
    }

    @Override
    public List<AttributeDefinition> listCsrAttributes(String locationUuid) throws ConnectorException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        return locationApiClient.listGenerateCsrAttributes(
                location.getEntityInstanceReference().getConnector().mapToDto(),
                location.getEntityInstanceReference().getEntityInstanceUuid());
    }

    @Override
    public LocationDto removeCertificateFromLocation(String locationUuid, String certificateUuid) throws ConnectorException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

        CertificateLocationId clId = new CertificateLocationId(location.getId(), certificate.getId());
        CertificateLocation certificateInLocation = certificateLocationRepository.findById(clId)
                .orElseThrow(() -> new NotFoundException(CertificateLocation.class, clId));

        removeCertificateFromLocation(certificateInLocation);

        return maskSecret(location.mapToDto());
    }

    @Override
    public List<Location> getCertificateLocations(String certificateUuid) throws NotFoundException {
        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);
        return certificate.getLocations().stream().map(CertificateLocation::getLocation).collect(Collectors.toList());
    }

    @Override
    public void removeCertificateFromLocations(String certificateUuid) throws ConnectorException {
        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

        Set<CertificateLocation> certificateLocations = certificate.getLocations();
        for (CertificateLocation cl : certificateLocations) {
            removeCertificateFromLocation(cl);
            certificateLocations.remove(cl);
        }
    }

    @Override
    public LocationDto pushCertificateToLocation(String locationUuid, String certificateUuid, PushToLocationRequestDto request) throws ConnectorException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        Certificate certificate = certificateService.getCertificateEntity(certificateUuid);

        PushCertificateRequestDto pushCertificateRequestDto = new PushCertificateRequestDto();
        pushCertificateRequestDto.setCertificate(certificate.getCertificateContent().getContent());
        pushCertificateRequestDto.setCertificateType(CertificateType.X509);
        pushCertificateRequestDto.setLocationAttributes(location.getRequestAttributes());
        pushCertificateRequestDto.setPushAttributes(request.getAttributes());

        PushCertificateResponseDto pushCertificateResponseDto = locationApiClient.pushCertificateToLocation(
                location.getEntityInstanceReference().getConnector().mapToDto(),
                location.getEntityInstanceReference().getEntityInstanceUuid(),
                pushCertificateRequestDto
        );

        CertificateLocation certificateLocation = new CertificateLocation();
        certificateLocation.setLocation(location);
        certificateLocation.setCertificate(certificate);
        certificateLocation.setMetadata(pushCertificateResponseDto.getCertificateMetadata());

        // TODO: response with the indication if the key is available for pushed certificate

        certificateLocationRepository.save(certificateLocation);

        logger.info("Certificate {} successfully pushed to Location {}", certificateUuid, location.getName());

        return maskSecret(location.mapToDto());
    }

    @Override
    public LocationDto issueCertificateToLocation(String locationUuid, IssueToLocationRequestDto request) throws ConnectorException, java.security.cert.CertificateException, AlreadyExistException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        GenerateCsrRequestDto generateCsrRequestDto = new GenerateCsrRequestDto();
        generateCsrRequestDto.setLocationAttributes(location.getRequestAttributes());
        generateCsrRequestDto.setCsrAttributes(request.getCsrAttributes());

        GenerateCsrResponseDto generateCsrResponseDto = locationApiClient.generateCsrLocation(
                location.getEntityInstanceReference().getConnector().mapToDto(),
                location.getEntityInstanceReference().getEntityInstanceUuid(),
                generateCsrRequestDto
        );

        logger.info("Received certificate signing request from Location {}", location.getName());

        ClientCertificateSignRequestDto clientCertificateSignRequestDto = new ClientCertificateSignRequestDto();
        clientCertificateSignRequestDto.setAttributes(request.getIssueAttributes());
        // TODO: support for different types of certificate
        clientCertificateSignRequestDto.setPkcs10(generateCsrResponseDto.getCsr());

        ClientCertificateDataResponseDto clientCertificateDataResponseDto = clientOperationService.issueCertificate(
                request.getRaProfileUuid(), clientCertificateSignRequestDto, true);

        PushCertificateRequestDto pushCertificateRequestDto = new PushCertificateRequestDto();
        pushCertificateRequestDto.setCertificate(clientCertificateDataResponseDto.getCertificateData());
        // TODO: support for different types of certificate
        pushCertificateRequestDto.setCertificateType(CertificateType.X509);
        pushCertificateRequestDto.setLocationAttributes(location.getRequestAttributes());
        pushCertificateRequestDto.setPushAttributes(generateCsrResponseDto.getPushAttributes());

        PushCertificateResponseDto pushCertificateResponseDto = locationApiClient.pushCertificateToLocation(
                location.getEntityInstanceReference().getConnector().mapToDto(),
                location.getEntityInstanceReference().getEntityInstanceUuid(),
                pushCertificateRequestDto
        );

        Certificate certificate = certificateService.getCertificateEntity(clientCertificateDataResponseDto.getUuid());

        CertificateLocation certificateLocation = new CertificateLocation();
        certificateLocation.setLocation(location);
        certificateLocation.setCertificate(certificate);
        certificateLocation.setMetadata(pushCertificateResponseDto.getCertificateMetadata());

        // TODO: response with the indication if the key is available for pushed certificate

        certificateLocationRepository.save(certificateLocation);

        logger.info("Certificate {} successfully issued and pushed to Location {}", clientCertificateDataResponseDto.getUuid(), location.getName());

        return maskSecret(location.mapToDto());
    }

    @Override
    public LocationDto updateLocationContent(String locationUuid) throws ConnectorException, CertificateException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        EntityInstanceReference entityInstanceRef = location.getEntityInstanceReference();

        LocationDetailRequestDto locationDetailRequestDto = new LocationDetailRequestDto();
        locationDetailRequestDto.setLocationAttributes(location.getRequestAttributes());

        LocationDetailResponseDto locationDetailResponseDto = locationApiClient.getLocationDetail(
                entityInstanceRef.getConnector().mapToDto(), entityInstanceRef.getEntityInstanceUuid(), locationDetailRequestDto);

        //Location updatedLocation = updateLocationContent(location, locationDetailResponseDto);

        updateLocationContent(location, locationDetailResponseDto);

        logger.info("Location with name {} and UUID {} synced", location.getName(), location.getUuid());

        return maskSecret(location.mapToDto());
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
        logger.info("Certificate {} deleted from Location {}", certificateLocation.getCertificate().getUuid(), certificateLocation.getLocation().getName());
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
            cls.add(cl);
        }

        for (CertificateLocation cl : entity.getCertificates()) {
            if (!cls.contains(cl)) {
                certificateLocationRepository.delete(cl);
                entity.getCertificates().remove(cl);
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
