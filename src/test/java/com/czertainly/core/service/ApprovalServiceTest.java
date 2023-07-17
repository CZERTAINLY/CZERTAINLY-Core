package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approval.ApprovalDetailDto;
import com.czertainly.api.model.client.approval.ApprovalResponseDto;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDetailDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.Approval;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.dao.repository.ApprovalProfileRepository;
import com.czertainly.core.dao.repository.ApprovalRecipientRepository;
import com.czertainly.core.dao.repository.ApprovalRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

public class ApprovalServiceTest extends ApprovalProfileData {

    private ApprovalService approvalService;

    private ApprovalProfileService approvalProfileService;

    private ApprovalRepository approvalRepository;

    private Approval approval;
    private ApprovalProfile approvalProfile;

    @BeforeEach
    public void setUp() throws NotFoundException, AlreadyExistException {
        approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        approval = approvalService.createApproval(approvalProfile.getTheLatestApprovalProfileVersion(), Resource.CERTIFICATE, ResourceAction.CREATE, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testListOfApprovals() {
        approvalService.createApproval(approvalProfile.getTheLatestApprovalProfileVersion(), Resource.CERTIFICATE, ResourceAction.CREATE, UUID.randomUUID(), null);
        approvalService.createApproval(approvalProfile.getTheLatestApprovalProfileVersion(), Resource.CERTIFICATE, ResourceAction.CREATE, UUID.randomUUID(), null);
        approvalService.createApproval(approvalProfile.getTheLatestApprovalProfileVersion(), Resource.CERTIFICATE, ResourceAction.CREATE, UUID.randomUUID(), null);

        final ApprovalResponseDto responseDto = approvalService.listApprovals(SecurityFilter.create(), new PaginationRequestDto());
        Assertions.assertEquals(4, responseDto.getApprovals().size());
    }

    @Test
    public void testDetailOfApproval() throws NotFoundException {
        final ApprovalDetailDto approvalDetailDto = approvalService.getApprovalDetail(approval.getUuid().toString());

        Assertions.assertEquals(approvalProfileRequestDto.getExpiry(), approvalDetailDto.getExpiry());
        Assertions.assertEquals(approvalProfileRequestDto.getDescription(), approvalDetailDto.getDescription());
    }

    @Test
    public void testApprovalProfileHistoryVersion() throws NotFoundException {
        approvalService.getApprovalDetail(approval.getUuid().toString());
        approvalProfileService.editApprovalProfile(approvalProfile.getUuid().toString(), approvalProfileUpdateRequestDto);
        approvalProfileService.editApprovalProfile(approvalProfile.getUuid().toString(), approvalProfileUpdateRequestDto);

        final ApprovalProfileDetailDto approvalProfileDetailDto = approvalProfileService.getApprovalProfile(approvalProfile.getUuid().toString(), 1);
        Assertions.assertEquals(approvalProfileRequestDto.getDescription(), approvalProfileDetailDto.getDescription());
    }

    @Test
    public void testApproveApproval() throws NotFoundException {
        approvalService.approveApproval(approval.getUuid().toString());
        Optional<Approval> approvalOptional = approvalRepository.findByUuid(SecuredUUID.fromUUID(approval.getUuid()));

        Assertions.assertTrue(approvalOptional.isPresent());
        Assertions.assertEquals(ApprovalStatusEnum.APPROVED, approvalOptional.get().getStatus());
    }

    @Test
    public void testRejectApproval() throws NotFoundException {
        approvalService.rejectApproval(approval.getUuid().toString());
        Optional<Approval> approvalOptional = approvalRepository.findByUuid(SecuredUUID.fromUUID(approval.getUuid()));

        Assertions.assertTrue(approvalOptional.isPresent());
        Assertions.assertEquals(ApprovalStatusEnum.REJECTED, approvalOptional.get().getStatus());
    }

    // SETTERs

    @Autowired
    public void setApprovalRepository(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @Autowired
    public void setApprovalService(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Autowired
    public void setApprovalProfileService(ApprovalProfileService approvalProfileService) {
        this.approvalProfileService = approvalProfileService;
    }
}
