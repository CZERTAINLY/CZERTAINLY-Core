package com.czertainly.core.api.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import com.czertainly.core.api.cmp.message.validator.Validator;
import org.bouncycastle.asn1.cmp.*;

/**
 * {@link PKIMessage} validator for type Certificate Confirmation
 * Content (certConf, {@link PKIBody#TYPE_CERT_CONFIRM}).
 */
public class BodyCertConfValidator extends BaseValidator implements Validator<PKIMessage, Void> {

    public BodyCertConfValidator(ConfigurationContext configuration) { super(configuration); }

    /**
     * <p>Certificate Confirmation Content</p>
     * <pre>
     *          CertConfirmContent ::= SEQUENCE OF CertStatus
     *
     *          CertStatus ::= SEQUENCE {
     *             certHash    OCTET STRING,
     *             certReqId   INTEGER,
     *             statusInfo  PKIStatusInfo OPTIONAL
     *          }
     * </pre>
     * @param request of certConf message, {@link CertConfirmContent}
     * @throws CmpProcessingException if validation has failed
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.3.18">Certificate Confirmation Content</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-F">Appendix F.  Compilable ASN.1 Definitions (rfc4210)</a>
     */
    @Override
    public Void validate(PKIMessage request) throws CmpException {
        assertEqualBodyType(PKIBody.TYPE_CERT_CONFIRM, request);
        CertConfirmContent content = (CertConfirmContent) request.getBody().getContent();
        CertStatus[] certStatuses = content.toCertStatusArray();
        checkOneElementInArray(certStatuses, "certStatus");
        CertStatus certStatus = certStatuses[0];
        checkValueNotNull(certStatus.getCertHash(), PKIFailureInfo.badDataFormat, "CertHash");
        return null;
    }

}
