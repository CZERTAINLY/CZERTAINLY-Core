package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.core.authority.CertificateRevocationReason;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.PkiMessageDumper;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import com.czertainly.core.service.v2.ClientOperationService;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertId;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.hibernate.validator.constraintvalidators.RegexpURLValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/**
 * <p>5.3.9.  Revocation Request Content</p>
 * <p>
 *    When requesting revocation of a certificate (or several
 *    certificates), the following data structure is used.  The name of the
 *    requester is present in the PKIHeader structure.</p>
 *
 * <pre>
 *     RevReqContent ::= SEQUENCE OF RevDetails
 *
 *     RevDetails ::= SEQUENCE {
 *         certDetails         CertTemplate,
 *         crlEntryDetails     Extensions       OPTIONAL
 *     }
 * </pre>
 *
 * <p>5.3.10.  Revocation Response Content</p>
 * <p>
 *    The revocation response is the response to the above message.  If
 *    produced, this is sent to the requester of the revocation.  (A
 *    separate revocation announcement message MAY be sent to the subject
 *    of the certificate for which revocation was requested.)</p>
 * <pre>
 *      RevRepContent ::= SEQUENCE {
 *          status        SEQUENCE SIZE (1..MAX) OF PKIStatusInfo,
 *          revCerts  [0] SEQUENCE SIZE (1..MAX) OF CertId OPTIONAL,
 *          crls      [1] SEQUENCE SIZE (1..MAX) OF CertificateList
 *                        OPTIONAL
 *      }
 * </pre>
 */
@Component
@Transactional
public class RevocationMessageHandler implements MessageHandler<PKIMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(RevocationMessageHandler.class.getName());

    private ClientOperationService clientOperationService;

    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) { this.clientOperationService = clientOperationService; }

    private CertificateRepository certificateRepository;
    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) { this.certificateRepository = certificateRepository; }

    private PollFeature pollFeature;
    @Autowired
    public void setPollFeature(PollFeature pollFeature) { this.pollFeature = pollFeature; }

    @Override
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpBaseException {
        if(PKIBody.TYPE_REVOCATION_REQ!=request.getBody().getType()) {
            throw new CmpProcessingException(
                    PKIFailureInfo.systemFailure,
                    "revocation (rr) message cannot be handled - unsupported body rawType="+request.getBody().getType()+", type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()) +"; only type=cerfConf is supported");
        }
        ASN1OctetString tid = request.getHeader().getTransactionID();
        RevReqContent revBody = (RevReqContent) request.getBody().getContent();

        RevDetails[] revocations = revBody.toRevDetailsArray();
        int revocationCount = revocations.length;
        RevRepContentBuilder revocationResponseBuilder = new RevRepContentBuilder();
        LOG.debug("TID={} | revocations started (count={})", tid, revocationCount);
        for (var revocation : revocations) {
            //RevDetails revocation = revBody.toRevDetailsArray()[0];
            CertTemplate certDetails = revocation.getCertDetails();
            CertId certId = new CertId(new GeneralName(certDetails.getIssuer()),
                    certDetails.getSerialNumber());
            try {
                Certificate certificate = getCertificate(revocation, tid);
                revokeCertificate(tid, revocation, certificate, configuration);
                pollFeature.pollCertificate(tid,
                        certificate.getSerialNumber(), certificate.getUuid().toString(), CertificateState.REVOKED);
                revocationResponseBuilder.add(
                        new PKIStatusInfo(PKIStatus.revocationNotification), certId);
                LOG.trace("TID={}, SN={} | revocations of certificate is done (remaining={})", tid, getSerialNumber(revocation), --revocationCount);
            } catch (Exception e) {
                LOG.error("TID={}, SN={} | revocation of certificate failed, reason={}", tid, getSerialNumber(revocation), e.getLocalizedMessage(), e);
                revocationResponseBuilder.add(
                        new PKIStatusInfo(PKIStatus.rejection/*rejection??*/), certId);
            }
        }
        if(revocationCount!=0) {
            LOG.error("TID={} | some revocations failed (count={})", tid, revocationCount);
        }

        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(new PKIBody(PKIBody.TYPE_REVOCATION_REP, revocationResponseBuilder.build()))
                    .addExtraCerts(null)
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    " problem build revocation response message", e);
        }
    }

    private Certificate getCertificate(RevDetails revocation, ASN1OctetString tid)
            throws CmpProcessingException {
        String serialNumber = getSerialNumber(revocation);
        Optional<Certificate> certificate = certificateRepository.findBySerialNumberIgnoreCase(serialNumber);
        if (certificate.isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    "SN="+getSerialNumber(revocation)+" | certificate for revocation could not be found");
        }
        return certificate.get();
    }

    private String getSerialNumber(RevDetails revocation) {
        ASN1Integer serialNumber = revocation.getCertDetails().getSerialNumber();//nul tady nenastavne viz. BodyRevocationValidator
        return serialNumber.getValue().toString(16);
    }

    private CertificateRevocationReason getReason(RevDetails revocation, ASN1OctetString tid)
            throws CmpProcessingException {
        Extensions crlEntryDetails = revocation.getCrlEntryDetails();
        Extension reasonCodeExt = crlEntryDetails.getExtension(Extension.reasonCode);
        Long reasonCode = ASN1Enumerated.getInstance(reasonCodeExt.getParsedValue())
                .getValue().longValue();
        CertificateRevocationReason reason = CertificateRevocationReason.UNSPECIFIED;
        if(reasonCode != null) {
            CertificateRevocationReason.fromReasonCode(reasonCode.intValue());
        }
        if(reason == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    "SN="+getSerialNumber(revocation)+" | revocation reason could not be found");
        }
        return reason;
    }

    private void revokeCertificate(ASN1OctetString tid, RevDetails revocation, Certificate certificate,
                                   ConfigurationContext configuration)
            throws CmpProcessingException {
        String sn = certificate.getSerialNumber();
        if (CertificateState.REVOKED.equals(certificate.getState())) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badCertTemplate,
                    "SN="+sn+" | Certificate is already revoked");
        }
        CertificateRevocationReason reason = getReason(revocation, tid);
        try {
            ClientCertificateRevocationDto dto = new ClientCertificateRevocationDto();
            dto.setReason(reason);
            dto.setAttributes(configuration.getClientOperationAttributes(true));
            RaProfile raProfile = configuration.getProfile().getRaProfile();
            // -- (1)revoke request (ask for issue)
            LOG.trace("TID={}, SN={} | revocation request (begin)", tid, sn);
            clientOperationService.revokeCertificate(
                    SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()),
                    certificate.getRaProfile().getSecuredUuid(),
                    certificate.getUuid().toString(),
                    dto);
            LOG.trace("TID={}, SN={} | revocation request (  end)", tid, sn);
        } catch (ConnectorException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "SN="+sn+" | cannot revoke certificate", e);
        } catch (AttributeException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "SN="+sn+" | cannot revoke certificate - wrong attributes", e);
        }
    }
}
