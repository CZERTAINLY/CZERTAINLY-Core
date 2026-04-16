package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.SimplifiedSigningProfileDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.model.signing.scheme.SigningSchemeModel;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.api.model.client.signing.protocols.tsp.TspActivationDetailDto;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.signing.signingrecord.SigningRecordListDto;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.model.SecuredList;

import java.util.List;
import java.util.UUID;

public interface SigningProfileService extends ResourceExtensionService {

    List<SearchFieldDataByGroupDto> getSearchableFieldInformation();

    PaginationResponseDto<SigningProfileListDto> listSigningProfiles(SearchRequestDto request, SecurityFilter filter);

    List<SimplifiedSigningProfileDto> listSigningProfilesAssociatedTimeQualityConfiguration(SecuredUUID timeQualityConfigurationUuid, SecurityFilter filter);

    SecuredList<SigningProfile> listSigningProfileEntitiesAssociatedTimeQualityConfiguration(SecuredUUID timeQualityConfigurationUuid, SecurityFilter filter);

    SecuredList<SigningProfile> listSigningProfilesAssociatedWithTsp(SecuredUUID tspProfileUuid, SecurityFilter filter);

    SigningProfileDto getSigningProfile(SecuredUUID uuid, Integer version) throws NotFoundException;

    /**
     * Resolves a Signing Profile by name, verifying it uses a timestamping workflow.
     *
     * @throws NotFoundException if the profile does not exist or is not configured with a timestamping workflow
     */
    SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ? extends SigningSchemeModel> getManagedTimestampingProfileModel(String name) throws NotFoundException;

    List<SigningProtocol> listSupportedProtocols(SigningWorkflowType workflowType);

    SigningProfileDto createSigningProfile(SigningProfileRequestDto request) throws AlreadyExistException, AttributeException, NotFoundException;

    SigningProfileDto updateSigningProfile(SecuredUUID uuid, SigningProfileRequestDto request) throws AlreadyExistException, AttributeException, NotFoundException;

    void deleteSigningProfile(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDeleteSigningProfiles(List<SecuredUUID> uuids);

    void enableSigningProfile(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkEnableSigningProfiles(List<SecuredUUID> uuids);

    void disableSigningProfile(SecuredUUID uuid) throws NotFoundException;

    List<BulkActionMessageDto> bulkDisableSigningProfiles(List<SecuredUUID> uuids);

    List<ApprovalProfileDto> getAssociatedApprovalProfiles(SecuredUUID uuid) throws NotFoundException;

    void associateWithApprovalProfile(SecuredUUID signingProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException;

    void disassociateFromApprovalProfile(SecuredUUID signingProfileUuid, SecuredUUID approvalProfileUuid) throws NotFoundException;

    List<CertificateDto> listSigningCertificates(SigningWorkflowType signingWorkflowType, boolean qualifiedTimestamp);

    List<BaseAttribute> listSignatureAttributesForCertificate(UUID certificateUuid) throws NotFoundException;

    PaginationResponseDto<SigningRecordListDto> listSigningRecordsForSigningProfile(SecuredUUID uuid, SearchRequestDto request, SecurityFilter filter) throws NotFoundException;

    TspActivationDetailDto getTspActivationDetails(SecuredUUID uuid) throws NotFoundException;

    TspActivationDetailDto activateTsp(SecuredUUID signingProfileUuid, SecuredUUID tspProfileUuid) throws NotFoundException;

    void deactivateTsp(SecuredUUID uuid) throws NotFoundException;
}
