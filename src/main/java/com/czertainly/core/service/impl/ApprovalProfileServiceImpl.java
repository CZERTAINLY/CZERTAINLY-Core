package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.*;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.dao.entity.ApprovalProfileVersion;
import com.czertainly.core.dao.entity.ApprovalStep;
import com.czertainly.core.dao.repository.ApprovalProfileRepository;
import com.czertainly.core.dao.repository.ApprovalProfileVersionRepository;
import com.czertainly.core.dao.repository.ApprovalStepRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalProfileService;
import com.czertainly.core.util.ApprovalRecipientHelper;
import com.czertainly.core.util.RequestValidatorHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class ApprovalProfileServiceImpl implements ApprovalProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalProfileServiceImpl.class);

    private ApprovalProfileRepository approvalProfileRepository;

    private ApprovalStepRepository approvalStepRepository;

    private ApprovalProfileVersionRepository approvalProfileVersionRepository;

    private ApprovalRecipientHelper approvalRecipientHelper;

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.LIST)
    public ApprovalProfileResponseDto listApprovalProfiles(final SecurityFilter filter, final PaginationRequestDto paginationRequestDto) {
        RequestValidatorHelper.revalidatePaginationRequestDto(paginationRequestDto);
        final Pageable pageable = PageRequest.of(paginationRequestDto.getPageNumber() - 1, paginationRequestDto.getItemsPerPage());
        final List<ApprovalProfile> approvalProfileList = approvalProfileRepository.findUsingSecurityFilter(filter, List.of("approvalProfileRelations"), null, pageable, null);

        final Long maxItems = approvalProfileRepository.countUsingSecurityFilter(filter, null);
        final ApprovalProfileResponseDto responseDto = new ApprovalProfileResponseDto();
        responseDto.setApprovalProfiles(approvalProfileList.stream().map(approvalProfile -> approvalProfile.getTheLatestApprovalProfileVersion().mapToDto()).toList());
        responseDto.setItemsPerPage(paginationRequestDto.getItemsPerPage());
        responseDto.setPageNumber(paginationRequestDto.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / paginationRequestDto.getItemsPerPage()));
        return responseDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL_PROFILE, operation = OperationType.REQUEST)
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.DETAIL)
    public ApprovalProfileDetailDto getApprovalProfile(final SecuredUUID uuid, final Integer version) throws NotFoundException {
        ApprovalProfileDetailDto approvalProfileDetailDto;
        if (version == null) {
            approvalProfileDetailDto = findApprovalProfileByUuid(uuid).getTheLatestApprovalProfileVersion().mapToDtoWithSteps();
        } else {
            approvalProfileDetailDto = findApprovalProfileByUuid(uuid).getApprovalProfileVersionByVersion(version).mapToDtoWithSteps();
        }

        approvalProfileDetailDto.getApprovalSteps().forEach(approvalStep -> approvalRecipientHelper.fillApprovalStepDto(approvalStep));
        return approvalProfileDetailDto;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL_PROFILE, operation = OperationType.DELETE)
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.DELETE)
    public void deleteApprovalProfile(final SecuredUUID uuid) throws NotFoundException, ValidationException {
        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        if (approvalProfile.getApprovalProfileVersions().stream().anyMatch(apv -> (apv.getApprovals() != null && !apv.getApprovals().isEmpty()))) {
            throw new ValidationException(ValidationError.create("Unable to delete approval profile with existing approvals."));
        }
        approvalProfile.getApprovalProfileVersions().forEach(apv -> {
            approvalStepRepository.deleteAll(apv.getApprovalSteps());
            approvalProfileVersionRepository.delete(apv);
        });
        approvalProfileRepository.delete(approvalProfile);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL_PROFILE, operation = OperationType.ENABLE)
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.ENABLE)
    public void enableApprovalProfile(final SecuredUUID uuid) throws NotFoundException, ValidationException {
        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        enableDisableApprovalProfile(approvalProfile, true);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL_PROFILE, operation = OperationType.DISABLE)
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.ENABLE)
    public void disableApprovalProfile(final SecuredUUID uuid) throws NotFoundException, ValidationException {
        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        enableDisableApprovalProfile(approvalProfile, false);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL_PROFILE, operation = OperationType.CREATE)
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.CREATE)
    public ApprovalProfile createApprovalProfile(final ApprovalProfileRequestDto approvalProfileRequestDto) throws NotFoundException, AlreadyExistException {
        if (approvalProfileRequestDto.getExpiry() != null && approvalProfileRequestDto.getExpiry() <= 0) {
            throw new ValidationException("Expiry value (if set) should be greater than 0");
        }
        if (approvalProfileRepository.findByName(approvalProfileRequestDto.getName()).isPresent()) {
            throw new AlreadyExistException("Approval profile with name " + approvalProfileRequestDto.getName() + " already exists.");
        }

        final ApprovalProfile approvalProfile = new ApprovalProfile();
        approvalProfile.setName(approvalProfileRequestDto.getName());
        approvalProfile.setEnabled(approvalProfileRequestDto.isEnabled());
        approvalProfileRepository.save(approvalProfile);

        final ApprovalProfileVersion approvalProfileVersion = new ApprovalProfileVersion();
        approvalProfileVersion.setExpiry(approvalProfileRequestDto.getExpiry());
        approvalProfileVersion.setDescription(approvalProfileRequestDto.getDescription());
        approvalProfileVersion.setApprovalProfileUuid(approvalProfile.getUuid());
        approvalProfileVersion.setApprovalProfile(approvalProfile);
        approvalProfileVersionRepository.save(approvalProfileVersion);

        approvalProfile.getApprovalProfileVersions().add(approvalProfileVersion);

        createApprovalSteps(approvalProfileVersion, approvalProfileRequestDto.getApprovalSteps());
        return approvalProfile;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.APPROVAL_PROFILE, operation = OperationType.CHANGE)
    @ExternalAuthorization(resource = Resource.APPROVAL_PROFILE, action = ResourceAction.UPDATE)
    public ApprovalProfile editApprovalProfile(final SecuredUUID uuid, final ApprovalProfileUpdateRequestDto approvalProfileUpdateRequestDto) throws NotFoundException {
        if (approvalProfileUpdateRequestDto.getExpiry() != null && approvalProfileUpdateRequestDto.getExpiry() <= 0) {
            throw new ValidationException("Expiry value (if set) should be greater than 0");
        }

        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        ApprovalProfileVersion latestVersion = approvalProfile.getTheLatestApprovalProfileVersion();
        if (approvalProfileVersionsEqual(latestVersion, approvalProfileUpdateRequestDto)) {
            logger.debug("Latest version of approval profile {} is same as in request. New version is not created", approvalProfile.getName());
            return approvalProfile;
        }

        // check existence of approvals, if no approvals associated update current latest version
        boolean createNewVersion = !latestVersion.getApprovals().isEmpty();
        if (createNewVersion) {
            latestVersion = latestVersion.createNewVersionObject();
            logger.debug("Creating new version of approval profile {} version {}.", approvalProfile.getName(), latestVersion.getVersion());
        } else {
            logger.debug("Updating latest version of approval profile {}.", approvalProfile.getName());

            for (ApprovalStep step : latestVersion.getApprovalSteps()) {
                approvalStepRepository.delete(step);
            }
            latestVersion.getApprovalSteps().clear();
        }

        latestVersion.setDescription(approvalProfileUpdateRequestDto.getDescription());
        latestVersion.setExpiry(approvalProfileUpdateRequestDto.getExpiry());

        approvalProfileVersionRepository.save(latestVersion);
        if (createNewVersion) approvalProfile.getApprovalProfileVersions().add(latestVersion);
        createApprovalSteps(latestVersion, approvalProfileUpdateRequestDto.getApprovalSteps());

        return latestVersion.getApprovalProfile();
    }

    private boolean approvalProfileVersionsEqual(ApprovalProfileVersion latestVersion, ApprovalProfileUpdateRequestDto request) {
        // check approval profile props
        if (latestVersion.getApprovalSteps().size() != request.getApprovalSteps().size() || !Objects.equals(latestVersion.getExpiry(), request.getExpiry()) || !StringUtils.equals(latestVersion.getDescription(), request.getDescription())) {
            return false;
        }

        // check approval profile steps
        List<ApprovalStep> actualSteps = latestVersion.getApprovalSteps();
        actualSteps.sort(Comparator.comparingInt(ApprovalStep::getOrder));
        for (int i = 0; i < actualSteps.size(); i++) {
            if (!Objects.equals(actualSteps.get(i).getUserUuid(), request.getApprovalSteps().get(i).getUserUuid())
                    || !Objects.equals(actualSteps.get(i).getGroupUuid(), request.getApprovalSteps().get(i).getGroupUuid())
                    || !Objects.equals(actualSteps.get(i).getRoleUuid(), request.getApprovalSteps().get(i).getRoleUuid())
                    || !StringUtils.equals(actualSteps.get(i).getDescription(), request.getApprovalSteps().get(i).getDescription())
                    || actualSteps.get(i).getRequiredApprovals() != request.getApprovalSteps().get(i).getRequiredApprovals()) {
                return false;
            }
        }

        return true;
    }

    private void createApprovalSteps(final ApprovalProfileVersion approvalProfileVersion, final List<ApprovalStepRequestDto> approvalStepDtos) throws ValidationException {
        if (approvalStepDtos == null || approvalStepDtos.isEmpty()) {
            throw new ValidationException("Unable to process approval profile without approval steps.");
        }

        approvalStepDtos.forEach(as -> {
            if (as.getRequiredApprovals() <= 0) {
                throw new ValidationException("Required approvals value should be greater than 0");
            }
            validateAssignedPersons(as);

            final ApprovalStep approvalStep = new ApprovalStep();
            approvalStep.setApprovalProfileVersion(approvalProfileVersion);
            approvalStep.setApprovalProfileVersionUuid(approvalProfileVersion.getUuid());
            approvalStep.setOrder(as.getOrder());
            approvalStep.setGroupUuid(as.getGroupUuid());
            approvalStep.setUserUuid(as.getUserUuid());
            approvalStep.setRoleUuid(as.getRoleUuid());
            approvalStep.setDescription(as.getDescription());
            approvalStep.setRequiredApprovals(as.getRequiredApprovals());
            approvalStepRepository.save(approvalStep);
            approvalProfileVersion.getApprovalSteps().add(approvalStep);
        });
        approvalProfileVersionRepository.save(approvalProfileVersion);
    }

    private void validateAssignedPersons(final ApprovalStepRequestDto as) {
        boolean isAssignedResponsibleUser = false;
        if (as.getRoleUuid() != null) {
            isAssignedResponsibleUser = true;
        }
        if (as.getUserUuid() != null) {
            if (isAssignedResponsibleUser) {
                throw new ValidationException(ValidationError.create("There is forbidden to have more than one assigned user/role/group."));
            }
            isAssignedResponsibleUser = true;
        }
        if (as.getGroupUuid() != null) {
            if (isAssignedResponsibleUser) {
                throw new ValidationException(ValidationError.create("There is forbidden to have more than one assigned user/role/group."));
            }
            isAssignedResponsibleUser = true;
        }

        if (!isAssignedResponsibleUser) {
            throw new ValidationException(ValidationError.create("There is required to have assigned one of user/role/group."));
        }
    }

    private ApprovalProfile findApprovalProfileByUuid(final SecuredUUID uuid) throws NotFoundException {
        final Optional<ApprovalProfile> approvalProfileOptional = approvalProfileRepository.findByUuid(uuid);
        if (approvalProfileOptional.isPresent()) {
            return approvalProfileOptional.get();
        }
        throw new NotFoundException("Unable to find approval profile with UUID: {}", uuid);
    }

    private void enableDisableApprovalProfile(final ApprovalProfile approvalProfile, final boolean enable) throws ValidationException {
        if (approvalProfile.isEnabled() == enable) {
            throw new ValidationException("Approval profile is already " + (enable ? "enabled" : "disabled"));
        }
        approvalProfile.setEnabled(enable);
        approvalProfileRepository.save(approvalProfile);
    }


    // SETTERs

    @Autowired
    public void setApprovalProfileRepository(ApprovalProfileRepository approvalProfileRepository) {
        this.approvalProfileRepository = approvalProfileRepository;
    }

    @Autowired
    public void setApprovalStepRepository(ApprovalStepRepository approvalStepRepository) {
        this.approvalStepRepository = approvalStepRepository;
    }

    @Autowired
    public void setApprovalProfileVersionRepository(ApprovalProfileVersionRepository approvalProfileVersionRepository) {
        this.approvalProfileVersionRepository = approvalProfileVersionRepository;
    }

    @Autowired
    public void setApprovalRecipientHelper(ApprovalRecipientHelper approvalRecipientHelper) {
        this.approvalRecipientHelper = approvalRecipientHelper;
    }
}
