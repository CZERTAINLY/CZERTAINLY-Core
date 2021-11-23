package com.czertainly.core.service;

import java.util.List;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.discovery.CertificateEntityDto;

public interface CertificateEntityService {

    List<CertificateEntityDto> listCertificateEntity();
    CertificateEntityDto getCertificateEntity(String uuid) throws NotFoundException;

    CertificateEntityDto createCertificateEntity(CertificateEntityDto request) throws ValidationException, AlreadyExistException;
    CertificateEntityDto updateCertificateEntity(String uuid, CertificateEntityDto request) throws NotFoundException;

    void removeCertificateEntity(String uuid) throws NotFoundException;
    void bulkRemoveCertificateEntity(List<String> entityUuids);
}
