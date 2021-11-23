package com.czertainly.core.service.impl;

import com.czertainly.api.CAInstanceApiClient;
import com.czertainly.api.core.modal.ClientDto;
import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.raprofile.AddRaProfileRequestDto;
import com.czertainly.api.model.raprofile.EditRaProfileRequestDto;
import com.czertainly.api.model.raprofile.RaProfileDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.CAInstanceReference;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CAInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class RaProfileServiceImpl implements RaProfileService {

    private static final Logger logger = LoggerFactory.getLogger(RaProfileServiceImpl.class);

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CAInstanceReferenceRepository caInstanceReferenceRepository;
    @Autowired
    private CAInstanceApiClient caInstanceApiClient;
    @Autowired
    private CertificateRepository certificateRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public List<RaProfileDto> listRaProfiles() {
        List<RaProfile> raProfiles = raProfileRepository.findAll();
        return raProfiles.stream().map(RaProfile::mapToDtoSimple).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public List<RaProfileDto> listRaProfiles(Boolean isEnabled) {
        List<RaProfile> raProfiles = raProfileRepository.findByEnabled(isEnabled);
        return raProfiles.stream().map(RaProfile::mapToDtoSimple).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CREATE)
    public RaProfileDto addRaProfile(AddRaProfileRequestDto dto) throws AlreadyExistException, ValidationException, NotFoundException, ConnectorException {
        if (StringUtils.isBlank(dto.getName())) {
            throw new ValidationException("RA profile name must not be empty");
        }

        Optional<RaProfile> o = raProfileRepository.findByName(dto.getName());
        if (o.isPresent()) {
            throw new AlreadyExistException(RaProfile.class, dto.getName());
        }

        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(dto.getCaInstanceUuid())
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, dto.getCaInstanceUuid()));

        if (Boolean.FALSE.equals(caInstanceApiClient.validateRAProfileAttributes(
                caInstanceRef.getConnector().mapToDto(),
                caInstanceRef.getCaInstanceId(),
                dto.getAttributes()))) {
            throw new ValidationException("RA profile attributes validation failed.");
        }

        RaProfile raProfile = createRaProfile(dto, caInstanceRef);
        raProfileRepository.save(raProfile);

        return raProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.REQUEST)
    public RaProfileDto getRaProfile(String uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        return raProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.CHANGE)
    public RaProfileDto editRaProfile(String uuid, EditRaProfileRequestDto dto) throws NotFoundException, ConnectorException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        CAInstanceReference caInstanceRef = caInstanceReferenceRepository.findByUuid(dto.getCaInstanceUuid())
                .orElseThrow(() -> new NotFoundException(CAInstanceReference.class, dto.getCaInstanceUuid()));

        if (Boolean.FALSE.equals(caInstanceApiClient.validateRAProfileAttributes(
                caInstanceRef.getConnector().mapToDto(),
                caInstanceRef.getCaInstanceId(),
                dto.getAttributes()))) {
            throw new ValidationException("RA profile attributes validation failed.");
        }

        updateRaProfile(raProfile, caInstanceRef, dto);
        raProfileRepository.save(raProfile);

        return raProfile.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DELETE)
    public void removeRaProfile(String uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        for(Certificate certificate: certificateRepository.findByRaProfile(raProfile)){
            certificate.setRaProfile(null);
            certificateRepository.save(certificate);
        }

        raProfileRepository.delete(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    public List<ClientDto> listClients(String uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        return raProfile.getClients().stream()
                .map(Client::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.ENABLE)
    public void enableRaProfile(String uuid) throws NotFoundException {
        RaProfile entity = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        entity.setEnabled(true);
        raProfileRepository.save(entity);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DISABLE)
    public void disableRaProfile(String uuid) throws NotFoundException {
        RaProfile entity = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

        entity.setEnabled(false);
        raProfileRepository.save(entity);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DELETE)
    public void bulkRemoveRaProfile(List<String> uuids) {
        for (String uuid : uuids) {
            try {
                RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

                for (Certificate certificate : certificateRepository.findByRaProfile(raProfile)) {
                    certificate.setRaProfile(null);
                    certificateRepository.save(certificate);
                }

                raProfileRepository.delete(raProfile);
            }catch (NotFoundException e){
                logger.warn("Unable to find RA Profile with uuid {}. It may have already been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DISABLE)
    public void bulkDisableRaProfile(List<String> uuids) {
        for (String uuid : uuids) {
            try {
                RaProfile entity = raProfileRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

                entity.setEnabled(false);
                raProfileRepository.save(entity);
            }catch (NotFoundException e){
                logger.warn("Unable to disable RA Profile with uuid {}. It may have been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.ENABLE)
    public void bulkEnableRaProfile(List<String> uuids) {
        for (String uuid : uuids) {
            try {
                RaProfile entity = raProfileRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));

                entity.setEnabled(true);
                raProfileRepository.save(entity);
            } catch (NotFoundException e) {
                logger.warn("Unable to enable RA Profile with uuid {}. It may have been deleted", uuids);
            }
        }
    }


    private RaProfile createRaProfile(AddRaProfileRequestDto dto, CAInstanceReference caInstanceRef) {
        RaProfile entity = new RaProfile();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(dto.getAttributes()));
        entity.setCaInstanceReference(caInstanceRef);
        entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        entity.setCaInstanceName(caInstanceRef.getName());
        return entity;
    }

    private RaProfile updateRaProfile(RaProfile entity, CAInstanceReference caInstanceRef, EditRaProfileRequestDto dto) {
        entity.setDescription(dto.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(dto.getAttributes()));
        entity.setCaInstanceReference(caInstanceRef);
        entity.setCaInstanceName(caInstanceRef.getName());
        return entity;
    }
}
