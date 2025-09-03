package com.czertainly.core.api.web.v2;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.v2.ComplianceProfileController;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileGroupsPatchRequestDto;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileRequestDto;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileRulesPatchRequestDto;
import com.czertainly.api.model.client.compliance.v2.ComplianceProfileUpdateRequestDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.ComplianceGroupListDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceProfileListDto;
import com.czertainly.api.model.core.compliance.v2.ComplianceRuleListDto;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.other.ResourceObjectDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ComplianceProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ComplianceProfileControllerImpl implements ComplianceProfileController {

    private ComplianceProfileService complianceProfileService;

    @Autowired
    public void setComplianceProfileService(ComplianceProfileService complianceProfileService) {
        this.complianceProfileService = complianceProfileService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.COMPLIANCE_PROFILE)
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.LIST)
    public List<ComplianceProfileListDto> listComplianceProfiles() {
        return complianceProfileService.listComplianceProfiles(SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.DETAIL)
    public ComplianceProfileDto getComplianceProfile(UUID uuid) {
        return null;
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.CREATE)
    public ComplianceProfileDto createComplianceProfile(ComplianceProfileRequestDto request) {
        return null;
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.UPDATE)
    public ComplianceProfileDto updateComplianceProfile(UUID uuid, ComplianceProfileUpdateRequestDto request) {
        return null;
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.DELETE)
    public void deleteComplianceProfile(UUID uuid) {

    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.DELETE)
    public List<BulkActionMessageDto> bulkDeleteComplianceProfiles(List<UUID> uuids) {
        return List.of();
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteComplianceProfiles(List<UUID> uuids) {
        return List.of();
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_RULE, operation = Operation.LIST)
    public List<ComplianceRuleListDto> getComplianceRules(UUID connectorUuid, String kind, @LogResource(resource = true, affiliated = true) Resource resource, String type, String format) {
        return List.of();
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_GROUP, operation = Operation.LIST)
    public List<ComplianceGroupListDto> getComplianceGroups(UUID connectorUuid, String kind, @LogResource(resource = true, affiliated = true) Resource resource) {
        return List.of();
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, affiliatedResource = Resource.COMPLIANCE_RULE, operation = Operation.UPDATE)
    public void patchComplianceProfileRule(UUID uuid, ComplianceProfileRulesPatchRequestDto request) {

    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, affiliatedResource = Resource.COMPLIANCE_GROUP, operation = Operation.UPDATE)
    public void patchComplianceProfileGroup(UUID uuid, ComplianceProfileGroupsPatchRequestDto request) {

    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.LIST_ASSOCIATIONS)
    public List<ResourceObjectDto> getAssociations(UUID uuid) {
        return List.of();
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.ASSOCIATE)
    public void associateComplianceProfile(UUID uuid, @LogResource(resource = true, affiliated = true) Resource resource, @LogResource(uuid = true, affiliated = true) UUID associationObjectUuid) {

    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.DISASSOCIATE)
    public void disassociateComplianceProfile(UUID uuid, @LogResource(resource = true, affiliated = true) Resource resource, @LogResource(uuid = true, affiliated = true) UUID associationObjectUuid) {

    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.LIST_ASSOCIATIONS)
    public List<ComplianceProfileListDto> getAssociatedComplianceProfiles(@LogResource(resource = true, affiliated = true) Resource resource, @LogResource(uuid = true, affiliated = true) UUID associationObjectUuid) {
        return List.of();
    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.CHECK_COMPLIANCE)
    public void checkCompliance(List<UUID> uuids) {

    }

    @Override
    @AuditLogged(module = Module.COMPLIANCE, resource = Resource.COMPLIANCE_PROFILE, operation = Operation.CHECK_COMPLIANCE)
    public void checkResourceObjectCompliance(@LogResource(resource = true, affiliated = true) Resource resource, @LogResource(uuid = true, affiliated = true) UUID objectUuid) {

    }
}
