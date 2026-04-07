package com.czertainly.core.api.web;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.SigningProfileController;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolActivationDetailDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.auth.AuthEndpoint;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.util.converter.SigningWorkflowTypeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SigningProfileControllerImpl implements SigningProfileController {

    private final SigningProfileService signingProfileService;

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(SigningWorkflowType.class, new SigningWorkflowTypeConverter());
    }

    @Autowired
    public SigningProfileControllerImpl(SigningProfileService signingProfileService) {
        this.signingProfileService = signingProfileService;
    }

    @Override
    @AuthEndpoint(resourceName = Resource.SIGNING_PROFILE)
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return signingProfileService.getSearchableFieldInformation();
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.LIST)
    public List<SigningProtocol> listSupportedProtocols(String workflowType) {
        return signingProfileService.listSupportedProtocols(SigningWorkflowType.findByCode(workflowType));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.LIST)
    public PaginationResponseDto<SigningProfileListDto> listSigningProfiles(SearchRequestDto request) {
        return signingProfileService.listSigningProfiles(request, SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DETAIL)
    public SigningProfileDto getSigningProfile(@LogResource(uuid = true) UUID uuid, Integer version) throws NotFoundException {
        return signingProfileService.getSigningProfile(SecuredUUID.fromUUID(uuid), version);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.CREATE)
    public SigningProfileDto createSigningProfile(SigningProfileRequestDto request) throws AttributeException, NotFoundException {
        return signingProfileService.createSigningProfile(request);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.UPDATE)
    public SigningProfileDto updateSigningProfile(@LogResource(uuid = true) UUID uuid, SigningProfileRequestDto request) throws NotFoundException, AttributeException {
        return signingProfileService.updateSigningProfile(SecuredUUID.fromUUID(uuid), request);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DELETE)
    public void deleteSigningProfile(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        signingProfileService.deleteSigningProfile(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DELETE)
    public List<BulkActionMessageDto> bulkDeleteSigningProfiles(@LogResource(uuid = true) List<UUID> uuids) {
        return signingProfileService.bulkDeleteSigningProfiles(SecuredUUID.fromUuidList(uuids));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.ENABLE)
    public void enableSigningProfile(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        signingProfileService.enableSigningProfile(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.ENABLE)
    public List<BulkActionMessageDto> bulkEnableSigningProfiles(@LogResource(uuid = true) List<UUID> uuids) {
        return signingProfileService.bulkEnableSigningProfiles(SecuredUUID.fromUuidList(uuids));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DISABLE)
    public void disableSigningProfile(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        signingProfileService.disableSigningProfile(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DISABLE)
    public List<BulkActionMessageDto> bulkDisableSigningProfiles(@LogResource(uuid = true) List<UUID> uuids) {
        return signingProfileService.bulkDisableSigningProfiles(SecuredUUID.fromUuidList(uuids));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DETAIL)
    public List<ApprovalProfileDto> getAssociatedApprovalProfiles(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return signingProfileService.getAssociatedApprovalProfiles(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.UPDATE)
    public void associateWithApprovalProfile(@LogResource(uuid = true) UUID signingProfileUuid, @LogResource(uuid = true, affiliated = true) UUID approvalProfileUuid) throws NotFoundException {
        signingProfileService.associateWithApprovalProfile(SecuredUUID.fromUUID(signingProfileUuid), SecuredUUID.fromUUID(approvalProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.UPDATE)
    public void disassociateFromApprovalProfile(@LogResource(uuid = true) UUID signingProfileUuid, @LogResource(uuid = true, affiliated = true) UUID approvalProfileUuid) throws NotFoundException {
        signingProfileService.disassociateFromApprovalProfile(SecuredUUID.fromUUID(signingProfileUuid), SecuredUUID.fromUUID(approvalProfileUuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.LIST)
    public List<CertificateDto> listSigningCertificates(SigningWorkflowType signingWorkflowType) {
        return signingProfileService.listSigningCertificates(signingWorkflowType);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.LIST)
    public List<BaseAttribute> listSignatureAttributesForCertificate(@LogResource(uuid = true) UUID certificateUuid) throws NotFoundException {
        return signingProfileService.listSignatureAttributesForCertificate(certificateUuid);
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DETAIL)
    public PaginationResponseDto<DigitalSignatureListDto> listDigitalSignaturesForSigningProfile(@LogResource(uuid = true) UUID uuid, SearchRequestDto request) throws NotFoundException {
        return signingProfileService.listDigitalSignaturesForSigningProfile(SecuredUUID.fromUUID(uuid), request, SecurityFilter.create());
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DETAIL)
    public IlmSigningProtocolActivationDetailDto getIlmSigningProtocolActivationDetails(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return signingProfileService.getIlmSigningProtocolActivationDetails(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.ACTIVATE_PROTOCOL)
    public IlmSigningProtocolActivationDetailDto activateIlmSigningProtocol(@LogResource(uuid = true) UUID signingProfileUuid, @LogResource(uuid = true, affiliated = true) UUID ilmConfigUuid) throws NotFoundException {
        return signingProfileService.activateIlmSigningProtocol(SecuredUUID.fromUUID(signingProfileUuid), SecuredUUID.fromUUID(ilmConfigUuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DEACTIVATE_PROTOCOL)
    public void deactivateIlmSigningProtocol(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        signingProfileService.deactivateIlmSigningProtocol(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DETAIL)
    public TspActivationDetailDto getTspActivationDetails(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        return signingProfileService.getTspActivationDetails(SecuredUUID.fromUUID(uuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.ACTIVATE_PROTOCOL)
    public TspActivationDetailDto activateTsp(@LogResource(uuid = true) UUID signingProfileUuid, @LogResource(uuid = true, affiliated = true) UUID tspConfigUuid) throws NotFoundException {
        return signingProfileService.activateTsp(SecuredUUID.fromUUID(signingProfileUuid), SecuredUUID.fromUUID(tspConfigUuid));
    }

    @Override
    @AuditLogged(module = Module.SIGNING, resource = Resource.SIGNING_PROFILE, operation = Operation.DEACTIVATE_PROTOCOL)
    public void deactivateTsp(@LogResource(uuid = true) UUID uuid) throws NotFoundException {
        signingProfileService.deactivateTsp(SecuredUUID.fromUUID(uuid));
    }
}
