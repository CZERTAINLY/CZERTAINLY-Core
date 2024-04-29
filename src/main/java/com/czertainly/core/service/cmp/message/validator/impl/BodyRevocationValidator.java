package com.czertainly.core.service.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.validator.BiValidator;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;

/**
 * Validate of revocation based messages, {@link RevReqContent}
 * and outgoing {@link RevRepContent}.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.9">Revocation request</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.10">Revocation response</a>
 */
public class BodyRevocationValidator extends BaseValidator implements BiValidator<Void,Void> {

    /**
     * <p>Validate of Revocation message (rr)</p>
     *
     * <pre>
     * 		RevReqContent ::= SEQUENCE OF RevDetails
     *
     * 		RevDetails ::= SEQUENCE {
     * 			certDetails         CertTemplate,
     * 			-- allows requester to specify as much as they can about
     * 			-- the cert. for which revocation is requested
     * 			-- (e.g., for cases in which serialNumber is not available)
     * 			crlEntryDetails     Extensions       OPTIONAL
     * 			-- requested crlEntryExtensions
     *                }
     * </pre>
     * @param request of message containing {@link RevReqContent}
     * @throws CmpProcessingException if validation will fail
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-F">Appendix F.  Compilable ASN.1 Definitions (rfc4210)</a>
     */
    @Override
    public Void validateIn(PKIMessage request, ConfigurationContext configuration) throws CmpProcessingException {
        assertEqualBodyType(PKIBody.TYPE_REVOCATION_REQ, request);
        RevReqContent content = (RevReqContent) request.getBody().getContent();
        RevDetails[] revDetails = content.toRevDetailsArray();
        checkOneElementInArray(revDetails, "RevDetails");
        CertTemplate certDetails = revDetails[0].getCertDetails();
        checkValueNotNull(certDetails, PKIFailureInfo.addInfoNotAvailable, "certDetails");
        checkValueNotNull(certDetails.getSerialNumber(), PKIFailureInfo.addInfoNotAvailable, "SerialNumber");
        checkValueNotNull(certDetails.getIssuer(), PKIFailureInfo.addInfoNotAvailable, "Issuer");
        Extensions crlEntryDetails = revDetails[0].getCrlEntryDetails();
        checkValueNotNull(crlEntryDetails, PKIFailureInfo.addInfoNotAvailable, "CrlEntryDetails");
        Extension reasonCodeExt = crlEntryDetails.getExtension(Extension.reasonCode);
        checkValueNotNull(reasonCodeExt, PKIFailureInfo.addInfoNotAvailable, "reasonCode");

        long reasonCode = ASN1Enumerated.getInstance(reasonCodeExt.getParsedValue())
                .getValue().longValue();
        if (reasonCode < 0 || reasonCode > 10) {
            throw new CmpProcessingException(PKIFailureInfo.badDataFormat, "reasonCode is out of range <0,10>");
        }

        return null;// validation is ok
    }

    /**
     * <p>Validation of Revocation (response) message (rp)</p>
     * <pre>
     * 		RevRepContent ::= SEQUENCE {
     * 			status       SEQUENCE SIZE (1..MAX) OF PKIStatusInfo,
     * 			-- in same order as was sent in RevReqContent
     * 			revCerts [0] SEQUENCE SIZE (1..MAX) OF CertId
     * 			                                 OPTIONAL,
     * 			-- IDs for which revocation was requested
     * 			-- (same order as status)
     * 			crls     [1] SEQUENCE SIZE (1..MAX) OF CertificateList
     * 			                                 OPTIONAL
     * 			-- the resulting CRLs (there may be more than one)
     *        }
     * </pre>
     *
     * @param response of message containing {@link RevRepContent}
     * @throws CmpProcessingException if validation will fail
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-F">Appendix F.  Compilable ASN.1 Definitions (rfc4210)</a>
     */
    @Override
    public Void validateOut(PKIMessage response, ConfigurationContext configuration) throws CmpProcessingException {
        assertEqualBodyType(PKIBody.TYPE_REVOCATION_REP, response);
        RevRepContent content = (RevRepContent) response.getBody().getContent();
        PKIStatusInfo[] statuses = content.getStatus();
        checkOneElementInArray(statuses, "status");

        PKIStatusInfo statusInfo = statuses[0];
        if (statusInfo.getStatus().intValue() == PKIStatus.GRANTED) {
            validatePositiveStatus(statusInfo);
        } else {
            validateNegativeStatus(statusInfo);
        }

        return null;// validation is ok
    }
}
// TODO [tocecz], dle roman.cinkais - ale protocol je pak neuplny
// 3g99, 9.5	CMPv2 Profiling
// 3gpp, 10.3.1.1	General Requirements
//      This CMPv2 profile shall only include certificate request and key update functions.
//      Revocation processing and PKCS#10 requests shall not be part of this CMPv2 profile.
