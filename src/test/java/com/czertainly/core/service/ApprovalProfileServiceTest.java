package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.SchedulerException;
import com.czertainly.api.model.client.approvalprofile.*;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.dao.entity.ApprovalProfileVersion;
import com.czertainly.core.dao.repository.ApprovalProfileRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.UUID;

public class ApprovalProfileServiceTest extends BaseSpringBootTest {

    @Autowired
    private ApprovalProfileService approvalProfileService;

    @Autowired
    private ApprovalProfileRepository approvalProfileRepository;

    private static final ApprovalProfileRequestDto approvalProfileRequestDto = new ApprovalProfileRequestDto();

    private static final ApprovalProfileUpdateRequestDto approvalProfileUpdateRequestDto = new ApprovalProfileUpdateRequestDto();

    @BeforeAll
    public static void setUp() {

        final ApprovalStepDto approvalStepDto = new ApprovalStepDto();
        approvalStepDto.setOrder(2);
        approvalStepDto.setUserUuid(UUID.randomUUID());
        approvalStepDto.setDescription("approval-step-1");
        approvalStepDto.setRequiredApprovals(2);

        final ApprovalStepDto approvalStepDto2 = new ApprovalStepDto();
        approvalStepDto2.setOrder(5);
        approvalStepDto2.setRoleUuid(UUID.randomUUID());
        approvalStepDto2.setDescription("approval-step-2");
        approvalStepDto2.setRequiredApprovals(3);

        approvalProfileRequestDto.setName("test-approvalProfile");
        approvalProfileRequestDto.setDescription("test_description");
        approvalProfileRequestDto.setEnabled(true);
        approvalProfileRequestDto.setExpiry(10);
        approvalProfileRequestDto.getApprovalSteps().add(approvalStepDto);
        approvalProfileRequestDto.getApprovalSteps().add(approvalStepDto2);

        final ApprovalStepDto approvalStepDto3 = new ApprovalStepDto();
        approvalStepDto3.setOrder(10);
        approvalStepDto3.setRoleUuid(UUID.randomUUID());
        approvalStepDto3.setDescription("approval-step-extra");
        approvalStepDto3.setRequiredApprovals(10);

        approvalProfileUpdateRequestDto.setExpiry(20);
        approvalProfileUpdateRequestDto.setDescription("test_description_updated");
        approvalProfileUpdateRequestDto.getApprovalSteps().add(approvalStepDto);
        approvalProfileUpdateRequestDto.getApprovalSteps().add(approvalStepDto2);
        approvalProfileUpdateRequestDto.getApprovalSteps().add(approvalStepDto3);

    }

    @Test
    public void testCreationOfApprovalProfile() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);

        final Optional<ApprovalProfile> approvalProfileOptional = approvalProfileRepository.findByUuid(approvalProfile.getSecuredUuid());
        Assertions.assertTrue(approvalProfileOptional.isPresent());

        final ApprovalProfile approvalProfileDB = approvalProfileOptional.get();
        Assertions.assertEquals(1, approvalProfileDB.getApprovalProfileVersions().size());
        Assertions.assertTrue(approvalProfileDB.isEnabled());

        final ApprovalProfileVersion approvalProfileVersion = approvalProfileDB.getTheLatestApprovalProfileVersion();
        Assertions.assertEquals(1, approvalProfileVersion.getVersion());
        Assertions.assertEquals(10, approvalProfileVersion.getExpiry());
        Assertions.assertEquals(2, approvalProfileVersion.getApprovalSteps().size());
    }

    @Test
    public void testEditApprovalProfile() throws NotFoundException, AlreadyExistException {
        ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        approvalProfile = approvalProfileService.editApprovalProfile(approvalProfile.getUuid().toString(), approvalProfileUpdateRequestDto);
        approvalProfile = approvalProfileService.editApprovalProfile(approvalProfile.getUuid().toString(), approvalProfileUpdateRequestDto);

        final Optional<ApprovalProfile> approvalProfileOptional = approvalProfileRepository.findByUuid(approvalProfile.getSecuredUuid());
        Assertions.assertTrue(approvalProfileOptional.isPresent());

        final ApprovalProfile approvalProfileDB = approvalProfileOptional.get();

        Assertions.assertEquals(3, approvalProfileDB.getApprovalProfileVersions().size());
        Assertions.assertTrue(approvalProfileDB.isEnabled());

        final ApprovalProfileVersion approvalProfileVersion = approvalProfileDB.getTheLatestApprovalProfileVersion();
        Assertions.assertEquals(3, approvalProfileVersion.getVersion());
        Assertions.assertEquals(20, approvalProfileVersion.getExpiry());
        Assertions.assertEquals(3, approvalProfileVersion.getApprovalSteps().size());
    }

    @Test
    public void testDisableEnableApprovalProfile() throws NotFoundException, AlreadyExistException, SchedulerException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        Assertions.assertTrue(approvalProfile.isEnabled());

        approvalProfileService.disableApprovalProfile(approvalProfile.getUuid().toString());

        final Optional<ApprovalProfile> approvalProfileDBOptional = approvalProfileRepository.findByUuid(SecuredUUID.fromUUID(approvalProfile.getUuid()));
        final ApprovalProfile approvalProfileDB = approvalProfileDBOptional.get();

        Assertions.assertFalse(approvalProfileDB.isEnabled());

        approvalProfileService.enableApprovalProfile(approvalProfile.getUuid().toString());

        final Optional<ApprovalProfile> approvalProfile2DBOptional = approvalProfileRepository.findByUuid(SecuredUUID.fromUUID(approvalProfile.getUuid()));
        final ApprovalProfile approvalProfile2DB = approvalProfile2DBOptional.get();

        Assertions.assertTrue(approvalProfile2DB.isEnabled());
    }

    @Test
    public void testDeleteApprovalProfile() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        Assertions.assertTrue(approvalProfile.isEnabled());

        approvalProfileService.deleteApprovalProfile(approvalProfile.getUuid().toString());

        final Optional<ApprovalProfile> approvalProfileDBOptional = approvalProfileRepository.findByUuid(SecuredUUID.fromUUID(approvalProfile.getUuid()));
        Assertions.assertFalse(approvalProfileDBOptional.isPresent());
    }

    @Test
    public void testListApprovalProfiles() throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        approvalProfileService.editApprovalProfile(approvalProfile.getUuid().toString(), approvalProfileUpdateRequestDto);
        approvalProfileService.editApprovalProfile(approvalProfile.getUuid().toString(), approvalProfileUpdateRequestDto);

        Assertions.assertTrue(approvalProfile.isEnabled());

        ApprovalProfileResponseDto approvalProfileResponseDto = approvalProfileService.listApprovalProfiles(SecurityFilter.create(), new PaginationRequestDto());
        Assertions.assertEquals(1, approvalProfileResponseDto.getApprovalProfiles().size());
    }

    @Test
    public void testListApprovalProfileDetail() throws NotFoundException, AlreadyExistException, CertificateException, IOException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        final ApprovalProfileDetailDto approvalProfileDetailDto = approvalProfileService.getApprovalProfile(approvalProfile.getUuid().toString());

        Assertions.assertEquals(approvalProfile.getName(), approvalProfileDetailDto.getName());
        Assertions.assertEquals(approvalProfile.getTheLatestApprovalProfileVersion().getDescription(), approvalProfileDetailDto.getDescription());
        Assertions.assertEquals(approvalProfile.getTheLatestApprovalProfileVersion().getVersion(), approvalProfileDetailDto.getVersion());
        Assertions.assertEquals(approvalProfile.getTheLatestApprovalProfileVersion().getExpiry(), approvalProfileDetailDto.getExpiry());
        Assertions.assertEquals(2, approvalProfileDetailDto.getApprovalSteps().size());
    }

}
