package com.czertainly.core.service.cmp.impl;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.core.api.cmp.error.*;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.provider.key.CzertainlyPublicKey;
import com.czertainly.core.service.CryptographicKeyService;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.PkiMessageError;
import com.czertainly.core.service.cmp.message.handler.CertConfirmMessageHandler;
import com.czertainly.core.service.cmp.message.handler.CrmfMessageHandler;
import com.czertainly.core.service.cmp.message.handler.RevocationMessageHandler;
import com.czertainly.core.service.cmp.message.validator.impl.BodyValidator;
import com.czertainly.core.service.cmp.message.validator.impl.HeaderValidator;
import com.czertainly.core.service.cmp.message.validator.impl.ProtectionValidator;
import com.czertainly.core.service.cmp.mock.MockCaImpl;
import com.czertainly.core.service.cmp.profiles.Mobile3gppProfileContext;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.AttributeOperation;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.cmp.CmpProfile;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.cmp.CmpProfileRepository;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.cmp.CmpService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import static com.czertainly.core.service.cmp.CmpConstants.*;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class CmpServiceImpl implements CmpService {

    private static final Logger LOG = LoggerFactory.getLogger(CmpServiceImpl.class.getName());

    // -- RA PROFILE
    private boolean raProfileBased;
    private RaProfile raProfile;
    private RaProfileRepository raProfileRepository;
    @Autowired
    public void setRaProfileRepository(RaProfileRepository raProfileRepository) { this.raProfileRepository = raProfileRepository; }

    // -- CMP PROFILE
    private CmpProfile cmpProfile;
    private CmpProfileRepository cmpProfileRepository;
    @Autowired
    public void setCmpProfileRepository(CmpProfileRepository cmpProfileRepository) { this.cmpProfileRepository = cmpProfileRepository; }

    // -- CERTIFICATE
    private List<X509Certificate> caCertificateChain = new ArrayList<>();
    private X509Certificate recipient;
    private List<RequestAttributeDto> issueAttributes;
    private CertificateService certificateService;
    @Autowired
    public void setCertificateService(CertificateService certificateService) { this.certificateService = certificateService; }

    // -- CRYPTO
    private CryptographicOperationsApiClient cryptographicOperationsApiClient;
    @Autowired
    public void setCryptographicOperationsApiClient(CryptographicOperationsApiClient cryptographicOperationsApiClient) {
        this.cryptographicOperationsApiClient = cryptographicOperationsApiClient;
    }
    private CryptographicKeyService cryptographicKeyService;
    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    // -- HANDLER, VALIDATORS
    private CrmfMessageHandler crmfMessageHandler;
    @Autowired
    public void setCrmfMessageHandler(CrmfMessageHandler crmfMessageHandler) { this.crmfMessageHandler = crmfMessageHandler; }
    private CertConfirmMessageHandler certConfirmMessageHandler;
    @Autowired
    public void setCertConfirmMessageHandler(CertConfirmMessageHandler certConfirmMessageHandler) { this.certConfirmMessageHandler = certConfirmMessageHandler; }
    private RevocationMessageHandler revocationMessageHandler;
    @Autowired
    public void setRevocationMessageHandler(RevocationMessageHandler revocationMessageHandler) { this.revocationMessageHandler = revocationMessageHandler; }

    @Autowired private HeaderValidator headerValidator;
    @Autowired private BodyValidator bodyValidator;
    @Autowired private ProtectionValidator protectionValidator;

    // -- OTHERS
    private AttributeEngine attributeEngine;
    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    public ResponseEntity<Object> handlePost(String profileName, byte[] request) throws CmpBaseException {
        boolean verbose = false;
        try { MockCaImpl.init();} catch (Exception e) {
            throw new IllegalStateException("mock of CA cannot start", e);
        }

        init(profileName);
        //validateProfile(profileName);

        final PKIMessage pkiRequest;
        try { pkiRequest = PKIMessage.getInstance(request); }
        catch (IllegalArgumentException e) {
            LOG.error("PN={} | request message cannot be parsed", profileName, e);
            return buildBadRequest(PkiMessageError.unprotectedMessage(
                    PKIFailureInfo.badRequest,
                    ImplFailureInfo.CMPSRV100));
        }
        ASN1OctetString tid = pkiRequest.getHeader().getTransactionID();
        String requestAsString = PkiMessageDumper.dumpPkiMessage(pkiRequest);
        String logPrefix = PkiMessageDumper.logPrefix(pkiRequest, profileName);
        LOG.info("{} | request processing: {}",
                logPrefix,
                PkiMessageDumper.dumpPkiMessage(verbose, pkiRequest));
        //LOG.info("{} | {}", logPrefix, Base64.getEncoder().encodeToString(request));

        // -- (processing) part
        CmpProfile profile = cmpProfileRepository.findByName(profileName).orElse(null);//najdi v databasi
        //*
        CzertainlyProvider czertainlyProvider = CzertainlyProvider.getInstance(profile.getName(),
                true, cryptographicOperationsApiClient);
        CryptographicKey key = profile.getSigningCertificate().getKey();
        CryptographicKeyItem item = cryptographicKeyService.getKeyItemFromKey(key, KeyType.PRIVATE_KEY);
        // Get the private key from the configuration of cmp Profile
        CzertainlyPrivateKey signerPrivateKey = new CzertainlyPrivateKey(
                key.getTokenInstanceReference().getTokenInstanceUuid(),
                item.getKeyReferenceUuid().toString(),
                key.getTokenInstanceReference().getConnector().mapToDto(),
                item.getKeyAlgorithm().getLabel()
        );
        CryptographicKeyItem itemPub = cryptographicKeyService.getKeyItemFromKey(key, KeyType.PUBLIC_KEY);
        CzertainlyPublicKey x = new CzertainlyPublicKey(
                key.getTokenInstanceReference().getTokenInstanceUuid(),
                itemPub.getKeyReferenceUuid().toString(),
                new ConnectorDto());
        X509Certificate signerCertificate;
        try {
            Certificate cert = profile.getSigningCertificate();
            LOG.info("CERT = {}, {}", cert.getFingerprint(), cert.getSerialNumber());
            signerCertificate = CertificateUtil.getX509Certificate(cert.getCertificateContent().getContent());
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
        //*/

        ConfigurationContext config3gppProfile = new Mobile3gppProfileContext(profile, pkiRequest,
                czertainlyProvider, signerPrivateKey, signerCertificate);
        try {
            PKIMessage pkiResponse;

            headerValidator.validate(pkiRequest, config3gppProfile);
            bodyValidator.validate(pkiRequest, config3gppProfile);
            protectionValidator.validateIn(pkiRequest, config3gppProfile);

            //see https://www.rfc-editor.org/rfc/rfc4210#section-5.1.2, PKI Message Body
            switch (pkiRequest.getBody().getType()) {
                case PKIBody.TYPE_INIT_REQ:                        // ( 1)       ir, Initial Request; CertReqMessages
                case PKIBody.TYPE_CERT_REQ:                        // ( 2)       cr, Certification Req; CertReqMessages
                case PKIBody.TYPE_KEY_UPDATE_REQ:                  // ( 7)      kur, Key Update Request; CertReqMessages
                    pkiResponse = crmfMessageHandler.handle(pkiRequest, config3gppProfile); break;
                case PKIBody.TYPE_REVOCATION_REQ:                  // (11)       rr, Revocation Request; RevReqContent
                    pkiResponse = revocationMessageHandler.handle(pkiRequest, config3gppProfile); break;
                case PKIBody.TYPE_CERT_CONFIRM:                    // (24) certConf, Certificate confirm; CertConfirmContent
                    pkiResponse = certConfirmMessageHandler.handle(pkiRequest, config3gppProfile); break;
                case PKIBody.TYPE_CROSS_CERT_REQ:
                case PKIBody.TYPE_KEY_RECOVERY_REQ:
                case PKIBody.TYPE_GEN_MSG:
                case PKIBody.TYPE_NESTED:
                case PKIBody.TYPE_P10_CERT_REQ:
                case PKIBody.TYPE_POLL_REQ:
                case PKIBody.TYPE_REVOCATION_ANN:
                case PKIBody.TYPE_CERT_ANN:
                case PKIBody.TYPE_CA_KEY_UPDATE_ANN:
                case PKIBody.TYPE_CRL_ANN:
                case PKIBody.TYPE_POPO_CHALL:
                    throw new CmpProcessingException(PKIFailureInfo.badRequest, ImplFailureInfo.CMPVALMSG200);
                default:
                    LOG.error("{} | unknown cmp message type", PkiMessageDumper.logPrefix(pkiRequest, profileName));
                    throw new CmpProcessingException(PKIFailureInfo.badRequest,
                            ImplFailureInfo.CMPVALMSG201);
            }

            if(pkiResponse == null) {
                throw new CmpProcessingException(
                        PKIFailureInfo.systemFailure,
                        String.format(" %s | general problem while handling PKIMessage", logPrefix));
            }
            LOG.info("{} | response processed: {}",
                    PkiMessageDumper.logPrefix(pkiResponse, profileName),
                    PkiMessageDumper.dumpPkiMessage(verbose, pkiResponse));

            headerValidator.validate(pkiResponse, config3gppProfile);
            bodyValidator.validate(pkiResponse, config3gppProfile);
            protectionValidator.validateOut(pkiResponse, config3gppProfile);

            //if(true)throw new CmpProcessingException(1,"kurvitko");

            return buildOk(pkiResponse);
        } catch (CmpBaseException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(pkiRequest.getHeader(), e.toPKIBody());
            LOG.error("{} | processing failed: \n\nrequest:\n {}\n response:\n {}", logPrefix,
                    requestAsString, PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return buildBadRequest(pkiResponse);
        } catch (IOException e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(
                    pkiRequest.getHeader(),
                    PKIFailureInfo.badDataFormat,
                    ImplFailureInfo.CMPSRV101);
            LOG.error("{} | parsing failed: \n\nrequest:\n {}\n response:\n {}", logPrefix,
                    requestAsString, PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return buildBadRequest(pkiResponse);
        } catch (Exception e) {
            PKIMessage pkiResponse = PkiMessageError.unprotectedMessage(pkiRequest.getHeader(), e);
            LOG.error("{} | handling failed: \n\nrequest:\n {}\n response:\n {}", logPrefix,
                    requestAsString, PkiMessageDumper.dumpPkiMessage(pkiResponse), e);
            return buildBadRequest(pkiResponse);
        }
    }

    private ResponseEntity buildBadRequest(PKIMessage pkiMessage) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("Content-Type", HTTP_HEADER_CONTENT_TYPE)
                .body(PkiMessageError.encode(pkiMessage));
    }
    private ResponseEntity buildOk(PKIMessage pkiMessage) throws IOException {
        return ResponseEntity
                .status(HttpStatus.OK)
                .header("Content-Type", HTTP_HEADER_CONTENT_TYPE)
                .body(pkiMessage.getEncoded());
    }

    private void init(String profileName) throws CmpConfigurationException {
        this.raProfileBased = ServletUriComponentsBuilder.fromCurrentRequestUri().build()
                .toUriString().contains("/raProfile/");
        if (raProfileBased) {
            raProfile = raProfileRepository.findByName(profileName).orElse(null);
            if (raProfile == null) {
                return;
            }
            cmpProfile = raProfile.getCmpProfile();
            String attributesJson = raProfile.getProtocolAttribute() != null ? raProfile.getProtocolAttribute().getCmpIssueCertificateAttributes() : null;
            issueAttributes = AttributeDefinitionUtils.getClientAttributes(AttributeDefinitionUtils.deserialize(attributesJson, DataAttribute.class));
        } else {
            cmpProfile = cmpProfileRepository.findByName(profileName).orElse(null);
            if (cmpProfile == null) {
                return;
            }
            raProfile = cmpProfile.getRaProfile();
            if (raProfile == null) {
                return;
            }
            issueAttributes = attributeEngine.getRequestObjectDataAttributesContent(cmpProfile.getRaProfile().getAuthorityInstanceReference().getConnectorUuid(), AttributeOperation.CERTIFICATE_ISSUE, Resource.CMP_PROFILE, cmpProfile.getUuid());
        }

        Certificate cmpCaCertificate = cmpProfile.getSigningCertificate();
        if (cmpCaCertificate == null) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+profileName+" | CMP profile does not have any associated CA certificate");
        }

        try {
            this.recipient = CertificateUtil.parseCertificate(cmpCaCertificate.getCertificateContent().getContent());
        } catch (CertificateException e) {
            // This should not occur
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+profileName+" | Error converting the certificate to x509 object");
        }
        try {
            this.caCertificateChain = loadCertificateChain(cmpCaCertificate);
        } catch (NotFoundException e) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+profileName+" | Failed to load certificate chain of CMP profile CA certificate");
        }
        LOG.debug("PN={} | CMP service initialized: isRaProfileBased: {}, raProfile: {}, cmpProfile: {}", profileName, raProfileBased, raProfile, cmpProfile);
    }

    private void validateProfile(String incomingProfileName) throws CmpConfigurationException {
        validateCmpProfile(incomingProfileName);
        validateRaProfile(incomingProfileName);
    }

    private void validateCmpProfile(String incomingProfileName) throws CmpConfigurationException {
        if (cmpProfile == null) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+incomingProfileName+" | Requested CMP Profile not found");
        }
        /*if (!cmpProfile.isEnabled()) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+incomingProfileName+" | CMP Profile is not enabled");
        }*/
        if (cmpProfile.getSigningCertificate() == null) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+incomingProfileName+" | CMP Profile does not have any associated CA certificate for signature");
        }
        if (!CertificateUtil.isCertificateCmpAcceptable(cmpProfile.getSigningCertificate())) {
           throw new CmpConfigurationException(PKIFailureInfo.systemFailure,"CMP Profile does not have associated acceptable CA certificate");
        }
        if (!raProfileBased && cmpProfile.getRaProfile() == null) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+incomingProfileName+" | CMP Profile does not contain associated RA Profile");
        }
    }

    private void validateRaProfile(String incomingProfileName) throws CmpConfigurationException {
        if (raProfile == null) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+incomingProfileName+" | Requested RA Profile not found");
        }
        if (!raProfile.getEnabled()) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+incomingProfileName+" | RA Profile is not enabled");
        }
        if (raProfileBased && raProfile.getScepProfile() == null) {
            throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                    "PN="+incomingProfileName+" | RA Profile does not contain associated CMP Profile");
        }
    }

    private List<X509Certificate> loadCertificateChain(Certificate leafCertificate) throws CmpConfigurationException, NotFoundException {
        ArrayList<X509Certificate> certificateChain = new ArrayList<>();
        for (CertificateDetailDto certificate : certificateService.getCertificateChain(leafCertificate.getSecuredUuid(), true).getCertificates()) {
            // only certificate with valid status should be used
            if (!certificate.getValidationStatus().equals(CertificateValidationStatus.VALID)) {
                throw new CmpConfigurationException(PKIFailureInfo.systemFailure,
                        String.format("Certificate is not valid. UUID: %s, Fingerprint: %s, Status: %s",
                        certificate.getUuid(),
                        certificate.getFingerprint(),
                        certificate.getValidationStatus().getLabel()));
            }
            try {
                certificateChain.add(CertificateUtil.parseCertificate(certificate.getCertificateContent()));
            } catch (CertificateException e) {
                // This should not happen
                throw new IllegalArgumentException("PN="+this.cmpProfile.getName()+" | Failed to parse certificate content: " +
                        certificate.getCertificateContent());
            }
        }

        return certificateChain;
    }
}
