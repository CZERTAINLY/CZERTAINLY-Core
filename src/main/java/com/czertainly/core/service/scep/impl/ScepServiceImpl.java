package com.czertainly.core.service.scep.impl;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.certificate.CertificateUpdateObjectsDto;
import com.czertainly.api.model.connector.cryptography.enums.KeyType;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.scep.FailInfo;
import com.czertainly.api.model.core.scep.MessageType;
import com.czertainly.api.model.core.scep.PkiStatus;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.scep.ScepProfile;
import com.czertainly.core.dao.entity.scep.ScepTransaction;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.scep.ScepProfileRepository;
import com.czertainly.core.dao.repository.scep.ScepTransactionRepository;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.scep.ScepService;
import com.czertainly.core.service.scep.message.ScepRequest;
import com.czertainly.core.service.scep.message.ScepResponse;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.RandomUtil;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

@Service
@Transactional
public class ScepServiceImpl implements ScepService {

    public static final String SCEP_URL_PREFIX = "/v1/protocol/scep";
    private static final Logger logger = LoggerFactory.getLogger(ScepServiceImpl.class);
    private static final List<String> SCEP_CA_CAPABILITIES = List.of(
            "POSTPKIOperation",
            "SHA-1",
            "SHA-256",
            "SHA-512",
            "DES3",
            "AES",
            "Renewal",
            "SCEPStandard"
    );
    private final List<X509Certificate> caCertificateChain = new ArrayList<>();
    private X509Certificate recipient;
    private boolean raProfileBased;
    private RaProfile raProfile;
    private ScepProfile scepProfile;
    private RaProfileRepository raProfileRepository;
    private ScepProfileRepository scepProfileRepository;
    private ScepTransactionRepository scepTransactionRepository;
    private ClientOperationService clientOperationService;
    private CertValidationService certValidationService;
    private CertificateService certificateService;
    private CryptographicOperationsApiClient cryptographicOperationsApiClient;

    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) {
        this.raProfileRepository = raProfileRepository;
    }

    @Autowired
    public void setScepProfileRepository(ScepProfileRepository scepProfileRepository) {
        this.scepProfileRepository = scepProfileRepository;
    }

    @Autowired
    public void setScepTransactionRepository(ScepTransactionRepository scepTransactionRepository) {
        this.scepTransactionRepository = scepTransactionRepository;
    }

    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) {
        this.clientOperationService = clientOperationService;
    }

    @Autowired
    public void setCertValidationService(CertValidationService certValidationService) {
        this.certValidationService = certValidationService;
    }

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Autowired
    public void setCryptographicOperationsApiClient(CryptographicOperationsApiClient cryptographicOperationsApiClient) {
        this.cryptographicOperationsApiClient = cryptographicOperationsApiClient;
    }

    public void setRecipient(String certificateContent) {
        try {
            this.recipient = CertificateUtil.parseCertificate(certificateContent);
        } catch (CertificateException e) {
            // This should not occur
            throw new IllegalArgumentException("Error converting the certificate to x509 object");
        }
    }

    @Override
    public ResponseEntity<Object> handleGet(String profileName, String operation, String message) throws ScepException {
        byte[] encoded = new byte[0];
        if (message != null) {
            encoded = message.getBytes();
        }
        return service(profileName, operation, encoded);
    }

    @Override
    public ResponseEntity<Object> handlePost(String profileName, String operation, byte[] message) throws ScepException {
        return service(profileName, operation, message);
    }

    private ResponseEntity<Object> service(String profileName, String operation, byte[] message) throws ScepException {
        init(profileName);
        // TODO: why validation relies on Strings?
        String validationResult = validateProfile();
        if (!validationResult.isEmpty()) {
            throw new ValidationException(ValidationError.create(validationResult));
        }
        return switch (operation) {
            case "GetCACert" -> caCertificateChain.size() > 1 ? getCaCertChain() : getCaCert();
            case "GetCACaps" -> getCaCaps();
            case "PKIOperation" -> pkiOperation(message);
            default -> buildResponse(null, buildFailedResponse(new ScepException("Unsupported Operation", FailInfo.BAD_REQUEST)));
        };
    }

    private void init(String profileName) {
        this.raProfileBased = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString().contains("/raProfile/");
        if (raProfileBased) {
            raProfile = raProfileRepository.findByName(profileName).orElse(null);
            if (raProfile == null) {
                return;
            }
            scepProfile = raProfile.getScepProfile();
        } else {
            scepProfile = scepProfileRepository.findByName(profileName).orElse(null);
            if (scepProfile == null) {
                return;
            }
            raProfile = scepProfile.getRaProfile();
        }

        Certificate scepCaCertificate = scepProfile.getCaCertificate();
        setRecipient(scepCaCertificate.getCertificateContent().getContent());
        for (Certificate certificate : certValidationService.getCertificateChain(scepCaCertificate)) {
            try {
                this.caCertificateChain.add(CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent()));
            } catch (CertificateException e) {
                // This should not happen
                throw new IllegalArgumentException("Error converting the certificate to x509 object");
            }
        }
    }

    // TODO: why we return String instead of Ezception?
    private String validateProfile() {
        String scepProfileValidation = validateScepProfile();
        if (scepProfileValidation.isEmpty()) {
            return validateRaProfile();
        } else {
            return scepProfileValidation;
        }
    }

    // TODO: why we return String instead of Ezception?
    private String validateScepProfile() {
        if (scepProfile == null) {
            return "Requested SCEP Profile not found";
        }
        if (!scepProfile.isEnabled()) {
            return "SCEP Profile is not enabled";
        }
        if (scepProfile.getCaCertificate() == null) {
            return "SCEP Profile does not have any associated CA certificate";
        }
        if (!raProfileBased && scepProfile.getRaProfile() == null) {
            return "SCEP Profile does not contain associated RA Profile";
        }
        return "";
    }

    // TODO: why we return String instead of Ezception?
    private String validateRaProfile() {
        if (raProfile == null) {
            return "Requested RA Profile not found";
        }
        if (!raProfile.getEnabled()) {
            return "RA Profile is not enabled";
        }
        if (raProfileBased && raProfile.getScepProfile() == null) {
            return "RA Profile does not contain associated SCEP Profile";
        }
        return "";
    }

    private ResponseEntity<Object> getCaCert() {
        try {
            byte[] encoded = recipient.getEncoded();
            return getResponseEntity(encoded, "application/x-x509-ca-cert", encoded.length);
        } catch (CertificateException e) {
            // This should not happen
            throw new IllegalArgumentException("Error converting the certificate to x509 object");
        }
    }

    private ResponseEntity<Object> getCaCertChain() throws ScepException {
        byte[] encoded;
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        try {
            generator.addCertificates(new JcaCertStore(caCertificateChain));
            encoded = generator.generate(new CMSProcessableByteArray(new byte[0])).getEncoded();
        } catch (CertificateEncodingException | IOException | CMSException e) {
            return buildResponse(null, buildFailedResponse(new ScepException("Error generating CA certificate chain", e, FailInfo.BAD_REQUEST)));
        }
        return getResponseEntity(encoded, "application/x-x509-ca-ra-cert", encoded.length);
    }

    private ResponseEntity<Object> getCaCaps() {
        return getResponseEntity(String.join(System.lineSeparator(), SCEP_CA_CAPABILITIES), "text/plain", null);
    }

    private ResponseEntity<Object> getResponseEntity(Object body, String contentType, Integer contentLength) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("Content-Type", contentType);
        if (contentLength != null) responseHeaders.set("Content-Length", String.valueOf(contentLength));
        return new ResponseEntity<>(body, responseHeaders, HttpStatus.OK);
    }

    private ResponseEntity<Object> pkiOperation(byte[] body) throws ScepException {
        ScepRequest scepRequest;
        ScepResponse scepResponse;

        scepRequest = new ScepRequest(body);

        // TODO: this should go to the cryptographic key service
        // Get the private key from the configuration of SCEP Profile
        CryptographicKey key = scepProfile.getCaCertificate().getKey();
        CzertainlyPrivateKey czertainlyPrivateKey = null;
        for (CryptographicKeyItem item : key.getItems()) {
            if (item.getType().equals(KeyType.PRIVATE_KEY)) {
                czertainlyPrivateKey = new CzertainlyPrivateKey(
                        key.getTokenInstanceReference().getTokenInstanceUuid(),
                        item.getKeyReferenceUuid().toString(),
                        key.getTokenInstanceReference().getConnector().mapToDto()
                );
            }
        }

        if (czertainlyPrivateKey == null) {
            return buildResponse(scepRequest, buildFailedResponse(new ScepException("Private key not found in SCEP Profile", FailInfo.BAD_REQUEST)));
        }

        CzertainlyProvider czertainlyProvider = CzertainlyProvider.getInstance(scepProfile.getName(), true, cryptographicOperationsApiClient);

        // decrypt the PKCS#10 request
        scepRequest.decryptData(czertainlyPrivateKey, czertainlyProvider);

        // validate challenge password, if configured
        if (!validateScepChallengePassword(scepRequest.getChallengePassword())) {
            return buildResponse(scepRequest, buildFailedResponse(new ScepException("Challenge password validation failed.", FailInfo.BAD_MESSAGE_CHECK)));
        }

        // validate the request POP
        try {
            verifyRequest(scepRequest);
        } catch (ScepException e) {
            return buildResponse(scepRequest, buildFailedResponse(e));
        }

        if (scepTransactionRepository.existsByTransactionIdAndScepProfile(scepRequest.getTransactionId(), scepProfile)) {
            scepResponse = getExistingTransaction(scepRequest.getTransactionId());
        } else if (scepRequest.getMessageType().equals(MessageType.PKCS_REQ)) {
            try {
                // TODO: where we configure manual approval?
                if (scepProfile.getRequireManualApproval() != null && !scepProfile.getRequireManualApproval()) {
                    scepResponse = issueCertificate(scepRequest);
                } else {
                    scepResponse = generateCsr(scepRequest);
                }
            } catch (Exception e) {
                scepResponse =  buildFailedResponse(new ScepException("Failed to process SCEP request", e, FailInfo.BAD_REQUEST));
            }
        } else if (scepRequest.getMessageType().equals(MessageType.CERT_POLL)) {
            scepResponse = pollCertificate(scepRequest);
        } else {
            scepResponse =  buildFailedResponse(new ScepException("Unsupported Operation. The requested operation is not supported", FailInfo.BAD_REQUEST));
        }
        return buildResponse(scepRequest, scepResponse);
    }

    private ScepResponse buildFailedResponse(ScepException scepException) {
        ScepResponse scepResponse = new ScepResponse();
        scepResponse.setPkiStatus(PkiStatus.FAILURE);
        scepResponse.setFailInfo(scepException.getFailInfo());
        scepResponse.setFailInfoText(scepException.getMessage());
        logger.error("SCEP error: " + scepException.getMessage());

        return scepResponse;
    }

    private ResponseEntity<Object> buildResponse(ScepRequest scepRequest, ScepResponse scepResponse) throws ScepException {
        prepareMessage(scepRequest, scepResponse);
        scepResponse.generate();
        byte[] responseBody;
        try {
            responseBody = scepResponse.getSignedResponseData().getEncoded();
        } catch (IOException e) {
            throw new ScepException("Error generating SCEP response", e, FailInfo.BAD_REQUEST);
        }
        return getResponseEntity(responseBody, "application/x-pki-message", responseBody.length);
    }

    private ScepResponse issueCertificate(ScepRequest scepRequest) throws CertificateException, ConnectorException, NoSuchAlgorithmException, AlreadyExistException, IOException {
        ClientCertificateSignRequestDto requestDto = new ClientCertificateSignRequestDto();
        ScepResponse scepResponse = new ScepResponse();

        requestDto.setPkcs10(new String(Base64.getEncoder().encode(scepRequest.getPkcs10Request().getEncoded())));
        ClientCertificateDataResponseDto response = clientOperationService.issueCertificate(raProfile.getAuthorityInstanceReference().getSecuredParentUuid(), raProfile.getSecuredUuid(), requestDto);
        scepResponse.setCertificate(CertificateUtil.parseCertificate(response.getCertificateData()));
        addTransactionEntity(scepRequest.getTransactionId(), response.getUuid());
        scepResponse.setPkiStatus(PkiStatus.SUCCESS);

        return scepResponse;
    }


    private ScepResponse generateCsr(ScepRequest scepRequest) throws NotFoundException, CertificateException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        ClientCertificateRequestDto requestDto = new ClientCertificateRequestDto();
        ScepResponse scepResponse = new ScepResponse();

        requestDto.setPkcs10(new String(Base64.getEncoder().encode(scepRequest.getPkcs10Request().getEncoded())));
        CertificateDetailDto response = clientOperationService.createCsr(requestDto);
        CertificateUpdateObjectsDto updateObjectsRequest = new CertificateUpdateObjectsDto();
        updateObjectsRequest.setRaProfileUuid(raProfile.getUuid().toString());
        certificateService.updateCertificateObjects(SecuredUUID.fromString(response.getUuid()), updateObjectsRequest);
        addTransactionEntity(scepRequest.getTransactionId(), response.getUuid());
        scepResponse.setPkiStatus(PkiStatus.PENDING);

        return scepResponse;
    }

    private ScepResponse getExistingTransaction(String transactionId) {
        ScepTransaction scepTransaction = scepTransactionRepository.findByTransactionIdAndScepProfile(transactionId, scepProfile).orElse(null);
        assert scepTransaction != null;
        Certificate certificate = scepTransaction.getCertificate();
        ScepResponse scepResponse = new ScepResponse();
        try {
            if (certificate.getStatus() != CertificateStatus.NEW) {
                scepResponse.setPkiStatus(PkiStatus.SUCCESS);
                scepResponse.setCertificate(CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent()));
            } else {
                scepResponse.setPkiStatus(PkiStatus.PENDING);
            }
        } catch (Exception e) {
            // TODO: why is this catch block empty?
        }
        return scepResponse;
    }

    private void addTransactionEntity(String transactionId, String certificateUuid) {
        ScepTransaction scepTransaction = new ScepTransaction();
        scepTransaction.setTransactionId(transactionId);
        scepTransaction.setCertificateUuid(UUID.fromString(certificateUuid));
        scepTransaction.setScepProfile(scepProfile);
        scepTransactionRepository.save(scepTransaction);
    }

    private ScepResponse pollCertificate(ScepRequest scepRequest) {
        ScepResponse scepResponse = new ScepResponse();
        try {
            ScepTransaction transaction = getTransaction(scepRequest.getTransactionId());
            if (!transaction.getCertificate().getStatus().equals(CertificateStatus.NEW)) {
                scepResponse.setCertificate(CertificateUtil.parseCertificate(transaction.getCertificate().getCertificateContent().getContent()));
                scepResponse.setPkiStatus(PkiStatus.SUCCESS);
            } else {
                scepResponse.setPkiStatus(PkiStatus.PENDING);
            }
            prepareMessage(scepRequest, scepResponse);

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return scepResponse;
    }

    private void prepareMessage(ScepRequest scepRequest, ScepResponse scepResponse) {
        if (scepRequest == null) {
            return;
        }
        // TODO: what if some of the request field are null?
        scepResponse.setRecipientNonce(scepRequest.getSenderNonce());
        scepResponse.setTransactionId(scepRequest.getTransactionId());
        scepResponse.setCaCertificate(recipient);
        scepResponse.setRecipientKeyInfo(scepRequest.getRequestKeyInfo());
        scepResponse.setDigestAlgorithmOid(scepRequest.getDigestAlgorithmOid());
        scepResponse.setSenderNonce(RandomUtil.generateRandomNonceBase64(16));
    }

    private ScepTransaction getTransaction(String transactionId) {
        return scepTransactionRepository.findByTransactionId(transactionId).orElse(null);
    }

    private boolean validateScepChallengePassword(String challengePassword) {
        if (scepProfile.getChallengePassword() == null || scepProfile.getChallengePassword().isEmpty()) {
            return true;
        }
        return challengePassword.equals(scepProfile.getChallengePassword());
    }

    public void verifyRequest(ScepRequest scepRequest) throws ScepException {

        // TODO: this condition can never happen?
        if ( !scepRequest.getMessageType().equals(MessageType.RENEWAL_REQ) && scepRequest.getMessageType().equals(MessageType.RENEWAL_REQ) ) {
            throw new ScepException("Unsupported Operation", FailInfo.BAD_REQUEST);
        }

        if (scepRequest.getMessageType().equals(MessageType.PKCS_REQ)) {
            // TODO: why we are checking renewal for the new request that are not of type RENEWAL_REQ?
            renewalValidation(scepRequest);
            try {
                scepRequest.verifyRequest();
            } catch (PKCSException | NoSuchAlgorithmException | InvalidKeyException | OperatorCreationException e) {
                throw new ScepException("Failed to verify PKCS#10 request POP", FailInfo.BAD_REQUEST);
            }
        } else if (scepRequest.getMessageType().equals(MessageType.RENEWAL_REQ)) {
            renewalValidation(scepRequest);
        }
        throw new ScepException("Unsupported Operation", FailInfo.BAD_REQUEST);
    }

    private void renewalValidation(ScepRequest scepRequest) throws ScepException {
        JcaPKCS10CertificationRequest pkcs10Request = scepRequest.getPkcs10Request();
        String cn = null;
        try {
            cn = pkcs10Request.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue().toString();
        } catch (Exception e){
            // TODO: why we are ignoring this exception?
            //Do Nothing
        }
        List<Certificate> certificates = certificateService.getCertificateEntityByCommonName(cn);
        for (Certificate certificate : certificates) {
            // TODO: subject can be the same but the certificate can be different
            if(!(new X500Name(certificate.getSubjectDn())).equals(pkcs10Request.getSubject())) {
                continue;
            }
            X509Certificate x509Certificate;
            try {
                x509Certificate = CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent());
            } catch (CertificateException e) {
                throw new ScepException("Failed to parse certificate", FailInfo.BAD_REQUEST);
            }
            try {
                if (scepRequest.verifySignature(x509Certificate.getPublicKey())) {
                    // TODO: why we are checking for same public key, when this is already done in the rekey method?
                    if (Arrays.equals(x509Certificate.getPublicKey().getEncoded(), pkcs10Request.getPublicKey().getEncoded())) {
                        throw new ScepException("Public Key of the renewal certificate and the CSR cannot be same", FailInfo.BAD_REQUEST);
                    }
                    checkRenewalTimeframe(certificate);
                }
            } catch (CMSException | OperatorCreationException | InvalidKeyException | NoSuchAlgorithmException e) {
                throw new ScepException("Failed to verify SCEP request signature", FailInfo.BAD_REQUEST);
            }
        }
    }

    private void checkRenewalTimeframe(Certificate certificate) throws ScepException {
        // TODO: default value for number of days is 0, so this condition will never be true
        if (scepProfile.getRenewalThreshold() == null) {
            // TODO: why we are checking for half life time of the certificate?
            if (certificate.getValidity() / 2 < certificate.getExpiryInDays()) {
                throw new ScepException("Cannot renew certificate. Validity exceeds the half life time of certificate", FailInfo.BAD_REQUEST);
            }
        } else if (certificate.getStatus().equals(CertificateStatus.EXPIRED) || certificate.getStatus().equals(CertificateStatus.REVOKED)) {
            throw new ScepException("Cannot renew certificate. Certificate is already in expired or revoked state", FailInfo.BAD_REQUEST);
        } else {
            if (certificate.getExpiryInDays() > scepProfile.getRenewalThreshold()) {
                throw new ScepException("Cannot renew certificate. Validity exceeds the configured value in SCEP profile", FailInfo.BAD_REQUEST);
            }
        }
    }
}
