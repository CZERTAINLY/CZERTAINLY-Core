package com.czertainly.core.service.impl;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.*;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.dao.entity.ApprovalProfileVersion;
import com.czertainly.core.dao.entity.ApprovalStep;
import com.czertainly.core.dao.repository.ApprovalProfileRepository;
import com.czertainly.core.dao.repository.ApprovalProfileVersionRepository;
import com.czertainly.core.dao.repository.ApprovalStepRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalProfileService;
import com.czertainly.core.util.RequestValidatorHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ApprovalProfileServiceImpl implements ApprovalProfileService {

    private ApprovalProfileRepository approvalProfileRepository;

    private ApprovalStepRepository approvalStepRepository;

    private ApprovalProfileVersionRepository approvalProfileVersionRepository;

    @Override
    public ApprovalProfileResponseDto listApprovalProfiles(final SecurityFilter filter, final PaginationRequestDto paginationRequestDto) {
        RequestValidatorHelper.revalidatePaginationRequestDto(paginationRequestDto);
        final Pageable pageable = PageRequest.of(paginationRequestDto.getPageNumber() - 1, paginationRequestDto.getItemsPerPage());
        final List<ApprovalProfile> approvalProfileList = approvalProfileRepository.findUsingSecurityFilter(filter, null, pageable, null);

        final Long maxItems = approvalProfileRepository.countUsingSecurityFilter(filter, null);
        final ApprovalProfileResponseDto responseDto = new ApprovalProfileResponseDto();
        responseDto.setApprovalProfiles(approvalProfileList.stream()
                .map(approvalProfile -> approvalProfile.getTheLatestApprovalProfileVersion().mapToDto())
                .collect(Collectors.toList()));
        responseDto.setItemsPerPage(paginationRequestDto.getItemsPerPage());
        responseDto.setPageNumber(paginationRequestDto.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / paginationRequestDto.getItemsPerPage()));
        return responseDto;
    }

    @Override
    public ApprovalProfileDetailDto getApprovalProfile(final String uuid, final Integer version) throws NotFoundException {
        if (version == null) {
            return findApprovalProfileByUuid(uuid).getTheLatestApprovalProfileVersion().mapToDtoWithSteps();
        } else {
            return findApprovalProfileByUuid(uuid).getApprovalProfileVersionByVersion(version).mapToDtoWithSteps();
        }
    }

    @Override
    public void deleteApprovalProfile(final String uuid) throws NotFoundException, ValidationException {
        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        if (approvalProfile.getApprovalProfileVersions().stream().filter(apv -> (apv.getApprovals() != null && apv.getApprovals().size() > 0)).findFirst().isPresent()) {
            throw new ValidationException(ValidationError.create("Unable to delete approval profile with existing approvals."));
        }
        approvalProfile.getApprovalProfileVersions().stream().forEach(apv -> {
            approvalStepRepository.deleteAll(apv.getApprovalSteps());
            approvalProfileVersionRepository.delete(apv);
        });
        approvalProfileRepository.delete(approvalProfile);
    }

    @Override
    public void enableApprovalProfile(final String uuid) throws NotFoundException, ValidationException {
        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        enableDisableApprovalProfile(approvalProfile, true);
    }

    @Override
    public void disableApprovalProfile(final String uuid) throws NotFoundException, ValidationException {
        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        enableDisableApprovalProfile(approvalProfile, false);
    }

    @Override
    public ApprovalProfile createApprovalProfile(final ApprovalProfileRequestDto approvalProfileRequestDto) throws NotFoundException, AlreadyExistException {

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
    public ApprovalProfile editApprovalProfile(final String uuid, final ApprovalProfileUpdateRequestDto approvalProfileUpdateRequestDto) throws NotFoundException {
        final ApprovalProfile approvalProfile = findApprovalProfileByUuid(uuid);
        final ApprovalProfileVersion approvalProfileVersionNew
                = approvalProfile.getTheLatestApprovalProfileVersion().createNewVersionObject();

        if (approvalProfileUpdateRequestDto.getDescription() != null) {
            approvalProfileVersionNew.setDescription(approvalProfileUpdateRequestDto.getDescription());
        }

        if (approvalProfileUpdateRequestDto.getExpiry() != null) {
            approvalProfileVersionNew.setExpiry(approvalProfileUpdateRequestDto.getExpiry());
        }

        approvalProfileVersionRepository.save(approvalProfileVersionNew);
        approvalProfile.getApprovalProfileVersions().add(approvalProfileVersionNew);
        createApprovalSteps(approvalProfileVersionNew, approvalProfileUpdateRequestDto.getApprovalSteps());
        return approvalProfileVersionNew.getApprovalProfile();
    }

    private void createApprovalSteps(final ApprovalProfileVersion approvalProfileVersion, final List<ApprovalStepDto> approvalStepDtos) throws NotFoundException {
        if (approvalStepDtos == null || approvalStepDtos.isEmpty()) {
            throw new NotFoundException("Unable to process approval profile without approval steps.");
        }

        approvalStepDtos.forEach(as -> {
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

    private void validateAssignedPersons(final ApprovalStepDto as) {
        boolean isAssignedResponsibleUser = false;
        if (as.getRoleUuid() != null) {
            isAssignedResponsibleUser = true;
        }
        if (as.getUserUuid() != null) {
            if (isAssignedResponsibleUser) {
                throw new ValidationException("There is forbidden to have more than one assigned user/role/group.");
            }
            isAssignedResponsibleUser = true;
        }
        if (as.getGroupUuid() != null) {
            if (isAssignedResponsibleUser) {
                throw new ValidationException("There is forbidden to have more than one assigned user/role/group.");
            }
            isAssignedResponsibleUser = true;
        }

        if (!isAssignedResponsibleUser) {
            throw new ValidationException("There is required to have assigned one of user/role/group.");
        }
    }

    private ApprovalProfile findApprovalProfileByUuid(final String uuid) throws NotFoundException {
        final Optional<ApprovalProfile> approvalProfileOptional = approvalProfileRepository.findByUuid(SecuredUUID.fromString(uuid));
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
}
