package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import com.czertainly.core.service.cmp.message.validator.impl.POPValidator;
import com.czertainly.core.service.cmp.util.CertUtil;
import com.czertainly.core.service.v2.ClientOperationService;
import com.czertainly.core.util.CertificateUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>Interface how to handle incoming Initial Request (ir) message from client.</p>
 *
 * <h5>Initialization request</h5>
 * <p>
 *      An Initialization request message contains as the PKIBody a
 *      <code>CertReqMessage</code>s data structure, which specifies the requested
 *      certificate(s).  Typically, <code>SubjectPublicKeyInfo</code>, <code>KeyId</code>,
 *      and <code>Validity</code> are the template fields which may be supplied for
 *      each certificate requested (see Appendix D profiles for further information).
 *      This message is intended to be used for entities when first initializing
 *      into the PKI.<br/>
 *      <b>source</b>:https://www.rfc-editor.org/rfc/rfc4210#section-5.3.1
 * </p>
 * <p>See Appendix C and [CRMF] for CertReqMessages syntax. </p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-4.2.1.1">[1] - Initial Request</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.1">[2] - CertReqMessages syntax</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-3">[3] - CertRequest syntax</a>
 * @see <a href="https://doc.primekey.com/bouncycastle/how-to-guides-pki-at-the-edge/how-to-generate-key-pairs-and-certification-requests#HowtoGenerateKeyPairsandCertificationRequests-GenerateCRMFCertificationRequestusingCMP">How to generate CRMF request</a>
 */
@Component
@Transactional
public class CrmfMessageHandler implements MessageHandler {

    @PersistenceContext
    private EntityManager entityManager;

    private static final Logger LOG = LoggerFactory.getLogger(CrmfMessageHandler.class.getName());
    private static final List<Integer> ALLOWED_TYPES = List.of(
            PKIBody.TYPE_INIT_REQ,          // ir       [0]  CertReqMessages,       --Initialization Req
            PKIBody.TYPE_CERT_REQ,          // cr       [2]  CertReqMessages,       --Certification Req
            PKIBody.TYPE_KEY_UPDATE_REQ,    // kur      [7]  CertReqMessages,       --Key Update Request
            PKIBody.TYPE_KEY_RECOVERY_REQ,  // krr      [9]  CertReqMessages,       --Key Recovery Req     (not implemented)
            PKIBody.TYPE_CROSS_CERT_REQ);   // ccr      [13] CertReqMessages,       --Cross-Cert.  Request (not implemented)

    private ClientOperationService clientOperationService;
    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) { this.clientOperationService = clientOperationService; }

    private CertificateService certificateService;
    @Autowired
    public void setCertificateService(CertificateService certificateService) { this.certificateService = certificateService; }

    /**
     *<pre>
     * CertRequest ::= SEQUENCE {
     *      certReqId     INTEGER,        -- ID for matching request and reply
     *      certTemplate  CertTemplate, --Selected fields of cert to be issued
     * 	    controls      Controls OPTIONAL
     * } -- Attributes affecting issuance
     *
     * CertTemplate ::= SEQUENCE {
     *      version      [0] Version               OPTIONAL,
     * 		    version MUST be 2 if supplied. It SHOULD be omitted.
     * 	    serialNumber [1] INTEGER               OPTIONAL,
     * 		    serialNumber MUST be omitted. This field is assigned by the CA during certificate creation.
     * 		signingAlg   [2] AlgorithmIdentifier   OPTIONAL,
     * 		    signingAlg MUST be omitted. This field is assigned by the CA during certificate creation.
     * 		issuer       [3] Name                  OPTIONAL,
     * 		    issuer is normally omitted.  It would be filled in with the CA that the requestor
     * 		    desires to issue the certificate in situations where an RA is servicing more than one CA.
     * 		validity     [4] OptionalValidity      OPTIONAL,
     *          validity is normally omitted.  It can be used to request that
     * 		    certificates either start at some point in the future or expire at
     * 		    some specific time.  A case where this field would commonly be
     * 		    used is when a cross certificate is issued for a CA.  In this case
     * 			the validity of an existing certificate would be placed in this
     * 			field so that the new certificate would have the same validity
     * 			period as the existing certificate.  If validity is not omitted,
     * 			then at least one of the sub-fields MUST be specified.
     * 		subject      [5] Name                  OPTIONAL,
     * 		    subject is filled in with the suggested name for the requestor.
     * 			This would normally be filled in by a name that has been
     * 			previously issued to the requestor by the CA.
     * 		publicKey    [6] SubjectPublicKeyInfo  OPTIONAL,
     * 		issuerUID    [7] UniqueIdentifier      OPTIONAL,
     * 		subjectUID   [8] UniqueIdentifier      OPTIONAL,
     * 		extensions   [9] Extensions            OPTIONAL
     * }
     *
     * 			OptionalValidity ::= SEQUENCE {
     * 			  notBefore  [0] Time OPTIONAL,
     * 			  notAfter   [1] Time OPTIONAL } --at least one must be present
     *
     * 			Time ::= CHOICE {
     * 			  utcTime        UTCTime,
     * 			  generalTime    GeneralizedTime }
     *</pre>
     *
     * @param request crmf-base message as ir, cr, kur, krr, ccr
     * @return response message related <code>request</code> message
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4211#section-5">...</a>
     */
    @Override
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpBaseException {
        ASN1OctetString tid = request.getHeader().getTransactionID();
        if(!ALLOWED_TYPES.contains(request.getBody().getType())) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                        "CRMF message cannot be handled, wrong message body/type, type="+request.getBody().getType());
        }

        new POPValidator()
                .validate(request, configuration);

        // -- request against CA
        ClientCertificateDataResponseDto requestedCert = issueCertificate(request, configuration);
        // -- polling against the database
        com.czertainly.core.dao.entity.Certificate polledCert = pollCertificate(tid, requestedCert);
        // clientOperationService.renewCertificate(authorityUuid, raProfileUuid, certificateUuid, request);
        // clientOperationService.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, request);

        X509Certificate parsedCert = parseCertificate(tid, polledCert);
        // -- field: extraCerts
        List<CMPCertificate> extraCerts = createExtraCerts(tid,configuration);
        // -- field: caPubs
        CMPCertificate[] caPubs = createCaPubs(tid, polledCert);

        PKIMessage response;
        try {
            response = new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(PkiMessageBuilder.createIpCpKupBody(
                            request.getBody(),
                            CMPCertificate.getInstance(parsedCert.getEncoded()),
                            caPubs))
                    .addExtraCerts(extraCerts)
                    .build();
            return response;
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "problem build response message, type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()), e);
        }
    }

    /**
     * Ask for CA in order to create/issue new certificate using CMP protocol. Attention: issuing of certificate
     * is processed in asynchronous manner (background process asked for/to get issued certificate). It means the
     * returned value is not REAL certificate but only wrapper object with identifier for given certificate.
     *
     * @param request incoming pki message which keeps CRMF data
     * @param configuration for current cmp profile
     * @return wrapper object for given certificate
     * @throws CmpProcessingException if any problem raised
     */
    private ClientCertificateDataResponseDto issueCertificate(PKIMessage request, ConfigurationContext configuration)
            throws CmpProcessingException {
        ASN1OctetString tid = request.getHeader().getTransactionID();
        try {
            ClientCertificateSignRequestDto dtoRequest = new ClientCertificateSignRequestDto();
            CertReqMessages crmf = (CertReqMessages)
                    request.getBody().getContent();
            String encodedRqs = Base64.getEncoder().encodeToString(crmf.getEncoded());
            LOG.info(">>>>> CRMF REQ  (begin) >>>>> \n {}", String.format("-> transactionId=%s,\n-> body=%s,\n-> subject=%s,\n-> issuer=%s",
                    tid,
                    encodedRqs,
                    crmf.toCertReqMsgArray()[0].getCertReq().getCertTemplate().getSubject(),
                    crmf.toCertReqMsgArray()[0].getCertReq().getCertTemplate().getIssuer()));
            dtoRequest.setRequest(encodedRqs);
            dtoRequest.setFormat(CertificateRequestFormat.CRMF);
            RaProfile raProfile = configuration.getProfile().getRaProfile();
            // -- (1)certification request (ask for issue)
            return clientOperationService.issueCertificate(
                    SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()),
                    raProfile.getSecuredUuid(),
                    dtoRequest);
        } catch (NotFoundException|CertificateException|IOException|
                 NoSuchAlgorithmException|InvalidKeyException|CertificateOperationException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "cannot issue certificate", e);
        } finally {
            LOG.info("<<<<< CRMF REQ  (  end) <<<<<");
        }
    }

    /**
     * Convert asynchronous behaviour (issuing certificate) to synchronous (cmp client ask for
     * certificate) using polling certificate until certificate
     *
     * @param tid processing transaction id, see {@link PKIHeader#getTransactionID()}
     * @param requestedCert issued certificate from CA
     * @return null if certificate (with state={@link CertificateState#ISSUED}) not found
     * @throws CmpProcessingException if polling of certificate failed
     */
    private com.czertainly.core.dao.entity.Certificate pollCertificate(ASN1OctetString tid, ClientCertificateDataResponseDto requestedCert)
            throws CmpProcessingException {
        LOG.debug(">>>>> CERT POLL (begin) >>>>> ");
        com.czertainly.core.dao.entity.Certificate polledCert;
        SecuredUUID certUUID = SecuredUUID.fromString(requestedCert.getUuid());
        try{
            long startRequest = System.currentTimeMillis();
            long endRequest;
            int timeout = 1000*10;//in millis, TODO vytahnout do konfigurace asi jenom nasobitel(zde *10), tzn. v sekundach!
            int counter = 0;//counter for logging purpose only
            do {
                LOG.debug(">>>>> TID={} POLL=[{}] | polling request: certificate with uuid={}", tid, counter, certUUID);
                // -- (2)certification polling (ask for created certificate entity)
                polledCert = certificateService.getCertificateEntity(certUUID);
                LOG.debug("<<<<< TID={} POLL=[{}] | polling result: certificate entity in state {}, uuid={}", tid, counter, polledCert.getState(), certUUID);
                endRequest = System.currentTimeMillis();
                counter++;
                if(polledCert != null) entityManager.refresh(polledCert);//get entity from db (instead from hibernate 1lvl cache)
                if(counter > 1) TimeUnit.MILLISECONDS.sleep(1000);
            } while ( endRequest - startRequest < timeout
                    && (polledCert != null && !CertificateState.ISSUED.equals(polledCert.getState())));
        } catch(InterruptedException e) {
                throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                        "cannot poll certificate - processing thread has been interrupted", e);
        } catch (NotFoundException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "issued certificate from CA cannot be found, uuid="+certUUID);
        } finally {
            LOG.debug("<<<<< CERT polling (  end) <<<<< ");
        }

        if(polledCert == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "result of polling cannot be null certificate");
        }
        if (!CertificateState.ISSUED.equals(polledCert.getState())) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    String.format("polled certificate is not at valid state (expected=ISSUED), retrieved=%s", polledCert.getState()));
        }
        return polledCert;
    }

    /**
     * Get certificate entity (db state) and convert
     * @param tid
     * @param polledCert
     * @return
     * @throws CmpProcessingException
     */
    private X509Certificate parseCertificate(ASN1OctetString tid, com.czertainly.core.dao.entity.Certificate polledCert)
            throws CmpProcessingException {
        if(polledCert == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "unable to parse empty certificate");
        }
        if(polledCert.getCertificateContent() == null || polledCert.getCertificateContent().getContent().isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "unable to parse empty certificate content");
        }

        try {
            return CertificateUtil.parseCertificate(polledCert.getCertificateContent().getContent());
        } catch (CertificateException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "unable to parse given certificate content", e);
        }
    }

    /**
     * @param configuration keep list of pre-defined certificates for given profile
     * @return list of certificates for CertRepMessage.extraCerts field
     * @throws CmpProcessingException
     */
    private List<CMPCertificate> createExtraCerts(ASN1OctetString tid, ConfigurationContext configuration)
            throws CmpProcessingException {
        List<CMPCertificate> extraCerts = new ArrayList<>();
        try { for (X509Certificate certificate : configuration.getExtraCertsCertificateChain()) {
            extraCerts.add(CertUtil.toCmpCertificate(certificate)); } }
        catch (CertificateException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "problem with create/fill 'extraCerts' field");
        }
        return extraCerts;
    }

    /**
     * <p>See Section 5.3.4 for CertRepMessage syntax.  Note that if the PKI
     *    Message Protection is "shared secret information" (see Section
     *    5.1.3), then any certificate transported in the caPubs field may be
     *    directly trusted as a root CA certificate by the initiator.</p>
     * <p>Scope: ip, cp, kup, ccp</p>
     * <p>Location: (optional) CertRepMessage.caPubs</p>
     *
     * <pre>
     *     new CMPCertificate[2];
     *             caPubs[1] = CMPCertificate.getInstance(CA_ROOT_CERT.getEncoded());
     *             caPubs[0] = CMPCertificate.getInstance(CA_INTERMEDIATE_CERT.getEncoded());
     * </pre>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.2">...</a>
     */
    private CMPCertificate[] createCaPubs(ASN1OctetString tid, Certificate certificate)
            throws CmpProcessingException {
        CMPCertificate[] caPubs = null;
        try {
            List<X509Certificate> caChain = loadCertificateChain(tid, certificate);
            int caChainCounter = 0;
            for (X509Certificate cr : caChain) {
                caPubs[caChainCounter]=CertUtil.toCmpCertificate(cr);
                ++caChainCounter;
            }
        } catch (NotFoundException e) {
            LOG.info("CA chain not found, caPubs will be null");
        } catch (CertificateException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "problem with create 'caPubs' field", e);
        }
        return caPubs;
    }

    private List<X509Certificate> loadCertificateChain(ASN1OctetString tid, Certificate leafCertificate)
            throws NotFoundException, CmpProcessingException {
        ArrayList<X509Certificate> certificateChain = new ArrayList<>();
        for (CertificateDetailDto certificate : certificateService.getCertificateChain(leafCertificate.getSecuredUuid(), true).getCertificates()) {
            // only certificate with valid status should be used
            if (!certificate.getValidationStatus().equals(CertificateValidationStatus.VALID)) {
                throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                        String.format("Certificate is not valid. UUID: %s, Fingerprint: %s, Status: %s",
                        certificate.getUuid(),
                        certificate.getFingerprint(),
                        certificate.getValidationStatus().getLabel()));
            }
            try {
                certificateChain.add(CertificateUtil.parseCertificate(certificate.getCertificateContent()));
            } catch (CertificateException e) {
                // This should not happen
                throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                        "Failed to parse certificate content: " +
                        certificate.getCertificateContent());
            }
        }

        return certificateChain;
    }

}
