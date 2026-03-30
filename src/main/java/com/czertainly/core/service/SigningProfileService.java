package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.api.model.client.signing.protocols.ilm.IlmSigningProtocolActivationDetailDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.signing.digitalsignature.DigitalSignatureListDto;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.model.SecuredList;

import java.util.List;
import java.util.UUID;

public interface SigningProfileService {

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    PaginationResponseDto<SigningProfileListDto> listSigningProfiles(SearchRequestDto request, SecurityFilter filter);

    SecuredList<SigningProfile> listSigningProfilesAssociatedWithIlmSigningProtocol(UUID ilmSigningProtocolConfigurationUuid, SecurityFilter filter);
    SecuredList<SigningProfile> listSigningProfilesAssociatedWithTsp(UUID tspConfigurationUuid, SecurityFilter filter);

    SigningProfileDto getSigningProfile(SecuredUUID uuid, Integer version) throws NotFoundException;

    List<SigningProtocol> listSupportedProtocols(SigningWorkflowType workflowType);

    SigningProfileDto createSigningProfile(SigningProfileRequestDto request) throws AttributeException, NotFoundException;

    SigningProfileDto updateSigningProfile(SecuredUUID uuid, SigningProfileRequestDto request) throws NotFoundException, AttributeException;

    void deleteSigningProfile(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDeleteSigningProfiles(List<SecuredUUID> uuids);

    void enableSigningProfile(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkEnableSigningProfiles(List<SecuredUUID> uuids);

    void disableSigningProfile(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDisableSigningProfiles(List<SecuredUUID> uuids);

    List<ApprovalProfileDto> getAssociatedApprovalProfiles(SecuredUUID uuid) throws NotFoundException;

    void associateWithApprovalProfile(SecuredUUID signingProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException;

    void disassociateFromApprovalProfile(SecuredUUID signingProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException;

    PaginationResponseDto<DigitalSignatureListDto> listDigitalSignaturesForSigningProfile(SecuredUUID uuid, SearchRequestDto request, SecurityFilter filter) throws NotFoundException;

    IlmSigningProtocolActivationDetailDto getIlmSigningProtocolActivationDetails(SecuredUUID uuid) throws NotFoundException;

    IlmSigningProtocolActivationDetailDto activateIlmSigningProtocol(SecuredUUID signingProfileUuid, SecuredUUID ilmConfigUuid) throws NotFoundException;

    void deactivateIlmSigningProtocol(SecuredUUID uuid) throws NotFoundException;

    TspActivationDetailDto getTspActivationDetails(SecuredUUID uuid) throws NotFoundException;

    TspActivationDetailDto activateTsp(SecuredUUID signingProfileUuid, SecuredUUID tspConfigUuid) throws NotFoundException;

    void deactivateTsp(SecuredUUID uuid) throws NotFoundException;
}
