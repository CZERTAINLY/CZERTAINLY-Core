package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileDetailDto;
import com.czertainly.api.model.client.approvalprofile.ApprovalProfileResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.dao.repository.ApprovalProfileRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

class ApprovalProfileServiceTest extends ApprovalProfileData {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalProfileService approvalProfileService;

    @Autowired
    private ApprovalProfileRepository approvalProfileRepository;


    @Test
    void testCreationOfApprovalProfile() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);

        final Optional<ApprovalProfile> approvalProfileOptional = approvalProfileRepository.findWithVersionsByUuid(approvalProfile.getUuid());
        Assertions.assertTrue(approvalProfileOptional.isPresent());

        final ApprovalProfile approvalProfileDB = approvalProfileOptional.get();
        Assertions.assertEquals(1, approvalProfileDB.getApprovalProfileVersions().size());

        final ApprovalProfileDetailDto approvalProfileVersion = approvalProfileService.getApprovalProfile(approvalProfile.getSecuredUuid(), null);
        Assertions.assertEquals(1, approvalProfileVersion.getVersion());
        Assertions.assertEquals(10, approvalProfileVersion.getExpiry());
        Assertions.assertEquals(2, approvalProfileVersion.getApprovalSteps().size());
    }

    @Test
    void testEditApprovalProfile() throws NotFoundException, AlreadyExistException {
        ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        approvalProfile = approvalProfileService.editApprovalProfile(approvalProfile.getSecuredUuid(), approvalProfileUpdateRequestDto);

        approvalProfileUpdateRequestDto.setExpiry(24);

        approvalService.createApproval(approvalProfile.getTheLatestApprovalProfileVersion(), Resource.CERTIFICATE, ResourceAction.CREATE, UUID.randomUUID(), UUID.randomUUID(), null);
        approvalProfile = approvalProfileService.editApprovalProfile(approvalProfile.getSecuredUuid(), approvalProfileUpdateRequestDto);

        final Optional<ApprovalProfile> approvalProfileOptional = approvalProfileRepository.findWithVersionsByUuid(approvalProfile.getUuid());
        Assertions.assertTrue(approvalProfileOptional.isPresent());

        final ApprovalProfile approvalProfileDB = approvalProfileOptional.get();

        Assertions.assertEquals(2, approvalProfileDB.getApprovalProfileVersions().size());

        final ApprovalProfileDetailDto approvalProfileVersion = approvalProfileService.getApprovalProfile(approvalProfile.getSecuredUuid(), null);
        Assertions.assertEquals(2, approvalProfileVersion.getVersion());
        Assertions.assertEquals(24, approvalProfileVersion.getExpiry());
        Assertions.assertEquals(3, approvalProfileVersion.getApprovalSteps().size());
    }

    @Test
    void testDeleteApprovalProfile() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        approvalProfileService.deleteApprovalProfile(approvalProfile.getSecuredUuid());

        final Optional<ApprovalProfile> approvalProfileDBOptional = approvalProfileRepository.findByUuid(SecuredUUID.fromUUID(approvalProfile.getUuid()));
        Assertions.assertFalse(approvalProfileDBOptional.isPresent());
    }

    @Test
    void testListApprovalProfiles() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        approvalProfileService.editApprovalProfile(approvalProfile.getSecuredUuid(), approvalProfileUpdateRequestDto);
        approvalProfileService.editApprovalProfile(approvalProfile.getSecuredUuid(), approvalProfileUpdateRequestDto);

        ApprovalProfileResponseDto approvalProfileResponseDto = approvalProfileService.listApprovalProfiles(SecurityFilter.create(), new PaginationRequestDto());
        Assertions.assertEquals(1, approvalProfileResponseDto.getApprovalProfiles().size());
    }

    @Test
    void testListApprovalProfileDetail() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        final ApprovalProfileDetailDto approvalProfileDetailDto = approvalProfileService.getApprovalProfile(approvalProfile.getSecuredUuid(), null);

        Assertions.assertEquals(approvalProfile.getName(), approvalProfileDetailDto.getName());
        Assertions.assertEquals(approvalProfile.getTheLatestApprovalProfileVersion().getDescription(), approvalProfileDetailDto.getDescription());
        Assertions.assertEquals(approvalProfile.getTheLatestApprovalProfileVersion().getVersion(), approvalProfileDetailDto.getVersion());
        Assertions.assertEquals(approvalProfile.getTheLatestApprovalProfileVersion().getExpiry(), approvalProfileDetailDto.getExpiry());
        Assertions.assertEquals(2, approvalProfileDetailDto.getApprovalSteps().size());
    }

}
