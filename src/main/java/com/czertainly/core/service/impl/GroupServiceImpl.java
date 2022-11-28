package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.certificate.group.GroupRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateGroup;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AttributeService;
import com.czertainly.core.service.GroupService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class GroupServiceImpl implements GroupService {
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private AttributeService attributeService;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CERTIFICATE_GROUP, action = ResourceAction.LIST)
    public List<GroupDto> listGroups(SecurityFilter filter) {
        List<CertificateGroup> groups;
        return groupRepository.findUsingSecurityFilter(filter)
                .stream()
                .map(CertificateGroup::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.CERTIFICATE_GROUP, action = ResourceAction.DETAIL)
    public GroupDto getGroup(SecuredUUID uuid) throws NotFoundException {
        GroupDto dto = getGroupEntity(uuid).mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(uuid.getValue(), Resource.CERTIFICATE_GROUP));
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE_GROUP, action = ResourceAction.CREATE)
    public GroupDto createGroup(GroupRequestDto request) throws ValidationException, AlreadyExistException {

        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("Name must not be empty");
        }

        if (groupRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(CertificateGroup.class, request.getName());
        }
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.CERTIFICATE_GROUP);
        CertificateGroup certificateGroup = new CertificateGroup();
        certificateGroup.setName(request.getName());
        certificateGroup.setDescription(request.getDescription());
        groupRepository.save(certificateGroup);
        attributeService.createAttributeContent(certificateGroup.getUuid(), request.getCustomAttributes(), Resource.COMPLIANCE_PROFILE);
        GroupDto dto = certificateGroup.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(certificateGroup.getUuid(), Resource.CERTIFICATE_GROUP));
        return dto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE_GROUP, action = ResourceAction.DELETE)
    public void deleteGroup(SecuredUUID uuid) throws NotFoundException {
        CertificateGroup certificateGroup = getGroupEntity(uuid);
        for(Certificate certificate: certificateRepository.findByGroup(certificateGroup)){
            certificate.setGroup(null);
            certificate.setGroupUuid(null);
            certificateRepository.save(certificate);
        }
        attributeService.deleteAttributeContent(certificateGroup.getUuid(), Resource.CERTIFICATE_GROUP);
        groupRepository.delete(certificateGroup);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE_GROUP, action = ResourceAction.DELETE)
    public void bulkDeleteGroup(List<SecuredUUID> entityUuids) {
        for(SecuredUUID uuid: entityUuids){
            try{
                deleteGroup(uuid);
            }catch(NotFoundException e){
                logger.warn("Unable to find the group with uuid {}. It may have been deleted", uuid);
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.CERTIFICATE_GROUP, action = ResourceAction.LIST)
    public Long statisticsGroupCount(SecurityFilter filter) {
        return groupRepository.countUsingSecurityFilter(filter);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.GROUP, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.CERTIFICATE_GROUP, action = ResourceAction.UPDATE)
    public GroupDto editGroup(SecuredUUID uuid, GroupRequestDto request) throws NotFoundException {
        attributeService.validateCustomAttributes(request.getCustomAttributes(), Resource.CERTIFICATE_GROUP);
        CertificateGroup certificateGroup = getGroupEntity(uuid);

        certificateGroup.setDescription(request.getDescription());
        groupRepository.save(certificateGroup);

        attributeService.updateAttributeContent(certificateGroup.getUuid(), request.getCustomAttributes(), Resource.CERTIFICATE_GROUP);
        GroupDto dto = certificateGroup.mapToDto();
        dto.setCustomAttributes(attributeService.getCustomAttributesWithValues(certificateGroup.getUuid(), Resource.CERTIFICATE_GROUP));
        return dto;
    }

    private CertificateGroup getGroupEntity(SecuredUUID uuid) throws NotFoundException {
        return groupRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(CertificateGroup.class, uuid));
    }
}
