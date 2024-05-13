package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.cmp.CmpTransactionState;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.message.CmpTransactionService;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import com.czertainly.core.service.cmp.message.validator.impl.POPValidator;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertReqMsg;
import org.bouncycastle.asn1.crmf.CertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Handle (czertainly) supported CRMF-based message - ir/cr/kur; concrete handle (how to get/update certificate)
 * delegates to another specific handlers, e.g. {@link CrmfIrCrMessageHandler} or {@link CrmfKurMessageHandler}.
 *
 * @see CrmfIrCrMessageHandler
 * @see CrmfKurMessageHandler
 */
@Component
@Transactional//(propagation = Propagation.REQUIRES_NEW)
public class CrmfMessageHandler implements MessageHandler<PKIMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(CrmfMessageHandler.class.getName());
    private static final List<Integer> ALLOWED_TYPES = List.of(
            PKIBody.TYPE_INIT_REQ,          // ir       [0]  CertReqMessages,       --Initialization Req
            PKIBody.TYPE_CERT_REQ,          // cr       [2]  CertReqMessages,       --Certification Req
            PKIBody.TYPE_KEY_UPDATE_REQ,    // kur      [7]  CertReqMessages,       --Key Update Request
            PKIBody.TYPE_KEY_RECOVERY_REQ,  // krr      [9]  CertReqMessages,       --Key Recovery Req     (not implemented)
            PKIBody.TYPE_CROSS_CERT_REQ);   // ccr      [13] CertReqMessages,       --Cross-Cert.  Request (not implemented)

    private CertificateService certificateService;
    @Autowired
    public void setCertificateService(CertificateService certificateService) { this.certificateService = certificateService; }

    private PollFeature pollFeature;
    @Autowired
    public void setPollFeature(PollFeature pollFeature) { this.pollFeature = pollFeature; }

    private CmpTransactionService cmpTransactionService;
    @Autowired
    private void setCmpTransactionService(CmpTransactionService cmpTransactionService) { this.cmpTransactionService = cmpTransactionService; }

    @Autowired private CrmfIrCrMessageHandler crmfIrCrMessageHandler;
    @Autowired private CrmfKurMessageHandler kurMessageHandler;

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
        String msgBodyType = PkiMessageDumper.msgTypeAsString(request);
        if(!ALLOWED_TYPES.contains(request.getBody().getType())) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "CRMF message cannot be handled - wrong type, type="+msgBodyType);
        }

        new POPValidator()
                .validate(request, configuration);


        CertReqMessages certReqMessages = (CertReqMessages) request.getBody().getContent();
        CertReqMsg[] certRequests = certReqMessages.toCertReqMsgArray();
        List<CMPCertificate> listOfCaCerts = null;
        List<CertResponse> listOfCertResponses = new ArrayList<>();
        // -- czertainly is (right now) able to handle only (first) one, see {@link CrmfCertificateRequest}
        //for(var certRequest : certRequests) {

            List<CmpTransaction> trx = cmpTransactionService.findByTransactionId(tid.toString());
            if(!trx.isEmpty()) {
                throw new CmpProcessingException(tid, PKIFailureInfo.transactionIdInUse,
                        "crmf processing failed - given transaction is already used");
            }

            CertRequest crmf = certRequests[0].getCertReq();
            ASN1Integer serialNumber = crmf.getCertTemplate().getSerialNumber();

            ClientCertificateDataResponseDto requestedCert = switch (request.getBody().getType()) {
                case PKIBody.TYPE_INIT_REQ, PKIBody.TYPE_CERT_REQ -> crmfIrCrMessageHandler.handle(request, configuration);
                case PKIBody.TYPE_KEY_UPDATE_REQ -> kurMessageHandler.handle(request, configuration);
                case PKIBody.TYPE_KEY_RECOVERY_REQ, PKIBody.TYPE_CROSS_CERT_REQ ->
                        throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                                "CRMF message cannot be handled - type is not supported, type=" + msgBodyType);
                default -> null;
            };
            if(requestedCert == null) {
                throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                        "certificate was not provided");
            }
            // -- polling against the database
            Certificate polledCert = pollFeature.pollCertificate(tid,
                    serialNumber==null?null:serialNumber.getValue().toString(16), requestedCert.getUuid(),
                    CertificateState.ISSUED);

            // -- parse polled certificate (as X509)
            X509Certificate parsedCert = parseCertificate(tid, polledCert);
            // -- field: caPubs
            if(listOfCaCerts == null){
                listOfCaCerts = createListCaPubs(tid, polledCert);
            } else {
                List<CMPCertificate> list = createListCaPubs(tid, polledCert);
                if(!list.isEmpty()) listOfCaCerts.addAll(list);
            }

            // -- store as transaction (tid+uuid of cert)
            CmpTransactionState trxState = switch (request.getBody().getType()) {
                case PKIBody.TYPE_INIT_REQ, PKIBody.TYPE_CERT_REQ -> crmfIrCrMessageHandler.getTransactionState();
                case PKIBody.TYPE_KEY_UPDATE_REQ -> kurMessageHandler.getTransactionState();
                case PKIBody.TYPE_KEY_RECOVERY_REQ, PKIBody.TYPE_CROSS_CERT_REQ ->
                        throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                                "CRMF message cannot be handled - type is not supported, type=" + msgBodyType);
                default -> null;
            };
            cmpTransactionService.save(cmpTransactionService.createTransactionEntity(
                    tid.toString(),
                    configuration.getProfile(),
                    polledCert.getUuid().toString(),
                    trxState));

            // -- create cert response
            try {
                CMPCertificate cmpCertificate = CMPCertificate.getInstance(parsedCert.getEncoded());
                listOfCertResponses.add(new CertResponse(
                                crmf.getCertReqId(),
                                new PKIStatusInfo(PKIStatus.granted),
                                new CertifiedKeyPair(new CertOrEncCert(cmpCertificate)),
                                null));
            } catch (CertificateEncodingException e) {
                throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                        "SN="+parsedCert.getSerialNumber()+" | CRMF cmp certificate encoding error", e);
            }
        //}

        // -- field 'caPubs'
        CMPCertificate[] caPubs = null;
        if(listOfCaCerts != null && !listOfCaCerts.isEmpty()) {
            caPubs = new CMPCertificate[listOfCaCerts.size()];
            listOfCaCerts.toArray(caPubs);
        }
        // -- field 'certResMessages'
        if(listOfCertResponses == null && listOfCertResponses.isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "CRMF request failed - no issued certificates found");
        }
        CertResponse certResponses[] = new CertResponse[listOfCertResponses.size()];
        listOfCertResponses.toArray(certResponses);

        // -- create response
        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(PkiMessageBuilder.createIpCpKupBody(
                            request.getBody(),
                            certResponses,
                            caPubs))
                    .addExtraCerts(null)
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "CRMF message cannot be build, type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()), e);
        }
    }

    /**
     * Get certificate entity (db state) and convert
     * @param tid
     * @param polledCert
     * @return current db certificate entity converted into x509 format
     * @throws CmpProcessingException
     */
    private X509Certificate parseCertificate(ASN1OctetString tid, Certificate polledCert)
            throws CmpProcessingException {
        if(polledCert == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "unable to parse empty certificate");
        }
        String serialNumber = polledCert.getSerialNumber();
        CertificateContent polledCertContent = polledCert.getCertificateContent();
        if(polledCertContent == null || polledCertContent.getContent().isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "SN="+serialNumber+" | unable to parse empty certificate content");
        }

        try { return CertificateUtil.parseCertificate(polledCertContent.getContent()); }
        catch (CertificateException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "SN="+serialNumber+" | unable to parse given certificate content", e);
        }
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
     * @param tid, transactionID of given flow (request/response)
     * @param leafCertificate which is ca chain built from
     * @return null, if ca chain is empty
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.2">...</a>
     */
    private List<CMPCertificate> createListCaPubs(ASN1OctetString tid, Certificate leafCertificate)
            throws CmpProcessingException {
        try {
            List<X509Certificate> caChain = loadCaCertificateChain(tid, leafCertificate);
            List<CMPCertificate> converted = new LinkedList<>();
            for (X509Certificate cr : caChain) {
                converted.add(CertificateUtil.toCmpCertificate(cr));
            }
            return converted;
        }  catch (CertificateException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "SN="+leafCertificate.getSerialNumber()+" | problem with convert of CA: from (x509) certificates to (cmp) certificates", e);
        }
    }

    private List<X509Certificate> loadCaCertificateChain(ASN1OctetString tid, Certificate leafCertificate)
            throws CmpProcessingException {
        ArrayList<X509Certificate> certificateChain = new ArrayList<>();

        // -- try to find CA chain from leaf certificate (can be null/empty==not found)
        List<CertificateDetailDto> caChain;
        String leafCertificateSerialNumber = leafCertificate.getSerialNumber();
        try {
            caChain = certificateService.getCertificateChain(
                    leafCertificate.getSecuredUuid(), true).getCertificates();
        } catch (NotFoundException e) {
            LOG.error("TID={}, SN={} | CA chain is empty (not found)", tid, leafCertificateSerialNumber);
            return certificateChain;
        }

        // -- parse found CA certificate(s)
        for (CertificateDetailDto certificate : caChain) {
            // only certificate with valid status should be used
            if (!certificate.getValidationStatus().equals(CertificateValidationStatus.VALID)) {
                throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                        String.format("SN=%s | CA Certificate is not valid. UUID: %s, Fingerprint: %s, Status: %s",
                                leafCertificateSerialNumber,
                                certificate.getUuid(),
                                certificate.getFingerprint(),
                                certificate.getValidationStatus().getLabel()));
            }
            try { certificateChain.add(CertificateUtil.parseCertificate(certificate.getCertificateContent())); }
            catch (CertificateException e) { // This should not happen
                throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                        String.format("SN=%s | failed to parse CA certificate (caSN=%s); content=%s",
                                leafCertificateSerialNumber,
                                certificate.getSerialNumber(),
                                certificate.getCertificateContent()));
            }
        }
        return certificateChain;
    }

}
