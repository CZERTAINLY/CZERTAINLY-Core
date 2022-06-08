package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.service.GroupService;
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
public class GroupServiceImpl implements GroupService {
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.REQUEST)
    public List<GroupDto> listGroups() {
        return groupRepository.findAll().stream().map(CertificateGroup::mapToDto).collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.REQUEST)
    public GroupDto getCertificateGroup(String uuid) throws NotFoundException {
        return getGroupEntity(uuid).mapToDto();
    }

    public CertificateGroup getGroupEntity(String uuid) throws NotFoundException {
        return groupRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.CREATE)
    public GroupDto createGroup(GroupRequestDto request) throws ValidationException, AlreadyExistException {

        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Name must not be empty");
        }

        if (groupRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(CertificateGroup.class, request.getName());
        }

        CertificateGroup certificateGroup = new CertificateGroup();
        certificateGroup.setName(request.getName());
        certificateGroup.setDescription(request.getDescription());
        groupRepository.save(certificateGroup);

        return certificateGroup.mapToDto();
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.DELETE)
    public void removeGroup(String uuid) throws NotFoundException {
        CertificateGroup certificateGroup = groupRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));
        for(Certificate certificate: certificateRepository.findByGroup(certificateGroup)){
            certificate.setGroup(null);
            certificateRepository.save(certificate);
        }
        groupRepository.delete(certificateGroup);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.DELETE)
    public void bulkRemoveGroup(List<String> entityUuids) {
        for(String uuid: entityUuids){
            try{
                CertificateGroup certificateGroup = groupRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));
                for(Certificate certificate: certificateRepository.findByGroup(certificateGroup)){
                    certificate.setGroup(null);
                    certificateRepository.save(certificate);
                }
                groupRepository.delete(certificateGroup);
            }catch(NotFoundException e){
                logger.warn("Unable to find the group with uuid {}. It may have been deleted", uuid);
            }
        }
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.CHANGE)
    public GroupDto updateGroup(String uuid, GroupRequestDto request) throws NotFoundException {
        CertificateGroup certificateGroup = groupRepository.findByUuid(uuid)
                .orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));

        certificateGroup.setDescription(request.getDescription());
        groupRepository.save(certificateGroup);
        return certificateGroup.mapToDto();
    }
}
