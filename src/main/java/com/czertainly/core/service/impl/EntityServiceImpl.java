package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.entity.EntityDto;
import com.czertainly.api.model.core.certificate.entity.EntityRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEntity;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.EntityRepository;
import com.czertainly.core.service.EntityService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Secured({"ROLE_ADMINISTRATOR", "ROLE_SUPERADMINISTRATOR"})
public class EntityServiceImpl implements EntityService {

    private static final Logger logger = LoggerFactory.getLogger(EntityServiceImpl.class);

    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.REQUEST)
    public List<EntityDto> listEntity() {
        return entityRepository.findAll().stream().map(CertificateEntity::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.REQUEST)
    public EntityDto getCertificateEntity(String uuid) throws NotFoundException {
        return getEntity(uuid).mapToDto();
    }

    public CertificateEntity getEntity(String uuid) throws NotFoundException {
        return entityRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.CREATE)
    public EntityDto createEntity(EntityRequestDto request) throws ValidationException, AlreadyExistException {

        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Name must not be empty");
        }

        if (entityRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(CertificateEntity.class, request.getName());
        }

        CertificateEntity certificateEntity = new CertificateEntity();
        certificateEntity.setName(request.getName());
        certificateEntity.setDescription(request.getDescription());
        certificateEntity.setEntityType(request.getEntityType());
        entityRepository.save(certificateEntity);

        return certificateEntity.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.DELETE)
    public void removeEntity(String uuid) throws NotFoundException {
        CertificateEntity certificateEntity = entityRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));
        for(Certificate certificate: certificateRepository.findByEntity(certificateEntity)){
            certificate.setEntity(certificateEntity);
            certificateRepository.save(certificate);
        }
        entityRepository.delete(certificateEntity);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.DELETE)
    public void bulkRemoveEntity(List<String> entityUuids) {
        for(String uuid: entityUuids){
            try{
                CertificateEntity certificateEntity = entityRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));
                for(Certificate certificate: certificateRepository.findByEntity(certificateEntity)){
                    certificate.setEntity(certificateEntity);
                    certificateRepository.save(certificate);
                }
                entityRepository.delete(certificateEntity);
            }catch(NotFoundException e){
                logger.warn("Unable to find the entity with uuid {}. It may have been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.CHANGE)
    public EntityDto updateEntity(String uuid, EntityRequestDto request) throws NotFoundException {
        CertificateEntity certificateEntity = entityRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));

        certificateEntity.setDescription(request.getDescription());
        certificateEntity.setEntityType(request.getEntityType());
        entityRepository.save(certificateEntity);
        return certificateEntity.mapToDto();
    }
}
