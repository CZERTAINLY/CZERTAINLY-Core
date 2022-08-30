package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerBulkUpdateDto;
import com.czertainly.api.model.client.certificate.owner.CertificateOwnerRequestDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public interface CertificateService {

    CertificateResponseDto listCertificates(SecurityFilter filter, SearchRequestDto request) throws ValidationException;
    
    CertificateDto getCertificate(SecuredUUID uuid) throws NotFoundException;
    Certificate getCertificateEntity(SecuredUUID uuid) throws NotFoundException;
    // TODO AUTH - unable to check access based on certificate content. Make private? Special permission? Call opa in method?
    Certificate getCertificateEntityByContent(String content);

    // TODO AUTH - unable to check access based on certificate serial number. Make private? Special permission? Call opa in method?
    Certificate getCertificateEntityBySerial(String serialNumber) throws NotFoundException;

    void deleteCertificate(SecuredUUID uuid) throws NotFoundException;
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

    // TODO AUTH - unable to check access based on certificate serial number. Make private? Special permission? Call opa in method?
    void revokeCertificate(String serialNumber);

    List<SearchFieldDataDto> getSearchableFieldInformation();
    void bulkDeleteCertificate(RemoveCertificateDto request) throws NotFoundException;

    /**
     * List all locations associated with the certificate
     * @param certificateUuid
     * @return List of locations
     * @throws NotFoundException
     */
    List<LocationDto> listLocations(SecuredUUID certificateUuid) throws NotFoundException;

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

    /**
     * Update the Certificate Objects contents
     * @param uuid UUID of the certificate
     * @param request Request for the certificate objects update
     */
    void updateCertificateObjects(SecuredUUID uuid, CertificateUpdateObjectsDto request) throws NotFoundException;

    /**
     * Method to update the Objects of multiple certificates
     * @param request Request to update multiple objects
     */
    void bulkUpdateCertificateObjects(MultipleCertificateObjectUpdateDto request) throws NotFoundException;
}
