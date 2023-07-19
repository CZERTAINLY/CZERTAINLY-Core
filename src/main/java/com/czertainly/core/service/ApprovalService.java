package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approval.ApprovalDetailDto;
import com.czertainly.api.model.client.approval.ApprovalResponseDto;
import com.czertainly.api.model.client.approval.UserApprovalDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfileVersion;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.UUID;

public interface ApprovalService {

    ApprovalResponseDto listApprovals(final SecurityFilter securityFilter, final PaginationRequestDto paginationRequestDto);

    ApprovalResponseDto listApprovalsByObject(final SecurityFilter securityFilter, final Resource resource, final UUID objectUuid, final PaginationRequestDto paginationRequestDto);

    ApprovalResponseDto listUserApprovals(final SecurityFilter securityFilter, final boolean withHistory, final PaginationRequestDto paginationRequestDto);

    ApprovalDetailDto getApprovalDetail(final String uuid) throws NotFoundException;

    void approveApproval(final String uuid) throws NotFoundException;

    void rejectApproval(final String uuid) throws NotFoundException;

    void approveApprovalRecipient(final String uuid, final UserApprovalDto userApprovalDto) throws NotFoundException;

    void rejectApprovalRecipient(final String uuid, final UserApprovalDto userApprovalDto) throws NotFoundException;

    Approval createApproval(final ApprovalProfileVersion approvalProfileVersion, final Resource resource, final ResourceAction resourceAction, final UUID objectUuid, final UUID userUuid);


}
