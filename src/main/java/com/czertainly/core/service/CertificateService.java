package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.*;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.MetadataAttribute;
import com.czertainly.api.model.core.certificate.*;
import com.czertainly.api.model.core.location.LocationDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CertificateService extends ResourceExtensionService  {

    CertificateResponseDto listCertificates(SecurityFilter filter, SearchRequestDto request) throws ValidationException;

    CertificateDetailDto getCertificate(SecuredUUID uuid) throws NotFoundException, CertificateException, IOException;

    Certificate getCertificateEntity(SecuredUUID uuid) throws NotFoundException;

    List<Certificate> getCertificateEntityBySubjectDn(String subjectDn);

    List<Certificate> getCertificateEntityByCommonName(String commonName);

    // TODO AUTH - unable to check access based on certificate content. Make private? Special permission? Call opa in method?
    Certificate getCertificateEntityByContent(String content);

    // TODO AUTH - unable to check access based on certificate serial number. Make private? Special permission? Call opa in method?
    Certificate getCertificateEntityByFingerprint(String fingerprint) throws NotFoundException;

    Certificate getCertificateEntityByIssuerDnAndSerialNumber(String issuerDn, String serialNumber) throws NotFoundException;

    Boolean checkCertificateExistsByFingerprint(String fingerprint);

    void deleteCertificate(SecuredUUID uuid) throws NotFoundException;

    Certificate createCertificateEntity(X509Certificate certificate);

    void updateCertificateIssuer(Certificate certificate) throws NotFoundException;

    /**
     * Creates the Certificate entity
     *
     * @param certificateData Base64-encoded data of the certificate
     * @param certificateType Type of the certificate
     * @return Certificate entity
     */
    Certificate createCertificate(String certificateData, CertificateType certificateType) throws com.czertainly.api.exception.CertificateException;

    Certificate checkCreateCertificate(String certificate) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    Certificate checkCreateCertificateWithMeta(
            String certificate,
            List<MetadataAttribute> meta,
            String csr,
            UUID keyUuid,
            List<DataAttribute> csrAttributes,
            List<RequestAttributeDto> signatureAttributes,
            UUID connectorUuid,
            UUID sourceCertificateUuid,
            String issueAttributes
    ) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    CertificateDetailDto upload(UploadCertificateRequestDto request) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    // TODO AUTH - unable to check access based on certificate serial number. Make private? Special permission? Call opa in method?
    void revokeCertificate(String serialNumber);

    List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup();

    void bulkDeleteCertificate(SecurityFilter filter, RemoveCertificateDto request) throws NotFoundException;

    /**
     * List all locations associated with the certificate
     *
     * @param certificateUuid
     * @return List of locations
     * @throws NotFoundException
     */
    List<LocationDto> listLocations(SecuredUUID certificateUuid) throws NotFoundException;

    /**
     * List the available certificates that are associated with the RA Profile
     *
     * @param raProfile Ra Profile entity to search for the certificates
     * @return List of Certificates
     */
    List<Certificate> listCertificatesForRaProfile(RaProfile raProfile);

    /**
     * List the available certificates that are associated with the RA Profile
     *
     * @param raProfile Ra Profile entity to search for the certificates
     * @return List of Certificates
     */
    List<Certificate> listCertificatesForRaProfileAndNonNullComplianceStatus(RaProfile raProfile);

    /**
     * Initiates the compliance check for the certificates in the request
     *
     * @param request List of uuids of the certificate
     */
    void checkCompliance(CertificateComplianceCheckDto request);

    /**
     * Update the Certificate Entity
     *
     * @param certificate Certificate entity to be updated
     */
    void updateCertificateEntity(Certificate certificate);

    /**
     * Update the Certificate Objects contents
     *
     * @param uuid    UUID of the certificate
     * @param request Request for the certificate objects update
     */
    void  updateCertificateObjects(SecuredUUID uuid, CertificateUpdateObjectsDto request) throws NotFoundException;

    /**
     * Method to update the Objects of multiple certificates
     *
     * @param request Request to update multiple objects
     */
    void bulkUpdateCertificateObjects(SecurityFilter filter, MultipleCertificateObjectUpdateDto request) throws NotFoundException;

    /**
     * Function to get the validation result of the certificate
     *
     * @param uuid UUID of the certificate
     * @return Certificate Validation result
     * @throws NotFoundException
     */
    Map<String, CertificateValidationDto> getCertificateValidationResult(SecuredUUID uuid) throws NotFoundException;

    /**
     * Function to update status of certificates by scheduled event
     *
     */
    int updateCertificatesStatusScheduled();

    /**
     * Update the user uuid of the certificate in the core database
     *
     * @param certificateUuid UUID of the certificate
     * @param userUuid        UUID of the User
     * @throws NotFoundException
     */
    void updateCertificateUser(UUID certificateUuid, String userUuid) throws NotFoundException;

    /**
     * Remove the user uuid of the certificate in the core database
     *
     * @param userUuid UUID of the User
     */
    void removeCertificateUser(UUID userUuid);

    /**
     * Get the number of certificates per user for dashboard
     *
     * @return Number of certificates
     */
    Long statisticsCertificateCount(SecurityFilter filter);

    /**
     * Add statistics information based on the permission with the logged in user
     *
     * @param dto Statistics DTO with predefined records
     * @return Statistics DTO
     */
    StatisticsDto addCertificateStatistics(SecurityFilter filter, StatisticsDto dto);

    /**
     * Method to check if the permission is available for the user to issue certificate
     */
    void checkIssuePermissions();

    /**
     * Method to check if the permission is available for the user to renew certificate
     */
    void checkRenewPermissions();

    /**
     * Method to check if the permission is available for the user to revoke certificate
     */
    void checkRevokePermissions();

    /**
     * Get the list of attributes to generate the CSR
     * @return List of attributes to generate the CSR in core
     */
    List<BaseAttribute> getCsrGenerationAttributes();

    /**
     * Unassociate the given key from all the certificate
     * @param keyUuid UUID of the key object to be unassociated
     */
    void clearKeyAssociations(UUID keyUuid);

    /**
     * Function to update the certificate with the keys if known
     * @param keyUuid
     * @param publicKeyFingerprint
     * @throws NotFoundException
     */
    void updateCertificateKeys(UUID keyUuid, String publicKeyFingerprint);

    /**
     * Get the list of the certificate contents for the provided certificate UUIDs
     * @param uuids UUIDs of the certificate
     * @return List of certificate contents
     */
    List<CertificateContentDto> getCertificateContent(List<String> uuids);

    /**
     * Create CSR Entity and store it in the database which is ready for issuing
     * @param csr - PKCS10 certificate request to be added
     * @param signatureAttributes signatureAttributes used to sign the CSR. If the CSR is uploaded from the User
     *                            this parameter should be left empty
     * @param csrAttributes Attributes used to create CSR
     * @param keyUuid UUID of the key used to sign the CSR
     */
    Certificate createCsr(String csr, List<RequestAttributeDto> signatureAttributes, List<DataAttribute> csrAttributes, UUID keyUuid) throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException;

    /**
     * Function to change the Certificate Entity from CSR to Certificate
     * @param uuid UUID of the entity to be transformed
     * @param certificateData Issued Certificate Data
     * @param meta Metadata of the certificate
     * @return Certificate entity
     */
    Certificate updateCsrToCertificate(UUID uuid, String certificateData, List<MetadataAttribute> meta) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException, NotFoundException;

    /**
     * List certificates eligible for CA certificate of SCEP requests
     * @param filter Security Filter
     * @param intuneEnabled flag to return certificates that are eligible for Intune integration
     * @return List of available CA certificates
     */
    List<CertificateDto> listScepCaCertificates(SecurityFilter filter, boolean intuneEnabled);
}
