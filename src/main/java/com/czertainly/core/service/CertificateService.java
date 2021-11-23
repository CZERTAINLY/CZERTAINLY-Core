package com.czertainly.core.service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import com.czertainly.api.core.modal.*;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.discovery.CertificateDto;

public interface CertificateService {

    List<CertificateDto> listCertificates(Integer start, Integer end);
    
    CertificateDto getCertificate(String uuid) throws NotFoundException;
    
    Certificate getCertificateEntity(String uuid) throws NotFoundException;
    
    Certificate getCertificateEntityBySerial(String serialNumber) throws NotFoundException;
    
    void removeCertificate(String uuid) throws NotFoundException;

	void updateIssuer();

	Certificate createCertificateEntity(X509Certificate certificate);
	Certificate checkCreateCertificate(X509Certificate certificate) throws AlreadyExistException;
	Certificate checkCreateCertificate(String certificate) throws AlreadyExistException, CertificateException;
	
	Certificate saveCertificateEntity(String cert) throws CertificateException;
	
	CertificateDto upload(UploadCertificateRequestDto request) throws AlreadyExistException, CertificateException;
	
	void revokeCertificate(String serialNumber);
	
	void updateRaProfile(String uuid, UuidDto request) throws NotFoundException;
    void updateCertificateGroup(String uuid, UuidDto request) throws NotFoundException;
    void updateEntity(String uuid, UuidDto request) throws NotFoundException;
    void updateOwner(String uuid, CertificateOwnerRequestDto request) throws NotFoundException;
    
    void bulkUpdateRaProfile(IdAndCertificateIdDto request) throws NotFoundException;
    void bulkUpdateCertificateGroup(IdAndCertificateIdDto request) throws NotFoundException;
    void bulkUpdateEntity(IdAndCertificateIdDto request) throws NotFoundException;
    void bulkUpdateOwner(CertificateOwnerBulkUpdateDto request) throws NotFoundException;
    void bulkRemoveCertificate(RemoveCertificateDto request) throws NotFoundException;
}
