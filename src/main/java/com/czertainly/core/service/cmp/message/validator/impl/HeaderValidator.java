package com.czertainly.core.service.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpBaseException;
import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.service.cmp.message.ConfigurationContext;
import com.czertainly.core.service.cmp.message.validator.Validator;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.cmp.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * <pre>
 *      PKIHeader ::= SEQUENCE {
 *          pvno                INTEGER     { cmp1999(1), cmp2000(2) },
 *          sender              GeneralName,
 *          recipient           GeneralName,
 *          messageTime     [0] GeneralizedTime         OPTIONAL,
 *          protectionAlg   [1] AlgorithmIdentifier     OPTIONAL,
 *          senderKID       [2] KeyIdentifier           OPTIONAL,
 *          recipKID        [3] KeyIdentifier           OPTIONAL,
 *          transactionID   [4] OCTET STRING            OPTIONAL,
 *          senderNonce     [5] OCTET STRING            OPTIONAL,
 *          recipNonce      [6] OCTET STRING            OPTIONAL,
 *          freeText        [7] PKIFreeText             OPTIONAL,
 *          generalInfo     [8] SEQUENCE SIZE (1..MAX) OF
 *                              InfoTypeAndValue     OPTIONAL
 *      }
 *      PKIFreeText ::= SEQUENCE SIZE (1..MAX) OF UTF8String
 * </pre>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#section-5.1.1">PKI Message header</a>
 */
@Component
@Transactional
public class HeaderValidator extends BaseValidator implements Validator<PKIMessage, Void> {

    @Override
    public Void validate(PKIMessage message, ConfigurationContext configuration) throws CmpBaseException {
        if (Objects.isNull(message)) {
            throw new CmpProcessingException(PKIFailureInfo.badDataFormat,
                    "message cannot be null");
        }

        PKIHeader header = message.getHeader();
        checkValueNotNull(header, PKIFailureInfo.badDataFormat, "header");
        ASN1Integer pvno = header.getPvno();
        checkValueNotNull(pvno, PKIFailureInfo.unsupportedVersion, "pvno");
        long versionNumber = pvno.longValueExact();
        if (versionNumber != PKIHeader.CMP_2000) {
            throw new CmpProcessingException(PKIFailureInfo.unsupportedVersion,
                    "version " + versionNumber + " not supported");
        }
        checkValueNotNull(header.getSender(), PKIFailureInfo.badDataFormat, "sender");
        checkValueNotNull(header.getRecipient(), PKIFailureInfo.badDataFormat, "recipient");
        checkMinimalLength(header.getTransactionID(), 16, "transactionID");
        checkMinimalLength(header.getSenderNonce(), 16, "senderNonce");

        /*
         * The protectionAlg field specifies the algorithm used to protect the
         * message.  If no protection bits are supplied (note that PKIProtection
         * is OPTIONAL) then this field MUST be omitted; if protection bits are
         * supplied, then this field MUST be supplied.
         *
         * @see https://www.rfc-editor.org/rfc/rfc4210#section-5.1.1
         *
         * if not null, it will in form defined in:
         * @see https://www.rfc-editor.org/rfc/rfc4210#appendix-D.2
         */
        if(message.getProtection() != null) {
            checkValueNotNull(header.getProtectionAlg(),
                    PKIFailureInfo.badDataFormat, "protectionAlg");
        }

        return null;//header is ok
    }
}
