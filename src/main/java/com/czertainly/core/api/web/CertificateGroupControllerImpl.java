package com.czertainly.core.api.web;

import java.net.URI;
import java.util.List;

import com.czertainly.api.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.czertainly.core.service.CertificateGroupService;
import com.czertainly.api.core.interfaces.web.CertificateGroupController;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.discovery.CertificateGroupDto;

@RestController
public class CertificateGroupControllerImpl implements CertificateGroupController {

    @Autowired
    private CertificateGroupService certificateGroupService;

    @Override
    public List<CertificateGroupDto> listCertificateGroups() {
        return certificateGroupService.listCertificateGroups();
    }

    @Override
    public CertificateGroupDto getCertificateGroup(@PathVariable String uuid) throws NotFoundException {
        return certificateGroupService.getCertificateGroup(uuid);
    }

    @Override
    public ResponseEntity<?> createCertificateGroup(@RequestBody CertificateGroupDto request) throws ValidationException, AlreadyExistException {
        CertificateGroupDto groupDto = certificateGroupService.createCertificateGroup(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(groupDto.getUuid())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @Override
    public CertificateGroupDto updateCertificateGroup(@PathVariable String uuid, @RequestBody CertificateGroupDto request) throws NotFoundException {
        return certificateGroupService.updateCertificateGroup(uuid, request);
    }

    @Override
    public void removeCertificateGroup(@PathVariable String uuid) throws NotFoundException {
        certificateGroupService.removeCertificateGroup(uuid);
    }

    @Override
    public void bulkRemoveCertificateGroup(List<String> groupUuids) throws NotFoundException {
        certificateGroupService.bulkRemoveCertificateGroup(groupUuids);
    }
}
