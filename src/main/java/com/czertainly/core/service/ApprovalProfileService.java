package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.SchedulerException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDetailDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileRequestDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileResponseDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileUpdateRequestDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.security.authz.SecurityFilter;

import java.io.IOException;
import java.security.cert.CertificateException;

public interface ApprovalProfileService {

    ApprovalProfileResponseDto listApprovalProfiles(final SecurityFilter securityFilter, final PaginationRequestDto paginationRequestDto) throws ValidationException;

    ApprovalProfileDetailDto getApprovalProfile(String uuid) throws NotFoundException, CertificateException, IOException;

    void deleteApprovalProfile(String uuid) throws NotFoundException;

    void enableApprovalProfile(String uuid) throws NotFoundException, SchedulerException;

    void disableApprovalProfile(String uuid) throws NotFoundException, SchedulerException;

    ApprovalProfile createApprovalProfile(ApprovalProfileRequestDto approvalProfileRequestDto) throws NotFoundException, AlreadyExistException;

    ApprovalProfile editApprovalProfile(String uuid, ApprovalProfileUpdateRequestDto approvalProfileUpdateRequestDto) throws NotFoundException;

}
