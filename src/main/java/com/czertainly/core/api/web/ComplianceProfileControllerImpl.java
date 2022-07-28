package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.ComplianceProfileController;
import com.czertainly.api.model.client.compliance.ComplianceGroupRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceGroupsListResponseDto;
import com.czertainly.api.model.client.compliance.ComplianceProfileComplianceCheckDto;
import com.czertainly.api.model.client.compliance.ComplianceProfileRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceRuleAdditionRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceRuleDeletionRequestDto;
import com.czertainly.api.model.client.compliance.ComplianceRulesListResponseDto;
import com.czertainly.api.model.client.compliance.RaProfileAssociationRequestDto;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceProfilesListDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.model.auth.ActionName;
import com.czertainly.core.model.auth.ResourceName;
import com.czertainly.core.service.ComplianceProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class ComplianceProfileControllerImpl implements ComplianceProfileController {
    @Autowired
    private ComplianceProfileService complianceProfileService;

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.LIST, isListingEndPoint = true)
    public List<ComplianceProfilesListDto> listComplianceProfiles() {
        return complianceProfileService.listComplianceProfiles();
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.DETAIL)
    public ComplianceProfileDto getComplianceProfile(String uuid) throws NotFoundException {
        return complianceProfileService.getComplianceProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.CREATE)
    public ResponseEntity<UuidDto> createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, ConnectorException {
        ComplianceProfileDto complianceProfile = complianceProfileService.createComplianceProfile(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{complianceProfileUuid}")
                .buildAndExpand(complianceProfile.getUuid())
                .toUri();

        UuidDto dto = new UuidDto();
        dto.setUuid(complianceProfile.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.ADD_RULE)
    public void addRule(String uuid, ComplianceRuleAdditionRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException {
        complianceProfileService.addRule(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.REMOVE_RULE)
    public void removeRule(String uuid, ComplianceRuleDeletionRequestDto request) throws NotFoundException {
        complianceProfileService.removeRule(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.ADD_GROUP)
    public void addGroup(String uuid, ComplianceGroupRequestDto request) throws AlreadyExistException, NotFoundException {
        complianceProfileService.addGroup(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.REMOVE_GROUP)
    public void removeGroup(String uuid, ComplianceGroupRequestDto request) throws NotFoundException {
        complianceProfileService.removeGroup(uuid, request);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.DELETE)
    public void deleteComplianceProfile(String uuid) throws NotFoundException {
        complianceProfileService.deleteComplianceProfile(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.LIST_ASSOCIATED_RA_PROFILE)
    public List<SimplifiedRaProfileDto> getAssociatedRAProfiles(String uuid) throws NotFoundException {
        return complianceProfileService.getAssociatedRAProfiles(uuid);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.DELETE)
    public List<BulkActionMessageDto> bulkDeleteComplianceProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        return complianceProfileService.bulkDeleteComplianceProfiles(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.FORCE_DELETE)
    public List<BulkActionMessageDto> forceDeleteComplianceProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        return complianceProfileService.forceDeleteComplianceProfiles(uuids);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.LIST_RULE)
    public List<ComplianceRulesListResponseDto> getComplianceRules(String complianceProviderUuid, String kind, List<CertificateType> certificateType) throws NotFoundException {
        return complianceProfileService.getComplianceRules(complianceProviderUuid, kind, certificateType);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.LIST_GROUP)
    public List<ComplianceGroupsListResponseDto> getComplianceGroups(String complianceProviderUuid, String kind) throws NotFoundException {
        return complianceProfileService.getComplianceGroups(complianceProviderUuid, kind);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.ASSOCIATE_RA_PROFILE)
    public void associateProfiles(String uuid, RaProfileAssociationRequestDto raProfiles) throws NotFoundException, ConnectorException {
        complianceProfileService.associateProfile(uuid, raProfiles);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.DISASSOCIATE_RA_PROFILE)
    public void disassociateProfiles(String uuid, RaProfileAssociationRequestDto raProfiles) throws NotFoundException, ConnectorException {
        complianceProfileService.disassociateProfile(uuid, raProfiles);
    }

    @Override
    @AuthEndpoint(resourceName = ResourceName.COMPLIANCE_PROFILE, actionName = ActionName.CHECK_COMPLIANCE)
    public void checkCompliance(ComplianceProfileComplianceCheckDto request) throws NotFoundException {
        complianceProfileService.checkCompliance(request);
    }

}
