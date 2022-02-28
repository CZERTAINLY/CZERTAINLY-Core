package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerBulkUpdateDto;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerRequestDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.Certificate;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public interface CertificateService {

    CertificateResponseDto listCertificates(CertificateSearchRequestDto request) throws ValidationException;
    
    CertificateDto getCertificate(String uuid) throws NotFoundException;
    
    Certificate getCertificateEntity(String uuid) throws NotFoundException;
    Certificate getCertificateEntityByContent(String content);

    Certificate getCertificateEntityBySerial(String serialNumber) throws NotFoundException;
    
    void removeCertificate(String uuid) throws NotFoundException;

	void updateIssuer();

	Certificate createCertificateEntity(X509Certificate certificate);
	Certificate checkCreateCertificate(X509Certificate certificate) throws AlreadyExistException;
	Certificate checkCreateCertificate(String certificate) throws AlreadyExistException, CertificateException;
	
	Certificate saveCertificateEntity(String cert) throws CertificateException;
	
	CertificateDto upload(UploadCertificateRequestDto request) throws AlreadyExistException, CertificateException;
	
	void revokeCertificate(String serialNumber);
	
	void updateRaProfile(String uuid, CertificateUpdateRAProfileDto request) throws NotFoundException;
    void updateCertificateGroup(String uuid, CertificateUpdateGroupDto request) throws NotFoundException;
    void updateEntity(String uuid, CertificateUpdateEntityDto request) throws NotFoundException;
    void updateOwner(String uuid, CertificateOwnerRequestDto request) throws NotFoundException;
    
    void bulkUpdateRaProfile(MultipleRAProfileUpdateDto request) throws NotFoundException;
    void bulkUpdateCertificateGroup(MultipleGroupUpdateDto request) throws NotFoundException;
    void bulkUpdateEntity(MultipleEntityUpdateDto request) throws NotFoundException;
    void bulkUpdateOwner(CertificateOwnerBulkUpdateDto request) throws NotFoundException;
    List<String> bulkRemoveCertificate(RemoveCertificateDto request) throws NotFoundException;

    List<SearchFieldDataDto> getSearchableFieldInformation();
}
