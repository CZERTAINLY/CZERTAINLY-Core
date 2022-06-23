package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerBulkUpdateDto;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerRequestDto;
import com.czertainly.api.model.core.certificate.CertificateComplianceStorageDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public interface CertificateService {

    CertificateResponseDto listCertificates(SearchRequestDto request) throws ValidationException;
    
    CertificateDto getCertificate(String uuid) throws NotFoundException;
    Certificate getCertificateEntity(String uuid) throws NotFoundException;
    Certificate getCertificateEntityByContent(String content);
    Certificate getCertificateEntityBySerial(String serialNumber) throws NotFoundException;

    void removeCertificate(String uuid) throws NotFoundException;
	void updateIssuer();
	Certificate createCertificateEntity(X509Certificate certificate);

    /**
     * Creates the Certificate entity
     *
     * @param certificateData  Base64-encoded data of the certificate
     * @param certificateType  Type of the certificate
     * @return Certificate entity
     */
    Certificate createCertificate(String certificateData, CertificateType certificateType) throws com.czertainly.api.exception.CertificateException;

	Certificate checkCreateCertificate(String certificate) throws AlreadyExistException, CertificateException;
    Certificate checkCreateCertificateWithMeta(String certificate, String meta) throws AlreadyExistException, CertificateException;
	CertificateDto upload(UploadCertificateRequestDto request) throws AlreadyExistException, CertificateException;
	void revokeCertificate(String serialNumber);
	
	void updateRaProfile(String uuid, CertificateUpdateRAProfileDto request) throws NotFoundException;
    void updateCertificateGroup(String uuid, CertificateUpdateGroupDto request) throws NotFoundException;

    void updateOwner(String uuid, CertificateOwnerRequestDto request) throws NotFoundException;

    void bulkUpdateRaProfile(MultipleRAProfileUpdateDto request) throws NotFoundException;
    void bulkUpdateCertificateGroup(MultipleGroupUpdateDto request) throws NotFoundException;

    void bulkUpdateOwner(CertificateOwnerBulkUpdateDto request) throws NotFoundException;

    List<SearchFieldDataDto> getSearchableFieldInformation();
    void bulkRemoveCertificate(RemoveCertificateDto request) throws NotFoundException;

    /**
     * List all locations associated with the certificate
     * @param certificateUuid
     * @return List of locations
     * @throws NotFoundException
     */
    List<LocationDto> listLocations(String certificateUuid) throws NotFoundException;

    /**
     * Updates the Certificate with compliance status and compliance result
     * @param uuid Uuid of the Certificate
     * @param complianceStatus Overall status of the Compliance check
     * @param result List of compliance responses from the connectors associated with the compliance profile
     * @throws NotFoundException Thrown when the system is unable to find the certificate with the given Uuid
     */
    void updateComplianceReport(String uuid, ComplianceStatus complianceStatus,
                                CertificateComplianceStorageDto result) throws NotFoundException;

    /**
     * List the available certificates that are associated with the RA Profile
     * @param raProfile Ra Profile entity to search for the certificates
     * @return List of Certificates
     */
    List<Certificate> listCertificatesForRaProfile(RaProfile raProfile);

    /**
     * Initiates the compliance check for the certificates in the request
     * @param request List of uuids of the certificate
     */
    void checkCompliance(CertificateComplianceCheckDto request);

    /**
     * Update the Certificate Entity
     * @param certificate Certificate entity to be updated
     */
    void updateCertificateEntity(Certificate certificate);
}
