package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.ApprovalController;
import com.czertainly.api.model.client.approval.ApprovalDetailDto;
import com.czertainly.api.model.client.approval.ApprovalResponseDto;
import com.czertainly.api.model.client.approval.ApprovalUserDto;
import com.czertainly.api.model.client.approval.UserApprovalDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApprovalControllerImpl implements ApprovalController {

    private ApprovalService approvalService;

    @Override
    public ApprovalResponseDto listApprovals(PaginationRequestDto paginationRequestDto) {
        return approvalService.listApprovals(SecurityFilter.create(), paginationRequestDto);
    }

    @Override
    public ApprovalResponseDto listUserApprovals(PaginationRequestDto paginationRequestDto, ApprovalUserDto approvalUserDto) throws ValidationException {
        return approvalService.listUserApprovals(SecurityFilter.create(), approvalUserDto.isHistory(), paginationRequestDto);
    }

    @Override
    public ApprovalDetailDto getApproval(final String uuid) throws NotFoundException {
        return approvalService.getApprovalDetail(uuid);
    }

    @Override
    public void approveApproval(final String uuid) throws NotFoundException {
        approvalService.approveApproval(uuid);
    }

    @Override
    public void rejectApproval(final String uuid) throws NotFoundException {
        approvalService.rejectApproval(uuid);
    }

    @Override
    public void approveApprovalRecipient(final String uuid, final UserApprovalDto userApprovalDto) throws NotFoundException {
        approvalService.approveApprovalRecipient(uuid, userApprovalDto);
    }

    @Override
    public void rejectApprovalRecipient(final String uuid, final UserApprovalDto userApprovalDto) throws NotFoundException {
        approvalService.rejectApprovalRecipient(uuid, userApprovalDto);
    }

    // SETTERs

    @Autowired
    public void setApprovalService(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }
}
