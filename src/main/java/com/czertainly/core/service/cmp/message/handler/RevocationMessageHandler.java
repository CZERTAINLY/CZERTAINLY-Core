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

    private ClientOperationService clientOperationService;
    private RegexpURLValidator regexpURLValidator;

    @Autowired
    public void setClientOperationService(ClientOperationService clientOperationService) { this.clientOperationService = clientOperationService; }

    private CertificateService certificateService;
    @Autowired
    public void setCertificateService(CertificateService certificateService) { this.certificateService = certificateService; }

    private CertificateRepository certificateRepository;
    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) { this.certificateRepository = certificateRepository; }


    @Override
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpBaseException {
        if(PKIBody.TYPE_REVOCATION_REQ!=request.getBody().getType()) {
            throw new CmpProcessingException(
                    PKIFailureInfo.systemFailure,
                    "revocation (rr) message cannot be handled - unsupported body rawType="+request.getBody().getType()+", type="+ PkiMessageDumper.msgTypeAsString(request.getBody().getType()) +"; only type=cerfConf is supported");
        }
        ASN1OctetString tid = request.getHeader().getTransactionID();
        RevReqContent revBody = (RevReqContent) request.getBody().getContent();

        RevRepContentBuilder rrcb = new RevRepContentBuilder();
        //rrcb.add(new PKIStatusInfo(PKIStatus.revocationNotification));
        for (var revocation : revBody.toRevDetailsArray()) {
            CertTemplate certDetails = revocation.getCertDetails();
            CertId certId = new CertId(new GeneralName(certDetails.getIssuer()),
                    certDetails.getSerialNumber());
            try {
                revokeCertificate(tid, revocation, configuration);
                // mam pollovat, za kazdy certifikat, jak dopadla revokace?
                rrcb.add(new PKIStatusInfo(PKIStatus.revocationNotification), certId);
            } catch (Exception e) {
                rrcb.add(new PKIStatusInfo(PKIStatus.revocationWarning/*rejection??*/), certId);
            }
        }

        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(new PKIBody(PKIBody.TYPE_REVOCATION_REP, rrcb.build()))
                    .addExtraCerts(null)
                    .build();
        } catch (Exception e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    " problem build revocation response message", e);
        }
    }

    private CertificateRevocationReason getReason(RevDetails revocation, ASN1OctetString tid)
            throws CmpProcessingException {
        Extensions crlEntryDetails = revocation.getCrlEntryDetails();
        Extension reasonCodeExt = crlEntryDetails.getExtension(Extension.reasonCode);
        Long reasonCode = ASN1Enumerated.getInstance(reasonCodeExt.getParsedValue())
                .getValue().longValue();
        CertificateRevocationReason reason = reasonCodeExt == null ? CertificateRevocationReason.UNSPECIFIED : CertificateRevocationReason.fromReasonCode(reasonCode.intValue());
        if (reason == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    "Revocation reason could not be found");
        }
        return reason;
    }

    private String getSerialNumber(RevDetails revocation, ASN1OctetString tid)
            throws CmpProcessingException {
        ASN1Integer serialNumber = revocation.getCertDetails().getSerialNumber();
        if (serialNumber == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    "certificate's serialNumber could not be found");
        }
        return serialNumber.getValue().toString(16);
    }

    private void revokeCertificate(ASN1OctetString tid, RevDetails revocation, ConfigurationContext configuration)
            throws CmpProcessingException {
        CertificateRevocationReason reason = getReason(revocation, tid);
        String serialNumber = getSerialNumber(revocation, tid);
        Optional<Certificate> certificate = certificateRepository.findBySerialNumberIgnoreCase(serialNumber);
        if (certificate.isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    "certificate for revocation could not be found, serialNumber="+serialNumber);
        }
        if (CertificateState.REVOKED.equals(certificate.get().getState())) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badCertTemplate,
                    "Certificate is already revoked");
        }

        try {
            ClientCertificateRevocationDto dto = new ClientCertificateRevocationDto();
            dto.setReason(reason);
            dto.setAttributes(configuration.getClientOperationAttributes(true));
            RaProfile raProfile = configuration.getProfile().getRaProfile();
            // -- (1)revoke request (ask for issue)
            clientOperationService.revokeCertificate(
                    SecuredParentUUID.fromUUID(raProfile.getAuthorityInstanceReferenceUuid()),
                    certificate.get().getRaProfile().getSecuredUuid(),
                    certificate.get().getUuid().toString(),
                    dto);
        } catch (ConnectorException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "cannot revoke certificate", e);
        } catch (AttributeException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "cannot revoke certificate - wrong attributes", e);
        }
    }
}
