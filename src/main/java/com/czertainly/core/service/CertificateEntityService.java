package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.certificate.entity.CertificateEntityDto;
import com.czertainly.api.model.core.certificate.entity.CertificateEntityRequestDto;

import java.util.List;

public interface CertificateEntityService {

    List<CertificateEntityDto> listCertificateEntity();
    CertificateEntityDto getCertificateEntity(String uuid) throws NotFoundException;

    CertificateEntityDto createCertificateEntity(CertificateEntityRequestDto request) throws ValidationException, AlreadyExistException;
    CertificateEntityDto updateCertificateEntity(String uuid, CertificateEntityRequestDto request) throws NotFoundException;

    void removeCertificateEntity(String uuid) throws NotFoundException;
    void bulkRemoveCertificateEntity(List<String> entityUuids);
}
