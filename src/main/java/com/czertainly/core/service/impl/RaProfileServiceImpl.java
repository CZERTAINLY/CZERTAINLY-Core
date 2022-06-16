package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AuthorityInstanceApiClient;
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
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.service.RaProfileService;
import com.czertainly.core.service.v2.ExtendedAttributeService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
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
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private AuthorityInstanceApiClient authorityInstanceApiClient;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private AcmeProfileRepository acmeProfileRepository;
    @Autowired
    private ExtendedAttributeService extendedAttributeService;

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

        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(dto.getAuthorityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, dto.getAuthorityInstanceUuid()));

        List<AttributeDefinition> attributes = mergeAndValidateAttributes(authorityInstanceRef, dto.getAttributes());
        RaProfile raProfile = createRaProfile(dto, attributes, authorityInstanceRef);
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

        AuthorityInstanceReference authorityInstanceRef = authorityInstanceReferenceRepository.findByUuid(dto.getAuthorityInstanceUuid())
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, dto.getAuthorityInstanceUuid()));

        List<AttributeDefinition> attributes = mergeAndValidateAttributes(authorityInstanceRef, dto.getAttributes());

        updateRaProfile(raProfile, authorityInstanceRef, dto, attributes);
        raProfileRepository.save(raProfile);

        return raProfile.mapToDto();
    }

    private List<AttributeDefinition> mergeAndValidateAttributes(AuthorityInstanceReference authorityInstanceRef, List<RequestAttributeDto> attributes) throws ConnectorException {
        List<AttributeDefinition> definitions = authorityInstanceApiClient.listRAProfileAttributes(
                authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid());
        List<AttributeDefinition> merged = AttributeDefinitionUtils.mergeAttributes(definitions, attributes);

        if (Boolean.FALSE.equals(authorityInstanceApiClient.validateRAProfileAttributes(
                authorityInstanceRef.getConnector().mapToDto(),
                authorityInstanceRef.getAuthorityInstanceUuid(),
                attributes))) {

            throw new ValidationException("RA profile attributes validation failed.");
        }

        return merged;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.RA_PROFILE, operation = OperationType.DELETE)
    public void removeRaProfile(String uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
        List<AcmeProfile> acmeProfiles = acmeProfileRepository.findByRaProfile(raProfile);
        for(AcmeProfile acmeProfile: acmeProfiles){
            acmeProfile.setRaProfile(null);
            acmeProfileRepository.save(acmeProfile);
        }
        for(Certificate certificate: certificateRepository.findByRaProfile(raProfile)){
            certificate.setRaProfile(null);
            certificateRepository.save(certificate);
        }

        raProfileRepository.delete(raProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.CLIENT, operation = OperationType.REQUEST)
    public List<SimplifiedClientDto> listClients(String uuid) throws NotFoundException {
        RaProfile raProfile = raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
        List<SimplifiedClientDto> clients = new ArrayList<>();
        for(Client client : raProfile.getClients()){
            SimplifiedClientDto dto = new SimplifiedClientDto();
            dto.setUuid(client.getUuid());
            dto.setName(client.getName());
            dto.setEnabled(client.getEnabled());
            clients.add(dto);
        }
        return clients;
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
                List<AcmeProfile> acmeProfiles = acmeProfileRepository.findByRaProfile(raProfile);
                for(AcmeProfile acmeProfile: acmeProfiles){
                    acmeProfile.setRaProfile(null);
                    acmeProfileRepository.save(acmeProfile);
                }
                for (Certificate certificate : certificateRepository.findByRaProfile(raProfile)) {
                    certificate.setRaProfile(null);
                    certificateRepository.save(certificate);
                }

                raProfileRepository.delete(raProfile);
            } catch (NotFoundException e){
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

    @Override
    public RaProfileAcmeDetailResponseDto getAcmeForRaProfile(String uuid) throws NotFoundException {
        return getRaProfileEntity(uuid).mapToAcmeDto();
    }

    @Override
    public RaProfileAcmeDetailResponseDto activateAcmeForRaProfile(String uuid, ActivateAcmeForRaProfileRequestDto request) throws ConnectorException, ValidationException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        AcmeProfile acmeProfile = acmeProfileRepository.findByUuid(request.getAcmeProfileUuid())
                .orElseThrow(() -> new NotFoundException(AcmeProfile.class, request.getAcmeProfileUuid()));
        raProfile.setAcmeProfile(acmeProfile);
        raProfile.setIssueCertificateAttributes(AttributeDefinitionUtils.serialize(
                extendedAttributeService.mergeAndValidateIssueAttributes
                        (raProfile, request.getIssueCertificateAttributes()
                        )));
        raProfile.setRevokeCertificateAttributes(AttributeDefinitionUtils.serialize(
                extendedAttributeService.mergeAndValidateRevokeAttributes(
                        raProfile, request.getIssueCertificateAttributes()
                        )));
        raProfileRepository.save(raProfile);
        return raProfile.mapToAcmeDto();
    }

    @Override
    public void deactivateAcmeForRaProfile(String uuid) throws NotFoundException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        raProfile.setAcmeProfile(null);
        raProfile.setIssueCertificateAttributes(null);
        raProfile.setRevokeCertificateAttributes(null);
        raProfileRepository.save(raProfile);
    }

    @Override
    public List<AttributeDefinition> listRevokeCertificateAttributes(String uuid) throws NotFoundException, ConnectorException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        return extendedAttributeService.listRevokeCertificateAttributes(raProfile);
    }

    @Override
    public List<AttributeDefinition> listIssueCertificateAttributes(String uuid) throws NotFoundException, ConnectorException {
        RaProfile raProfile = getRaProfileEntity(uuid);
        return extendedAttributeService.listIssueCertificateAttributes(raProfile);
    }

    private RaProfile getRaProfileEntity(String uuid) throws NotFoundException {
        return raProfileRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(RaProfile.class, uuid));
    }


    private RaProfile createRaProfile(AddRaProfileRequestDto dto, List<AttributeDefinition> attributes, AuthorityInstanceReference authorityInstanceRef) {
        RaProfile entity = new RaProfile();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        entity.setAuthorityInstanceReference(authorityInstanceRef);
        entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        entity.setAuthorityInstanceName(authorityInstanceRef.getName());
        return entity;
    }

    private RaProfile updateRaProfile(RaProfile entity, AuthorityInstanceReference authorityInstanceRef, EditRaProfileRequestDto dto, List<AttributeDefinition> attributes) {
        entity.setDescription(dto.getDescription());
        entity.setAttributes(AttributeDefinitionUtils.serialize(attributes));
        entity.setAuthorityInstanceReference(authorityInstanceRef);
        if(dto.isEnabled() != null) {
            entity.setEnabled(dto.isEnabled() != null && dto.isEnabled());
        }
        entity.setAuthorityInstanceName(authorityInstanceRef.getName());
        return entity;
    }
}
