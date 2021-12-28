package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.group.CertificateGroupDto;
import com.czertainly.api.model.core.certificate.group.CertificateGroupRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEntity;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.repository.CertificateGroupRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.service.CertificateGroupService;
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
public class CertificateGroupServiceImpl implements CertificateGroupService {
    private static final Logger logger = LoggerFactory.getLogger(CertificateGroupServiceImpl.class);

    @Autowired
    private CertificateGroupRepository certificateGroupRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.REQUEST)
    public List<CertificateGroupDto> listCertificateGroups() {
        return certificateGroupRepository.findAll().stream().map(CertificateGroup::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.REQUEST)
    public CertificateGroupDto getCertificateGroup(String uuid) throws NotFoundException {
        return getGroupEntity(uuid).mapToDto();
    }

    public CertificateGroup getGroupEntity(String uuid) throws NotFoundException {
        return certificateGroupRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.CREATE)
    public CertificateGroupDto createCertificateGroup(CertificateGroupRequestDto request) throws ValidationException, AlreadyExistException {

        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Name must not be empty");
        }

        if (certificateGroupRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(CertificateGroup.class, request.getName());
        }

        CertificateGroup group = new CertificateGroup();
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        certificateGroupRepository.save(group);

        return group.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.DELETE)
    public void removeCertificateGroup(String uuid) throws NotFoundException {
        CertificateGroup group = certificateGroupRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));
        for(Certificate certificate: certificateRepository.findByGroup(group)){
            certificate.setGroup(null);
            certificateRepository.save(certificate);
        }
        certificateGroupRepository.delete(group);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.DELETE)
    public void bulkRemoveCertificateGroup(List<String> entityUuids) {
        for(String uuid: entityUuids){
            try{
                CertificateGroup group = certificateGroupRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(CertificateEntity.class, uuid));
                for(Certificate certificate: certificateRepository.findByGroup(group)){
                    certificate.setGroup(null);
                    certificateRepository.save(certificate);
                }
                certificateGroupRepository.delete(group);
            }catch(NotFoundException e){
                logger.warn("Unable to find the group with uuid {}. It may have been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.CHANGE)
    public CertificateGroupDto updateCertificateGroup(String uuid, CertificateGroupRequestDto request) throws NotFoundException {
        CertificateGroup group = certificateGroupRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));

        group.setDescription(request.getDescription());
        certificateGroupRepository.save(group);
        return group.mapToDto();
    }
}
