package com.czertainly.core.service.impl;

import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.clients.LocationApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.location.AddLocationRequestDto;
import com.czertainly.api.model.client.location.EditLocationRequestDto;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.LocationRepository;
import com.czertainly.core.service.LocationService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LocationServiceImpl implements LocationService {

    private static final Logger logger = LoggerFactory.getLogger(LocationServiceImpl.class);

    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    @Autowired
    private EntityInstanceApiClient entityInstanceApiClient;
    @Autowired
    private LocationApiClient locationApiClient;

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public List<LocationDto> listLocation() {
        List<Location> locations = locationRepository.findAll();
        return locations.stream().map(Location::mapToDto).collect(Collectors.toList());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public List<LocationDto> listLocations(Boolean isEnabled) {
        List<Location> locations = locationRepository.findByEnabled(isEnabled);
        return locations.stream().map(Location::mapToDto).collect(Collectors.toList());
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CREATE)
    public LocationDto addLocation(AddLocationRequestDto dto) throws AlreadyExistException, ValidationException, ConnectorException {
        if (StringUtils.isBlank(dto.getName())) {
            throw new ValidationException("Location name must not be empty");
        }

        Optional<Location> o = locationRepository.findByName(dto.getName());
        if (o.isPresent()) {
            throw new AlreadyExistException(RaProfile.class, dto.getName());
        }

        EntityInstanceReference entityInstanceRef = entityInstanceReferenceRepository.findByUuid(dto.getEntityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, dto.getEntityInstanceUuid()));

        List<AttributeDefinition> attributes = mergeAndValidateAttributes(entityInstanceRef, dto.getAttributes());
        Location location = createLocation(dto, attributes, entityInstanceRef);
        locationRepository.save(location);

        logger.info("Location with name {} and UUID {} created", location.getName(), location.getUuid());

        return location.mapToDto();
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public LocationDto getLocation(String locationUuid) throws NotFoundException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        return location.mapToDto();
    }

    @Override
    //@AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CHANGE)
    public LocationDto editLocation(String locationUuid, EditLocationRequestDto dto) throws ConnectorException {
        Location location = locationRepository.findByUuid(locationUuid)
                .orElseThrow(() -> new NotFoundException(Location.class, locationUuid));

        EntityInstanceReference entityInstanceRef = entityInstanceReferenceRepository.findByUuid(dto.getEntityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(EntityInstanceReference.class, dto.getEntityInstanceUuid()));

        List<AttributeDefinition> attributes = mergeAndValidateAttributes(entityInstanceRef, dto.getAttributes());

        updateLocation(location, entityInstanceRef, dto, attributes);
        locationRepository.save(location);

        logger.info("Location with UUID {} updated", location.getName(), location.getUuid());

        return location.mapToDto();
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
            location.getCertificates().stream().forEach(c -> errors.add(ValidationError.create(c.getCertificate().getUuid())));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Could not delete Location", errors);
        }

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

    private Location createLocation(AddLocationRequestDto dto, List<AttributeDefinition> attributes, EntityInstanceReference entityInstanceRef) {
        Location entity = new Location();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        entity.setEntityInstanceReference(entityInstanceRef);
        entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        entity.setEntityInstanceName(entityInstanceRef.getName());
        return entity;
    }

    private Location updateLocation(Location entity, EntityInstanceReference entityInstanceRef, EditLocationRequestDto dto, List<AttributeDefinition> attributes) {
        entity.setDescription(dto.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        entity.setEntityInstanceReference(entityInstanceRef);
        if(dto.isEnabled() != null) {
            entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        }
        entity.setEntityInstanceName(entityInstanceRef.getName());
        return entity;
    }

}
