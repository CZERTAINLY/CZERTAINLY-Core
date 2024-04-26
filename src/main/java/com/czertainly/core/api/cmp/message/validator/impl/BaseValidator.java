package com.czertainly.core.api.cmp.message.validator.impl;

import com.czertainly.core.api.cmp.error.CmpProcessingException;
import com.czertainly.core.api.cmp.message.ConfigurationContext;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.cmp.PKIStatusInfo;

import java.util.Objects;

public abstract class BaseValidator {

    /**
     * ir,ip - fixed value of zero
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.4">Initial Registration/Certification (Basic Authenticated Scheme)</a>
     */
    public static final ASN1Integer ZERO = new ASN1Integer(0);

    protected final ConfigurationContext configuration;

    public BaseValidator(ConfigurationContext configuration) {this.configuration = configuration;}

    /**
     * Check if given message <code>checkedMessage</code> is same type as <code>expectedBodyType</code>.
     *
     * @param expectedBodyType type which is checked
     * @param checkedMessage message which must be same as <code>expectedBodyType</code>
     * @throws CmpProcessingException if bodyTypes (expected,checked) are not equals
     */
    protected void assertEqualBodyType(int expectedBodyType, PKIMessage checkedMessage) throws CmpProcessingException {
        if(expectedBodyType!=checkedMessage.getBody().getType()){
            ASN1OctetString tid = checkedMessage.getHeader().getTransactionID();
            int incomingType = checkedMessage.getBody().getType();
            throw new CmpProcessingException(PKIFailureInfo.systemFailure,
                    "TID="+tid+" | mismatch using validator (type is "+incomingType+", but must be="+expectedBodyType+")");
        }
    }

    /**
     * Check if given values (<code>value1</code>, <code>value2</>) are the same
     * @param value1 first value for
     * @param value2 first value for
     * @throws CmpProcessingException if values are different
     */
    protected void assertEqual(Object value1, Object value2, String errorMsg)
            throws CmpProcessingException {
        if (!Objects.equals(value1, value2)) {
            throw new CmpProcessingException(PKIFailureInfo.badDataFormat, errorMsg);
        }
    }

    protected void checkOneElementInArray(Object[] array, String fieldName)
            throws CmpProcessingException {
        if (array == null) {
            throw new CmpProcessingException(
                    PKIFailureInfo.addInfoNotAvailable, "missing '" + fieldName + "'");
        }
        if (array.length != 1) {
            throw new CmpProcessingException(
                    PKIFailureInfo.badDataFormat, "'" + fieldName + "' must have one element");
        }
        if (array[0] == null) {
            throw new CmpProcessingException(
                    PKIFailureInfo.addInfoNotAvailable, "missing '" + fieldName + "'");
        }
    }

    /**
     * Check if <code>value</code> is NULL otherwise (value is NOT NULL) throws {@link CmpProcessingException}
     *
     * @param value which must be NULL
     * @param failInfo type of purpose (why value is NOT NULL)
     * @param errorMsg error description (why value is NOT NULL)
     * @throws CmpProcessingException if value is NOT NULL
     */
    protected void checkValueIsNull(Object value, int failInfo, String errorMsg)
            throws CmpProcessingException {
        if (!Objects.isNull(value)) {
            throw new CmpProcessingException(failInfo, errorMsg);
        }
    }

    /**
     * Check if <code>value</code> is NOT NULL otherwise (value is NULL) throws {@link CmpProcessingException}
     *
     * @param value which must be NOT NULL (not be empty)
     * @param failInfo type of purpose (why value is NULL)
     * @param fieldName name of field (whose value is NULL)
     * @throws CmpProcessingException if value is NULL
     */
    protected void checkValueNotNull(Object value, int failInfo, String fieldName)
            throws CmpProcessingException {
        if (Objects.isNull(value)) {
            throw new CmpProcessingException(failInfo, "'" + fieldName + "' has null value");
        }
    }
    /**
     * <pre>
     *     crc[0].status.       present, positive values allowed:
     *       status               "accepted", "grantedWithMods"
     *                         negative values allowed:
     *                            "rejection"
     *    crc[0].status.       present if and only if
     *       failInfo          crc[0].status.status is "rejection"
     * </pre>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.4">Initial Registration/Certification (Basic Authenticated Scheme)</a>
     */
    protected void validatePositiveStatus(final PKIStatusInfo pkiStatusInfo) throws CmpProcessingException {
        switch (pkiStatusInfo.getStatus().intValue()) {
            case PKIStatus.GRANTED:
            case PKIStatus.GRANTED_WITH_MODS:
                if(!Objects.isNull(pkiStatusInfo.getFailInfo())) {
                    throw new CmpProcessingException(PKIFailureInfo.badDataFormat,
                            "failInfo cannot be present in positive status");
                }
                return;
            default:
                assertEqual(
                        pkiStatusInfo.getStatus().intValue(),
                        PKIStatus.REJECTION,
                        "status must have have the value \"accepted\" or \"grantedWithMods\"");
        }
    }

    /**
     * <pre>
     *     crc[0].status.       present, positive values allowed:
     *       status               "accepted", "grantedWithMods"
     *                         negative values allowed:
     *                            "rejection"
     *    crc[0].status.       present if and only if
     *       failInfo          crc[0].status.status is "rejection"
     * </pre>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc4210#appendix-D.4">Initial Registration/Certification (Basic Authenticated Scheme)</a>
     */
    protected void validateNegativeStatus(PKIStatusInfo pkiStatusInfo) throws CmpProcessingException {
        if (pkiStatusInfo.getStatus().intValue() == PKIStatus.REJECTION) {
            if (Objects.isNull(pkiStatusInfo.getFailInfo())) {
                throw new CmpProcessingException(PKIFailureInfo.badMessageCheck,
                        "failInfo cannot be null if status is 'rejection'");
            }
        } else {
            if (!Objects.isNull(pkiStatusInfo.getFailInfo())) {
                throw new CmpProcessingException(PKIFailureInfo.badMessageCheck,
                        "failInfo must present only if the status is 'rejection'");
            }
        }
    }

    /**
     * Check if given <code>value</code> is greater than <code>minimalLength</code>
     * otherwise exception is raised (reason={@link PKIFailureInfo#badRequest}). If value is null,
     * exception is raised also (with different {@link PKIFailureInfo#addInfoNotAvailable} reason).
     *
     * @param value value which is checked (for given <code>minimalLength</code>)
     * @param minimalLength <code>value</code>'s length must be greater
     * @param fieldName where length is checked
     * @throws CmpProcessingException if validation failed
     */
    protected void checkMinimalLength(
            final ASN1OctetString value, final int minimalLength, final String fieldName)
            throws CmpProcessingException {
        if (value == null) {
            throw new CmpProcessingException(PKIFailureInfo.addInfoNotAvailable,
                    "mandatory field '" + fieldName + "' is missing");
        }
        if (value.getOctets().length < minimalLength) {
            throw new CmpProcessingException(PKIFailureInfo.badRequest,
                    fieldName + "'s value is too short");
        }
    }
}
