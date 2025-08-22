package com.czertainly.core.service.impl;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileGroupsPatchRequestDto;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileRequestDto;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileRulesPatchRequestDto;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.ComplianceGroupListDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileListDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceRuleListDto;
import com.czertainly.api.model.core.other.ResourceObjectDto;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.dao.entity.ComplianceProfile;
import com.czertainly.core.dao.entity.ComplianceProfileAssociation;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service(Resource.Codes.COMPLIANCE_PROFILE)
@Transactional
public class ComplianceProfileServiceImpl implements ComplianceProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceProfileServiceImpl.class);

    private ComplianceProfileRepository complianceProfileRepository;
    private ComplianceProfileRuleRepository complianceProfileRuleRepository;
    private ComplianceProfileAssociationRepository complianceProfileAssociationRepository;

    private ComplianceRuleRepository complianceRuleRepository;
    private ComplianceGroupRepository complianceGroupRepository;

    private AttributeEngine attributeEngine;

    @Autowired
    public void setComplianceProfileRepository(ComplianceProfileRepository complianceProfileRepository) {
        this.complianceProfileRepository = complianceProfileRepository;
    }

    @Autowired
    public void setComplianceProfileAssociationRepository(ComplianceProfileAssociationRepository complianceProfileAssociationRepository) {
        this.complianceProfileAssociationRepository = complianceProfileAssociationRepository;
    }

    @Autowired
    public void setComplianceRuleRepository(ComplianceRuleRepository complianceRuleRepository) {
        this.complianceRuleRepository = complianceRuleRepository;
    }

    @Autowired
    public void setComplianceGroupRepository(ComplianceGroupRepository complianceGroupRepository) {
        this.complianceGroupRepository = complianceGroupRepository;
    }

    @Autowired
    public void setComplianceProfileRuleRepository(ComplianceProfileRuleRepository complianceProfileRuleRepository) {
        this.complianceProfileRuleRepository = complianceProfileRuleRepository;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }


    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.LIST)
    public List<ComplianceProfileListDto> listComplianceProfiles(SecurityFilter filter) {
        return complianceProfileRepository.findUsingSecurityFilter(filter).stream().map(ComplianceProfile::mapToListDto).toList();
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DETAIL)
    public ComplianceProfileDto getComplianceProfile(SecuredUUID uuid) {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(uuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, uuid));
        ComplianceProfileDto dto = complianceProfile.mapToDto();
        dto.setCustomAttributes(attributeEngine.getObjectCustomAttributesContent(Resource.COMPLIANCE_PROFILE, uuid.getValue()));

        return dto;
    }

    @Override
    public ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) {
        if (complianceProfileRepository.findByName(request.getName()).isPresent()) {
            throw new AlreadyExistException(ComplianceProfile.class, request.getName());
        }
    }

    @Override
    public ComplianceProfileDto updateComplianceProfile(SecuredUUID uuid, ComplianceProfileUpdateRequestDto request) {
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.COMPLIANCE_PROFILE, action = ResourceAction.DELETE)
    public void deleteComplianceProfile(SecuredUUID uuid) throws NotFoundException {
        deleteComplianceProfile(uuid, false);
    }

    @Override
    public List<BulkActionMessageDto> bulkDeleteComplianceProfiles(List<SecuredUUID> uuids) {
        return List.of();
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteComplianceProfiles(List<SecuredUUID> uuids) {
        return List.of();
    }

    @Override
    public List<ComplianceRuleListDto> getComplianceRules(Resource resource, UUID connectorUuid, String kind, String type, String format) {
        return List.of();
    }

    @Override
    public List<ComplianceGroupListDto> getComplianceGroups(Resource resource, UUID connectorUuid, String kind) {
        return List.of();
    }

    @Override
    public void patchComplianceProfileRule(SecuredUUID uuid, ComplianceProfileRulesPatchRequestDto request) {

    }

    @Override
    public void patchComplianceProfileGroup(SecuredUUID uuid, ComplianceProfileGroupsPatchRequestDto request) {

    }

    @Override
    public List<ResourceObjectDto> getAssociations(SecuredUUID uuid) {
        return List.of();
    }

    @Override
    public void associateComplianceProfile(SecuredUUID uuid, Resource resource, UUID associationObjectUuid) {

    }

    @Override
    public void disassociateComplianceProfile(SecuredUUID uuid, Resource resource, UUID associationObjectUuid) {

    }

    @Override
    public List<ComplianceProfileListDto> getAssociatedComplianceProfiles(Resource resource, UUID associationObjectUuid) {
        return List.of();
    }

    @Override
    public void checkCompliance(List<SecuredUUID> uuids) {

    }

    @Override
    public void checkResourceObjectCompliance(Resource resource, UUID objectUuid) {

    }

    @Override
    public List<NameAndUuidDto> listResourceObjects(SecurityFilter filter) {
        return List.of();
    }

    @Override
    public void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException {

    }

    private void deleteComplianceProfile(SecuredUUID complianceProfileUuid, boolean isForce) throws NotFoundException {
        ComplianceProfile complianceProfile = complianceProfileRepository.findByUuid(complianceProfileUuid).orElseThrow(() -> new NotFoundException(ComplianceProfile.class, complianceProfileUuid));
        if (!isForce && !complianceProfile.getAssociations().isEmpty()) {
            var associations = getAssociations(complianceProfileUuid);
            String associatedObjects = String.join(", ", associations.stream().map(a -> "%s '%s'".formatted(a.getResource().getLabel(), a.getName())).toList());
            throw new ValidationException("Unable to delete compliance profile due to associated resource objects: " + associatedObjects);
        }

        complianceProfileAssociationRepository.deleteByComplianceProfileUuid(complianceProfileUuid.getValue());
        attributeEngine.deleteAllObjectAttributeContent(Resource.COMPLIANCE_PROFILE, complianceProfile.getUuid());

        complianceProfileRepository.delete(complianceProfile);
    }
}
