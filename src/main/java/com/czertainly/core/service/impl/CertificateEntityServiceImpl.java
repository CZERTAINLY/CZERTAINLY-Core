package com.czertainly.core.service.impl;

import com.czertainly.api.core.modal.ObjectType;
import com.czertainly.api.core.modal.OperationType;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.certificate.entity.CertificateEntityDto;
import com.czertainly.api.model.certificate.entity.CertificateEntityRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEntity;
import com.czertainly.core.dao.repository.CertificateEntityRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.CertificateEntityService;
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
public class CertificateEntityServiceImpl implements CertificateEntityService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateEntityServiceImpl.class);

    @Autowired
    private CertificateEntityRepository certificateEntityRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.REQUEST)
    public List<CertificateEntityDto> listCertificateEntity() {
        return certificateEntityRepository.findAll().stream().map(CertificateEntity::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.REQUEST)
    public CertificateEntityDto getCertificateEntity(String uuid) throws NotFoundException {
        return getEntity(uuid).mapToDto();
    }

    public CertificateEntity getEntity(String uuid) throws NotFoundException {
        return certificateEntityRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.CREATE)
    public CertificateEntityDto createCertificateEntity(CertificateEntityRequestDto request) throws ValidationException, AlreadyExistException {

        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Name must not be empty");
        }

        if (certificateEntityRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(CertificateEntity.class, request.getName());
        }

        CertificateEntity entity = new CertificateEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setEntityType(request.getEntityType());
        certificateEntityRepository.save(entity);

        return entity.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.DELETE)
    public void removeCertificateEntity(String uuid) throws NotFoundException {
        CertificateEntity entity = certificateEntityRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));
        for(Certificate certificate: certificateRepository.findByEntity(entity)){
            certificate.setEntity(entity);
            certificateRepository.save(certificate);
        }
        certificateEntityRepository.delete(entity);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.DELETE)
    public void bulkRemoveCertificateEntity(List<String> entityUuids) {
        for(String uuid: entityUuids){
            try{
                CertificateEntity entity = certificateEntityRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));
                for(Certificate certificate: certificateRepository.findByEntity(entity)){
                    certificate.setEntity(entity);
                    certificateRepository.save(certificate);
                }
                certificateEntityRepository.delete(entity);
            }catch(NotFoundException e){
                logger.warn("Unable to find the entity with uuid {}. It may have been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ENTITY, operation = OperationType.CHANGE)
    public CertificateEntityDto updateCertificateEntity(String uuid, CertificateEntityRequestDto request) throws NotFoundException {
        CertificateEntity entity = certificateEntityRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));

        entity.setDescription(request.getDescription());
        entity.setEntityType(request.getEntityType());
        certificateEntityRepository.save(entity);
        return entity.mapToDto();
    }
}
