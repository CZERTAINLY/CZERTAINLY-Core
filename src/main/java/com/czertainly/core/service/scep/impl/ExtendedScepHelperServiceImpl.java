package com.czertainly.core.service.scep.impl;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.client.certificate.CertificateUpdateObjectsDto;
import com.czertainly.api.model.common.collection.DigestAlgorithm;
import com.czertainly.api.model.common.collection.RsaSignatureScheme;
import com.czertainly.api.model.connector.cryptography.enums.KeyType;
import com.czertainly.api.model.connector.cryptography.operations.CipherDataRequestDto;
import com.czertainly.api.model.connector.cryptography.operations.DecryptDataResponseDto;
import com.czertainly.api.model.connector.cryptography.operations.data.CipherRequestData;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateStatus;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.scep.FailInfo;
import com.czertainly.api.model.core.scep.PkiStatus;
import com.czertainly.api.model.core.scep.ScepRequestMessage;
import com.czertainly.api.model.core.scep.ScepResponseMessage;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.attribute.EncryptionAttributes;
import com.czertainly.core.attribute.RsaSignatureAttributes;
import com.czertainly.core.config.ScepVerifierProvider;
import com.czertainly.core.config.TokenContentSigner;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.scep.ScepProfile;
import com.czertainly.core.dao.entity.scep.ScepTransaction;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.scep.ScepProfileRepository;
import com.czertainly.core.dao.repository.scep.ScepTransactionRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertValidationService;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.scep.ExtendedScepHelperService;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.ScepCommonHelper;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.x500.DirectoryString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.util.CollectionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;

@Service
@Transactional
public class ExtendedScepHelperServiceImpl implements ExtendedScepHelperService {

    public static final String SCEP_URL_PREFIX = "/v1/protocol/scep";
    private static final Logger logger = LoggerFactory.getLogger(ExtendedScepHelperServiceImpl.class);
    private static final List<String> SCEP_CA_CAPABILITIES = List.of(
            "POSTPKIOperation",
            "SHA-1",
            "SHA-256",
            "DES3",
            "Renewal"
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
    private CryptographicKeyItem privateKeyItem = null;
    private CryptographicKeyItem publicKeyItem = null;

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
    public ResponseEntity<Object> handleGet(String profileName, String operation, String message) {
        byte[] encoded = new byte[0];
        if (message != null) {
            encoded = message.getBytes();
        }
        return service(profileName, operation, encoded);
    }

    @Override
    public ResponseEntity<Object> handlePost(String profileName, String operation, byte[] message) {
        return service(profileName, operation, message);
    }

    private ResponseEntity<Object> service(String profileName, String operation, byte[] message) {
        init(profileName);
        String validationResult = validate();
        if (!validationResult.isEmpty()) {
            throw new ValidationException(ValidationError.create(validationResult));
        }
        switch (operation) {
            case "GetCACert":
                return caCertificateChain.size() > 1 ? getCaCertChain() : getCaCert();
            case "GetCACaps":
                return getCaCaps();
            case "PKIOperation":
                return pkiOperation(message);
            default:
                return errorReturn(null, FailInfo.BAD_REQUEST, "Unsupported Operation");

        }
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
        this.caCertificateChain = new ArrayList<>();
        for (Certificate certificate : certValidationService.getCertificateChain(scepCaCertificate)) {
            try {
                this.caCertificateChain.add(CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent()));
            } catch (CertificateException e) {
                // This should not happen
                throw new IllegalArgumentException("Error converting the certificate to x509 object");
            }
        }
    }

    private String validate() {
        String scepProfileValidation = validateScepProfile();
        if (scepProfileValidation.isEmpty()) {
            return validateRaProfile();
        } else {
            return scepProfileValidation;
        }
    }

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

    private ResponseEntity<Object> getCaCertChain() {
        byte[] encoded;
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        try {
            generator.addCertificates(new JcaCertStore(caCertificateChain));
            encoded = generator.generate(new CMSProcessableByteArray(new byte[0])).getEncoded();
        } catch (CertificateEncodingException | IOException | CMSException e) {
            return errorReturn(null, FailInfo.BAD_REQUEST, e.getMessage());
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

    private ResponseEntity<Object> pkiOperation(byte[] body) {
        ScepRequestMessage scepRequestMessage = null;
        ScepResponseMessage responseMessage = null;
        setPrivateKey();
        try {
            scepRequestMessage = new ScepRequestMessage(body);
            String requestError = initiateRequestMessageParsing(scepRequestMessage);
            if (!requestError.isEmpty()) {
                return errorReturn(scepRequestMessage, FailInfo.BAD_REQUEST, requestError);
            }
        } catch (Exception e) {
            logger.error("Error parsing the Scep Request Message", e.getMessage());
            errorReturn(scepRequestMessage, FailInfo.BAD_REQUEST, "Error parsing scep request message");
        }

        String decryptError = decrypt(scepRequestMessage);
        if (!decryptError.isEmpty()) {
            return errorReturn(scepRequestMessage, FailInfo.BAD_REQUEST, decryptError);
        }
        if (!validateScepChallengePassword(scepRequestMessage)) {
            return errorReturn(scepRequestMessage, FailInfo.BAD_MESSAGE_CHECK, "Challenge password validation failed. Empty / Incorrect password");
        }
        try {
            String validationErrors = verifySignature(scepRequestMessage);
            if (!validationErrors.isEmpty()) {
                return errorReturn(scepRequestMessage, FailInfo.BAD_MESSAGE_CHECK, "Signature verification failed for the request");
            }
        } catch (Exception e) {
            return errorReturn(scepRequestMessage, FailInfo.BAD_REQUEST, "Error validating signature. " + e.getMessage());
        }

        if (scepTransactionRepository.existsByTransactionIdAndScepProfile(scepRequestMessage.getTransactionId(), scepProfile)) {
            responseMessage = checkGetExistingTransaction(scepRequestMessage);
        } else if (scepRequestMessage.getMessageType() == ScepCommonHelper.SCEP_TYPE_PKCSREQ) {
            try {
                if (scepProfile.getRequireManualApproval() != null && !scepProfile.getRequireManualApproval()) {
                    responseMessage = issueCertificate(scepRequestMessage);
                } else {
                    responseMessage = generateCsr(scepRequestMessage);
                }
            } catch (Exception e) {
                return errorReturn(scepRequestMessage, FailInfo.BAD_REQUEST, e.getMessage());
            }
        } else if (scepRequestMessage.getMessageType() == ScepCommonHelper.SCEP_TYPE_POLL_CERT) {
            responseMessage = pollCertificate(scepRequestMessage);
        } else {
            return errorReturn(scepRequestMessage, FailInfo.BAD_REQUEST, "Unsupported Operation. The requested operation is not supported");
        }
        return getResponse(responseMessage);
    }

    private ResponseEntity<Object> errorReturn(ScepRequestMessage scepRequestMessage, FailInfo failInfo, String errorText) {
        ScepResponseMessage scepResponseMessage = new ScepResponseMessage();
        scepResponseMessage.setFailInfo(failInfo);
        scepResponseMessage.setFailText(errorText);
        scepResponseMessage.setStatus(PkiStatus.FAILURE);
        prepareMessage(scepRequestMessage, scepResponseMessage);
        logger.error("Error in SCEP Request: " + errorText);
        try {
            byte[] responseBody = generateResponseBody(scepResponseMessage);
            return getResponseEntity(responseBody, "application/x-pki-message", responseBody.length);
        } catch (CertificateEncodingException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    private ResponseEntity<Object> getResponse(ScepResponseMessage scepResponseMessage) {
        try {
            byte[] responseBody = generateResponseBody(scepResponseMessage);
            return getResponseEntity(responseBody, "application/x-pki-message", responseBody.length);
        } catch (CertificateEncodingException e) {
            // This should not happen
            return null;
        }
    }

    private void setPrivateKey() {
        for (CryptographicKeyItem item : scepProfile.getCaCertificate().getKey().getItems()) {
            if (item.getType().equals(KeyType.PRIVATE_KEY)) {
                privateKeyItem = item;
            } else if (item.getType().equals(KeyType.PUBLIC_KEY)) {
                publicKeyItem = item;
            } else {
                //do nothing
            }
        }
    }

    private ScepResponseMessage issueCertificate(ScepRequestMessage scepRequestMessage) throws CertificateException, ConnectorException, NoSuchAlgorithmException, AlreadyExistException, IOException {
        ClientCertificateSignRequestDto requestDto = new ClientCertificateSignRequestDto();
        ScepResponseMessage responseMessage = new ScepResponseMessage();
        requestDto.setPkcs10(new String(Base64.getEncoder().encode(scepRequestMessage.getPkcs10().getEncoded())));
        ClientCertificateDataResponseDto response = clientOperationService.issueCertificate(raProfile.getAuthorityInstanceReference().getSecuredParentUuid(), raProfile.getSecuredUuid(), requestDto);
        responseMessage.setCertificate(CertificateUtil.parseCertificate(response.getCertificateData()));
        addTransactionEntity(scepRequestMessage.getTransactionId(), response.getUuid());
        responseMessage.setStatus(PkiStatus.SUCCESS);
        prepareMessage(scepRequestMessage, responseMessage);
        return responseMessage;
    }

    private ScepResponseMessage generateCsr(ScepRequestMessage scepRequestMessage) throws NotFoundException, CertificateException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        ClientCertificateRequestDto requestDto = new ClientCertificateRequestDto();
        ScepResponseMessage responseMessage = new ScepResponseMessage();
        requestDto.setPkcs10(new String(Base64.getEncoder().encode(scepRequestMessage.getPkcs10().getEncoded())));
        CertificateDetailDto response = clientOperationService.createCsr(requestDto);
        CertificateUpdateObjectsDto updateObjectsRequest = new CertificateUpdateObjectsDto();
        updateObjectsRequest.setRaProfileUuid(raProfile.getUuid().toString());
        certificateService.updateCertificateObjects(SecuredUUID.fromString(response.getUuid()), updateObjectsRequest);
        addTransactionEntity(scepRequestMessage.getTransactionId(), response.getUuid());
        responseMessage.setStatus(PkiStatus.PENDING);
        prepareMessage(scepRequestMessage, responseMessage);
        return responseMessage;
    }

    private ScepResponseMessage checkGetExistingTransaction(ScepRequestMessage scepRequestMessage) {
        ScepTransaction scepTransaction = scepTransactionRepository.findByTransactionIdAndScepProfile(scepRequestMessage.getTransactionId(), scepProfile).orElse(null);
        Certificate certificate = scepTransaction.getCertificate();
        ScepResponseMessage responseMessage = new ScepResponseMessage();
        try {
            if (certificate.getStatus() != CertificateStatus.NEW) {
                responseMessage.setStatus(PkiStatus.SUCCESS);
                responseMessage.setCertificate(CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent()));
                prepareMessage(scepRequestMessage, responseMessage);
            } else {
                responseMessage.setStatus(PkiStatus.PENDING);
                prepareMessage(scepRequestMessage, responseMessage);
            }
        } catch (Exception e) {
            //TODO
        }
        return responseMessage;
    }

    private String decrypt(ScepRequestMessage scepRequestMessage) {
        if (scepRequestMessage.getMessageType() != ScepCommonHelper.SCEP_TYPE_PKCSREQ) {
            return "";
        }
        try {
            String tokenInstanceUuid = privateKeyItem.getCryptographicKey().getTokenInstanceReference().getTokenInstanceUuid();
            String keyUuid = privateKeyItem.getKeyReferenceUuid().toString();
            ConnectorDto connectorDto = privateKeyItem.getCryptographicKey().getTokenInstanceReference().getConnector().mapToDto();
            CipherDataRequestDto cipherDataRequestDto = new CipherDataRequestDto();
            CipherRequestData cipherRequestData = new CipherRequestData();
            cipherRequestData.setData(scepRequestMessage.getEncryptedData().getEncoded());
            cipherDataRequestDto.setCipherAttributes(List.of(EncryptionAttributes.buildCmsRequestAttribute(true)));
            cipherDataRequestDto.setCipherData(List.of(cipherRequestData));

            DecryptDataResponseDto decryptDataResponseDto = cryptographicOperationsApiClient.decryptData(connectorDto, tokenInstanceUuid, keyUuid, cipherDataRequestDto);
            scepRequestMessage.setPkcs10(new JcaPKCS10CertificationRequest(decryptDataResponseDto.getDecryptedData().get(0).getData()));
            return "";
        } catch (IOException e) {
            return "Error decrypting request message. IO Exception when decrypting the data." + e.getMessage();
        } catch (ConnectorException e) {
            return "Error decrypting the scep request. Connector threw error: " + e.getMessage();
        }
    }

    private void addTransactionEntity(String transactionId, String certificateUuid) {
        ScepTransaction scepTransaction = new ScepTransaction();
        scepTransaction.setTransactionId(transactionId);
        scepTransaction.setCertificateUuid(UUID.fromString(certificateUuid));
        scepTransaction.setScepProfile(scepProfile);
        scepTransactionRepository.save(scepTransaction);
    }

    private ScepResponseMessage pollCertificate(ScepRequestMessage scepRequestMessage) {
        ScepResponseMessage responseMessage = new ScepResponseMessage();
        try {
            ScepTransaction transaction = getTransaction(scepRequestMessage.getTransactionId());
            if (!transaction.getCertificate().getStatus().equals(CertificateStatus.NEW)) {
                responseMessage.setCertificate(CertificateUtil.parseCertificate(transaction.getCertificate().getCertificateContent().getContent()));
                responseMessage.setStatus(PkiStatus.SUCCESS);
            } else {
                responseMessage.setStatus(PkiStatus.PENDING);
            }
            prepareMessage(scepRequestMessage, responseMessage);

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return responseMessage;
    }

    private void prepareMessage(ScepRequestMessage requestMessage, ScepResponseMessage responseMessage) {
        if (requestMessage == null) {
            return;
        }
        responseMessage.setRecipientNonce(requestMessage.getSenderNonce());
        responseMessage.setTransactionId(requestMessage.getTransactionId());
        responseMessage.setCaCertificate(recipient);
        responseMessage.setRecipientKeyInfo(requestMessage.getRequestKeyInfo());
        responseMessage.setDigestAlgorithm(requestMessage.getPreferredDigestAlg());

        responseMessage.setSenderNonce(ScepCommonHelper.getRandomNonce());
    }

    private ScepTransaction getTransaction(String transactionId) {
        return scepTransactionRepository.findByTransactionId(transactionId).orElse(null);
    }

    private boolean validateScepChallengePassword(ScepRequestMessage scepRequestMessage) {
        if (scepProfile.getChallengePassword() == null || scepProfile.getChallengePassword().isEmpty()) {
            return true;
        }

        String csrChallengePassword = getPassword(scepRequestMessage);
        return csrChallengePassword.equals(scepProfile.getChallengePassword());
    }

    public byte[] generateResponseBody(ScepResponseMessage responseMessage) throws CertificateEncodingException {
        try {
            CMSTypedData msg;

            if (responseMessage.getStatus().equals(PkiStatus.SUCCESS)) {
                CMSEnvelopedDataGenerator edGen = new CMSEnvelopedDataGenerator();
                List<X509Certificate> certList = new ArrayList<X509Certificate>();
                if (responseMessage.getCertificate() != null) {
                    logger.debug("Adding certificates to response message");
                    certList.add(responseMessage.getCertificate());
                    if (scepProfile.isIncludeCaCertificate()) {
                        if (scepProfile.isIncludeCaCertificateChain()) {
                            certList.addAll(caCertificateChain);
                        } else {
                            // If we have an explicit CAcertificate
                            logger.debug("Including explicitly set CA certificate in SCEP response.");
                            certList.add(recipient);
                        }
                    }
                }
                CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                gen.addCertificates(new CollectionStore<>(ScepCommonHelper.convertToX509CertificateHolder(certList)));
                CMSSignedData s = gen.generate(new CMSAbsentContent(), false);

                // Envelope the CMS message
                if (responseMessage.getRecipientKeyInfo() != null) {
                    try {
                        X509Certificate rec = CertificateUtil.getX509Certificate(responseMessage.getRecipientKeyInfo());
                        logger.debug("Added recipient information - issuer: '" + rec.getIssuerX500Principal().getName() + "', serno: '" + rec.getSerialNumber().toString(16));
                        edGen.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(rec).setProvider(BouncyCastleProvider.PROVIDER_NAME));
                    } catch (CertificateParsingException e) {
                        logger.error(e.getMessage());
                        throw new IllegalArgumentException("Can not decode recipients self signed certificate!", e);
                    } catch (CertificateException e) {
                        throw new IllegalArgumentException("Error when converting the certificate", e);
                    }
                } else {
                    edGen.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(responseMessage.getCertificate()).setProvider(BouncyCastleProvider.PROVIDER_NAME));
                }
                try {
                    JceCMSContentEncryptorBuilder jceCMSContentEncryptorBuilder = new JceCMSContentEncryptorBuilder(SMIMECapability.dES_EDE3_CBC).setProvider(BouncyCastleProvider.PROVIDER_NAME);
                    CMSEnvelopedData ed = edGen.generate(new CMSProcessableByteArray(s.getEncoded()), jceCMSContentEncryptorBuilder.build());
                    if (logger.isDebugEnabled()) {
                        logger.debug("Enveloped data is " + ed.getEncoded().length + " bytes long");
                    }
                    msg = new CMSProcessableByteArray(ed.getEncoded());
                } catch (IOException e) {
                    throw new IllegalStateException("Unexpected IOException caught", e);
                }
            } else {
                msg = new CMSProcessableByteArray(new byte[0]);
            }
            CMSSignedDataGenerator gen1 = new CMSSignedDataGenerator();

            Hashtable<ASN1ObjectIdentifier, Attribute> attributes = new Hashtable<>();
            ASN1ObjectIdentifier oid;
            Attribute attr;
            DERSet value;

            // Message type (certrep)
            oid = new ASN1ObjectIdentifier(ScepCommonHelper.id_messageType);
            value = new DERSet(new DERPrintableString("3"));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);

            // TransactionId
            if (responseMessage.getTransactionId() != null) {
                oid = new ASN1ObjectIdentifier(ScepCommonHelper.id_transId);
                logger.debug("Added transactionId: " + responseMessage.getTransactionId());
                value = new DERSet(new DERPrintableString(responseMessage.getTransactionId()));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            // status
            oid = new ASN1ObjectIdentifier(ScepCommonHelper.id_pkiStatus);
            value = new DERSet(new DERPrintableString(responseMessage.getStatus().getValue()));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);

            if (responseMessage.getStatus().equals(PkiStatus.FAILURE)) {
                oid = new ASN1ObjectIdentifier(ScepCommonHelper.id_failInfo);
                logger.debug("Added failInfo: " + responseMessage.getFailInfo().getValue());
                value = new DERSet(new DERPrintableString(responseMessage.getFailInfo().getValue()));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            if (responseMessage.getStatus().equals(PkiStatus.FAILURE) && responseMessage.getFailText() != null) {
                oid = new ASN1ObjectIdentifier(ScepCommonHelper.id_scep_failInfoText);
                logger.debug("Added failInfo: " + responseMessage.getFailText());
                value = new DERSet(new DERPrintableString(responseMessage.getFailText()));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            // senderNonce
            if (responseMessage.getSenderNonce() != null) {
                oid = new ASN1ObjectIdentifier(ScepCommonHelper.id_senderNonce);
                logger.debug("Added senderNonce: " + responseMessage.getSenderNonce());
                value = new DERSet(new DEROctetString(Base64.getDecoder().decode(responseMessage.getSenderNonce().getBytes())));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            } else {
                byte[] senderNonce = new byte[16];
                Random randomSource = new Random();
                randomSource.nextBytes(senderNonce);
                responseMessage.setSenderNonce(new String(Base64.getEncoder().encode(senderNonce)));
            }

            // recipientNonce
            if (responseMessage.getRecipientNonce() != null) {
                oid = new ASN1ObjectIdentifier(ScepCommonHelper.id_recipientNonce);
                logger.debug("Added recipientNonce: " + responseMessage.getRecipientNonce());
                value = new DERSet(new DEROctetString(Base64.getDecoder().decode(responseMessage.getRecipientNonce().getBytes())));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            try {
                if(privateKeyItem != null) {
                    List<RequestAttributeDto> signatureAttributes = List.of(
                            RsaSignatureAttributes.buildRequestRsaSigScheme(RsaSignatureScheme.PKCS1V15),
                            RsaSignatureAttributes.buildRequestDigest(DigestAlgorithm.SHA_256.name()));
                    ContentSigner signer = new TokenContentSigner(
                            cryptographicOperationsApiClient,
                            privateKeyItem.getCryptographicKey().getTokenInstanceReference().getConnector().mapToDto(),
                            privateKeyItem.getCryptographicKey().getTokenInstanceReferenceUuid(),
                            privateKeyItem.getKeyReferenceUuid(),
                            publicKeyItem.getKeyReferenceUuid(),
                            publicKeyItem.getKeyData(),
                            publicKeyItem.getCryptographicAlgorithm(),
                            signatureAttributes
                    );
                    JcaDigestCalculatorProviderBuilder calculatorProviderBuilder = new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME);
                    JcaSignerInfoGeneratorBuilder builder = new JcaSignerInfoGeneratorBuilder(calculatorProviderBuilder.build());
                    builder.setSignedAttributeGenerator(new DefaultSignedAttributeTableGenerator(new AttributeTable(attributes)));
                    gen1.addSignerInfoGenerator(builder.build(signer, recipient));
                }
            } catch (OperatorCreationException e) {
                throw new IllegalStateException("BouncyCastle failed in creating signature provider.", e);
            }

            final CMSSignedData signedData = gen1.generate(msg, true);
            try {
                return signedData.getEncoded();
            } catch (IOException e) {
                throw new IllegalStateException("Unexpected IOException caught.", e);
            }
        } catch (CMSException e) {
            logger.error("Error creating CMS message: ", e);
        }
        return new byte[0];
    }


    public String getPassword(ScepRequestMessage scepRequestMessage) {
        String challengePassword = "";
        org.bouncycastle.asn1.pkcs.Attribute[] attributes = scepRequestMessage.getPkcs10().getAttributes(PKCSObjectIdentifiers.pkcs_9_at_challengePassword);
        ASN1Encodable obj;
        ASN1Set values;
        if (attributes.length == 0) {
            attributes = scepRequestMessage.getPkcs10().getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
            if (attributes.length == 0) {
                return "";
            }

            values = attributes[0].getAttrValues();
            if (values.size() == 0) {
                return "";
            }

            Extensions exts = Extensions.getInstance(values.getObjectAt(0));
            org.bouncycastle.asn1.x509.Extension ext = exts.getExtension(PKCSObjectIdentifiers.pkcs_9_at_challengePassword);
            if (ext == null) {
                return "";
            }

            obj = ext.getExtnValue();
        } else {
            values = attributes[0].getAttrValues();
            obj = values.getObjectAt(0);
        }

        if (obj != null) {

            Object str;
            try {
                str = DirectoryString.getInstance(obj);
            } catch (IllegalArgumentException var7) {
                str = DERIA5String.getInstance(obj);
            }

            if (str != null) {
                challengePassword = ((ASN1String) str).getString();
            }
        }

        return challengePassword;
    }

    private String initiateRequestMessageParsing(ScepRequestMessage scepRequestMessage) throws IOException {
        try {
            CMSSignedData csd = new CMSSignedData(scepRequestMessage.getScepRequestMessage());
            SignerInformationStore infoStore = csd.getSignerInfos();
            Collection<SignerInformation> signers = infoStore.getSigners();
            Iterator<SignerInformation> iter = signers.iterator();
            if (iter.hasNext()) {
                SignerInformation si = iter.next();
                final String reqAlg = si.getDigestAlgOID();
                scepRequestMessage.setOriginalDigestAlgorithm(reqAlg);
                if (reqAlg == CMSSignedGenerator.DIGEST_SHA1 || reqAlg == CMSSignedGenerator.DIGEST_MD5) {
                    logger.debug("Legacy request digest algorithm will only be used if explicitly allowed in SCEP alias, defaulting to " + scepRequestMessage.getPreferredDigestAlg());
                } else {
                    scepRequestMessage.setPreferredDigestAlg(reqAlg);
                    logger.debug("Set " + reqAlg + " as preferred digest algorithm for SCEP");
                }
            }
        } catch (CMSException e) {
            logger.error("CMSException trying to get preferred digest algorithm: ", e);
        }
        ASN1InputStream seqAsn1InputStream = new ASN1InputStream(new ByteArrayInputStream(scepRequestMessage.getScepRequestMessage()));
        ASN1Sequence seq;
        try {
            seq = ASN1Sequence.getInstance(seqAsn1InputStream.readObject());
        } finally {
            seqAsn1InputStream.close();
        }
        ContentInfo ci = ContentInfo.getInstance(seq);
        String ctoid = ci.getContentType().getId();

        if (ctoid.equals(CMSObjectIdentifiers.signedData.getId())) {
            scepRequestMessage.setSignedData(SignedData.getInstance(ASN1Sequence.getInstance(ci.getContent())));
            ASN1Set certs = scepRequestMessage.getSignedData().getCertificates();
            if (certs.size() > 0) {
                ASN1Encodable dercert = certs.getObjectAt(0);
                if (dercert != null) {
                    final ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                    final ASN1OutputStream dOut = ASN1OutputStream.create(bOut, ASN1Encoding.DER);
                    dOut.writeObject(dercert);
                    if (bOut.size() > 0) {
                        scepRequestMessage.setRequestKeyInfo(bOut.toByteArray());
                    }
                    try {
                        scepRequestMessage.setSignercert(CertificateUtil.getX509Certificate(scepRequestMessage.getRequestKeyInfo()));
                    } catch (CertificateException e) {
                        logger.error("Error parsing requestKeyInfo : ", e);
                    }
                }
            }

            Enumeration<?> sis = scepRequestMessage.getSignedData().getSignerInfos().getObjects();

            if (sis.hasMoreElements()) {
                final SignerInfo si = SignerInfo.getInstance(ASN1Sequence.getInstance(sis.nextElement()));
                Enumeration<?> attr = si.getAuthenticatedAttributes().getObjects();

                while (attr.hasMoreElements()) {
                    final Attribute a = Attribute.getInstance(ASN1Sequence.getInstance(attr.nextElement()));
                    if (logger.isDebugEnabled()) {
                        logger.debug("Found attribute: " + a.getAttrType().getId());
                    }
                    if (a.getAttrType().getId().equals(ScepCommonHelper.id_senderNonce)) {
                        Enumeration<?> values = a.getAttrValues().getObjects();
                        ASN1OctetString str = ASN1OctetString.getInstance(values.nextElement());
                        scepRequestMessage.setSenderNonce(new String(Base64.getEncoder().encode(str.getOctets())));
                    }
                    if (a.getAttrType().getId().equals(ScepCommonHelper.id_transId)) {
                        Enumeration<?> values = a.getAttrValues().getObjects();
                        DERPrintableString str = DERPrintableString.getInstance(values.nextElement());
                        scepRequestMessage.setTransactionId(str.getString());
                    }
                    if (a.getAttrType().getId().equals(ScepCommonHelper.id_messageType)) {
                        Enumeration<?> values = a.getAttrValues().getObjects();
                        DERPrintableString str = DERPrintableString.getInstance(values.nextElement());
                        scepRequestMessage.setMessageType(Integer.parseInt(str.getString()));
                    }
                }
            }

            // If this is a PKCSReq
            if ((scepRequestMessage.getMessageType() == ScepCommonHelper.SCEP_TYPE_PKCSREQ)) {
                ci = scepRequestMessage.getSignedData().getEncapContentInfo();
                ctoid = ci.getContentType().getId();

                if (ctoid.equals(CMSObjectIdentifiers.data.getId())) {
                    final ASN1OctetString content = ASN1OctetString.getInstance(ci.getContent());
                    if (logger.isDebugEnabled()) {
                        logger.debug("envelopedData is " + content.getOctets().length + " bytes.");
                    }
                    final ASN1InputStream seq1Asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(content.getOctets()));
                    ASN1Sequence seq1;
                    try {
                        seq1 = ASN1Sequence.getInstance(seq1Asn1InputStream.readObject());
                    } finally {
                        seq1Asn1InputStream.close();
                    }
                    scepRequestMessage.setEncryptedData(ContentInfo.getInstance(seq1));
                    ctoid = scepRequestMessage.getEncryptedData().getContentType().getId();

                    if (ctoid.equals(CMSObjectIdentifiers.envelopedData.getId())) {
                        scepRequestMessage.setEnvelopedData(EnvelopedData.getInstance(ASN1Sequence.getInstance(scepRequestMessage.getEncryptedData().getContent())));
                    } else {
                        return "EncapsulatedContentInfo does not contain PKCS7 envelopedData";
                    }
                } else {
                    return "EncapsulatedContentInfo is not of type 'data'";
                }
            } else {
                return "This is not a certification request";
            }
        } else {
            return "PKCSReq does not contain 'signedData'";
        }
        return "";
    }

    public String verifySignature(ScepRequestMessage scepRequestMessage) throws CMSException, OperatorCreationException, CertificateException, NoSuchAlgorithmException, InvalidKeyException {

        if (scepRequestMessage.getMessageType() != ScepCommonHelper.SCEP_TYPE_RENEWAL && scepRequestMessage.getMessageType() == ScepCommonHelper.SCEP_TYPE_RENEWAL) {
            return "Unsupported Operation";
        }
        if (scepRequestMessage.getMessageType() == ScepCommonHelper.SCEP_TYPE_PKCSREQ) {
            String renewalChecks = renewalValidation(scepRequestMessage);
            if (renewalChecks != "Empty" && !renewalChecks.isEmpty()) {
                return renewalChecks;
            }
            return "";
        } else if (scepRequestMessage.getMessageType() == ScepCommonHelper.SCEP_TYPE_RENEWAL) {
            String renewalChecks = renewalValidation(scepRequestMessage);
            if (renewalChecks.equals("Empty")) {
                return "Unable to find renewal certificate";
            }
            return "";
        }
        return "Unsupported Operation";
    }

    private String renewalValidation(ScepRequestMessage scepRequestMessage) throws CertificateException, OperatorCreationException, CMSException, NoSuchAlgorithmException, InvalidKeyException {
        CMSSignedData cmsSignedData = new CMSSignedData(scepRequestMessage.getScepRequestMessage());
        String cn = null;
        try {
            cn = scepRequestMessage.getPkcs10().getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue().toString();
        } catch (Exception e){
            //Do Nothing
        }
        List<Certificate> certificates = certificateService.getCertificateEntityByCommonName(cn);
        for (Certificate certificate : certificates) {
            if(!(new X500Name(certificate.getSubjectDn())).equals(scepRequestMessage.getPkcs10().getSubject())) {
                continue;
            }
            X509Certificate x509Certificate = CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent());
            if (cmsSignedData.verifySignatures(new ScepVerifierProvider(x509Certificate.getPublicKey()))) {
                if (x509Certificate.getPublicKey().getEncoded().equals(((JcaPKCS10CertificationRequest) scepRequestMessage.getPkcs10()).getPublicKey().getEncoded())) {
                    return "Public Key of the renewal certificate and the CSR cannot be same";
                }
                return checkRenewalTimeframe(certificate);
            }
        }
        if (certificates.isEmpty()) {
            return "Empty";
        }
        return "";
    }

    private String checkRenewalTimeframe(Certificate certificate) {
        if (scepProfile.getRenewalThreshold() == null) {
            if (certificate.getValidity() / 2 < certificate.getExpiryInDays()) {
                return "Cannot renew certificate. Validity exceeds the half life time of certificate";
            }
        } else if (certificate.getExpiryInDays() < 0 || certificate.getStatus().equals(CertificateStatus.REVOKED)) {
            return "Cannot renew certificate. Certificate already expired / revoked";
        } else {
            if (certificate.getExpiryInDays() > scepProfile.getRenewalThreshold()) {
                return "Cannot renew certificate. Validity exceeds the configured value in scep profile";
            }
        }
        return "";
    }
}
