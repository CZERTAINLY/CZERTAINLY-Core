package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.ComplianceProfileController;
import com.czertainly.api.model.client.compliance.*;
import com.czertainly.api.model.client.raprofile.SimplifiedRaProfileDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceProfileDto;
import com.czertainly.api.model.core.compliance.ComplianceProfilesListDto;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
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
    @AuthEndpoint(resourceName = Resource.COMPLIANCE_PROFILE)
    public List<ComplianceProfilesListDto> listComplianceProfiles() {
        return complianceProfileService.listComplianceProfiles(SecurityFilter.create());
    }

    @Override
    public ComplianceProfileDto getComplianceProfile(String uuid) throws NotFoundException {
        return complianceProfileService.getComplianceProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public ResponseEntity<UuidDto> createComplianceProfile(ComplianceProfileRequestDto request) throws AlreadyExistException, ConnectorException, AttributeException {
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
    public ComplianceProfileRuleDto addRule(String uuid, ComplianceRuleAdditionRequestDto request) throws AlreadyExistException, NotFoundException, ValidationException {
        return complianceProfileService.addRule(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void removeRule(String uuid, ComplianceRuleDeletionRequestDto request) throws NotFoundException {
        complianceProfileService.removeRule(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void addGroup(String uuid, ComplianceGroupRequestDto request) throws AlreadyExistException, NotFoundException {
        complianceProfileService.addGroup(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void removeGroup(String uuid, ComplianceGroupRequestDto request) throws NotFoundException {
        complianceProfileService.removeGroup(SecuredUUID.fromString(uuid), request);
    }

    @Override
    public void deleteComplianceProfile(String uuid) throws NotFoundException {
        complianceProfileService.deleteComplianceProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<SimplifiedRaProfileDto> getAssociatedRAProfiles(String uuid) throws NotFoundException {
        return complianceProfileService.getAssociatedRAProfiles(SecuredUUID.fromString(uuid));
    }

    @Override
    public List<BulkActionMessageDto> bulkDeleteComplianceProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        return complianceProfileService.bulkDeleteComplianceProfiles(SecuredUUID.fromList(uuids));
    }

    @Override
    public List<BulkActionMessageDto> forceDeleteComplianceProfiles(List<String> uuids) throws NotFoundException, ValidationException {
        return complianceProfileService.forceDeleteComplianceProfiles(SecuredUUID.fromList(uuids));
    }

    @Override
    public List<ComplianceRulesListResponseDto> getComplianceRules(String complianceProviderUuid, String kind, List<CertificateType> certificateType) throws NotFoundException {
        return complianceProfileService.getComplianceRules(complianceProviderUuid, kind, certificateType);
    }

    @Override
    public List<ComplianceGroupsListResponseDto> getComplianceGroups(String complianceProviderUuid, String kind) throws NotFoundException {
        return complianceProfileService.getComplianceGroups(complianceProviderUuid, kind);
    }

    @Override
    public void associateProfiles(String uuid, RaProfileAssociationRequestDto raProfiles) throws ConnectorException {
        complianceProfileService.associateProfile(SecuredUUID.fromString(uuid), raProfiles);
    }

    @Override
    public void disassociateProfiles(String uuid, RaProfileAssociationRequestDto raProfiles) throws ConnectorException {
        complianceProfileService.disassociateProfile(SecuredUUID.fromString(uuid), raProfiles);
    }

    @Override
    public void checkCompliance(List<String> uuids) throws NotFoundException {
        complianceProfileService.checkCompliance(SecuredUUID.fromList(uuids));
    }

}
