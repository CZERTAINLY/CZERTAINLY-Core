package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.interfaces.core.web.ApprovalProfileController;
import com.czertainly.api.model.client.approvalprofile.*;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.logging.enums.Module;
import com.czertainly.api.model.core.logging.enums.Operation;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.ApprovalProfile;
import com.czertainly.core.logging.LogResource;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.ApprovalProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class ApprovalProfileControllerImpl implements ApprovalProfileController {

    private ApprovalProfileService approvalProfileService;

    @Override
    @AuditLogged(module = Module.APPROVALS, resource = Resource.APPROVAL_PROFILE, operation = Operation.LIST)
    public ApprovalProfileResponseDto listApprovalProfiles(final PaginationRequestDto paginationRequestDto) {
        return approvalProfileService.listApprovalProfiles(SecurityFilter.create(), paginationRequestDto);
    }

    @Override
    @AuditLogged(module = Module.APPROVALS, resource = Resource.APPROVAL_PROFILE, operation = Operation.DETAIL)
    public ApprovalProfileDetailDto getApprovalProfile(@LogResource(uuid = true) final String uuid, final ApprovalProfileForVersionDto approvalProfileForVersionDto) throws NotFoundException {
        return approvalProfileService.getApprovalProfile(SecuredUUID.fromString(uuid), approvalProfileForVersionDto.getVersion());
    }

    @Override
    @AuditLogged(module = Module.APPROVALS, resource = Resource.APPROVAL_PROFILE, operation = Operation.DELETE)
    public void deleteApprovalProfile(@LogResource(uuid = true) final String uuid) throws NotFoundException, ValidationException {
        approvalProfileService.deleteApprovalProfile(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.APPROVALS, resource = Resource.APPROVAL_PROFILE, operation = Operation.CREATE)
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
    @AuditLogged(module = Module.APPROVALS, resource = Resource.APPROVAL_PROFILE, operation = Operation.UPDATE)
    public ResponseEntity<?> editApprovalProfile(@LogResource(uuid = true) final String uuid, final ApprovalProfileUpdateRequestDto approvalProfileUpdateRequestDto) throws NotFoundException {
        final ApprovalProfile approvalProfile = approvalProfileService.editApprovalProfile(SecuredUUID.fromString(uuid), approvalProfileUpdateRequestDto);
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
