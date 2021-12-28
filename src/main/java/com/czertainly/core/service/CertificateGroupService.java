package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.group.CertificateGroupDto;
import com.czertainly.api.model.core.certificate.group.CertificateGroupRequestDto;

import java.util.List;

public interface CertificateGroupService {

    List<CertificateGroupDto> listCertificateGroups();
    CertificateGroupDto getCertificateGroup(String uuid) throws NotFoundException;

    CertificateGroupDto createCertificateGroup(CertificateGroupRequestDto request) throws ValidationException, AlreadyExistException;
    CertificateGroupDto updateCertificateGroup(String uuid, CertificateGroupRequestDto request) throws NotFoundException;

    void removeCertificateGroup(String uuid) throws NotFoundException;
    void bulkRemoveCertificateGroup(List<String> groupUuids);
}
