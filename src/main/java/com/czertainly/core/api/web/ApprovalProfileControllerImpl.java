package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.SchedulerException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.ApprovalProfileController;
import com.czertainly.api.model.client.approvalprofile.*;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateException;

@RestController
public class ApprovalProfileControllerImpl implements ApprovalProfileController {

    private ApprovalProfileService approvalProfileService;

    @Override
    public ApprovalProfileResponseDto listApprovalProfiles(final PaginationRequestDto paginationRequestDto) throws ValidationException {
        return approvalProfileService.listApprovalProfiles(SecurityFilter.create(), paginationRequestDto);
    }

    @Override
    public ApprovalProfileDetailDto getApprovalProfile(final String uuid) throws NotFoundException, CertificateException, IOException {
        return approvalProfileService.getApprovalProfile(uuid);
    }

    @Override
    public void deleteApprovalProfile(final String uuid) throws NotFoundException {
        approvalProfileService.deleteApprovalProfile(uuid);
    }

    @Override
    public void enableApprovalProfile(final String uuid) throws NotFoundException, SchedulerException {
        approvalProfileService.enableApprovalProfile(uuid);
    }

    @Override
    public void disableApprovalProfile(final String uuid) throws NotFoundException, SchedulerException {
        approvalProfileService.disableApprovalProfile(uuid);
    }

    @Override
    public ResponseEntity<?> createApprovalProfile(final ApprovalProfileRequestDto approvalProfileRequestDto) throws NotFoundException, AlreadyExistException {
        final ApprovalProfile approvalProfile = approvalProfileService.createApprovalProfile(approvalProfileRequestDto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(approvalProfile.getUuid())
                .toUri();
        UuidDto responseDto = new UuidDto();
        responseDto.setUuid(approvalProfile.getUuid().toString());
        return ResponseEntity.created(location).body(responseDto);
    }

    @Override
    public ResponseEntity<?> editApprovalProfile(final String uuid, final ApprovalProfileUpdateRequestDto approvalProfileUpdateRequestDto) throws NotFoundException {
        final ApprovalProfile approvalProfile = approvalProfileService.editApprovalProfile(uuid, approvalProfileUpdateRequestDto);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(approvalProfile.getUuid())
                .toUri();
        UuidDto responseDto = new UuidDto();
        responseDto.setUuid(approvalProfile.getUuid().toString());
        return ResponseEntity.created(location).body(responseDto);
    }

    // SETTERs

    @Autowired
    public void setApprovalProfileService(ApprovalProfileService approvalProfileService) {
        this.approvalProfileService = approvalProfileService;
    }
}
