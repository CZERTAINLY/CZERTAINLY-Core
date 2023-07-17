package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDetailDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileRequestDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileResponseDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileUpdateRequestDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.security.authz.SecurityFilter;

public interface ApprovalProfileService {

    ApprovalProfileResponseDto listApprovalProfiles(final SecurityFilter securityFilter, final PaginationRequestDto paginationRequestDto);

    ApprovalProfileDetailDto getApprovalProfile(String uuid, Integer version) throws NotFoundException;

    void deleteApprovalProfile(String uuid) throws NotFoundException, ValidationException;

    void enableApprovalProfile(String uuid) throws NotFoundException, ValidationException;

    void disableApprovalProfile(String uuid) throws NotFoundException, ValidationException;

    ApprovalProfile createApprovalProfile(ApprovalProfileRequestDto approvalProfileRequestDto) throws NotFoundException, AlreadyExistException;

    ApprovalProfile editApprovalProfile(String uuid, ApprovalProfileUpdateRequestDto approvalProfileUpdateRequestDto) throws NotFoundException;

}
