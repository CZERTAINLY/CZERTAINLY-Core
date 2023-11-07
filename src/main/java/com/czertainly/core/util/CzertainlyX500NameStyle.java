package com.czertainly.core.util;

import com.czertainly.api.model.core.certificate.RdnType;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStrictStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

import java.util.Arrays;
import java.util.Collections;

public class CzertainlyX500NameStyle extends BCStrictStyle {

    public static final CzertainlyX500NameStyle DEFAULT_INSTANCE = new CzertainlyX500NameStyle(false);
    public static final CzertainlyX500NameStyle NORMALIZED_INSTANCE = new CzertainlyX500NameStyle(true);
    private final boolean normalized;


    public CzertainlyX500NameStyle(boolean normalized) {
        this.normalized = normalized;
    }

    @Override
    public String toString(X500Name x500Name) {
        StringBuffer stringBuffer = new StringBuffer();
        boolean isFirstRdn = true;

        RDN[] rdNs = x500Name.getRDNs();
        // Make the ordering last-to-first
        Collections.reverse(Arrays.asList(rdNs));
        if (this.normalized) Arrays.sort(rdNs, (rdn1, rdn2) -> getRdnCode(rdn1.getFirst())
                .compareTo(getRdnCode(rdn2.getFirst())));
        for (RDN rdn : rdNs) {
            if (isFirstRdn) {
                isFirstRdn = false;
            } else {
                stringBuffer.append(", ");
            }

            appendRDN(stringBuffer, rdn);
        }

        return stringBuffer.toString();
    }

    private String getRdnCode(AttributeTypeAndValue attributeTypeAndValue) {
        ASN1ObjectIdentifier type = attributeTypeAndValue.getType();
        try {
            return RdnType.fromOID(type.toString()).getCode();
        } catch (IllegalArgumentException e) {
            if (this.defaultSymbols.get(type) != null)
                return (String) this.defaultSymbols.get(type);
            else return type.getId();
        }
    }

    private void appendRDN(StringBuffer stringBuffer, RDN rdn) {
        if (rdn.isMultiValued()) {
            AttributeTypeAndValue[] attributeTypeAndValues = rdn.getTypesAndValues();
            boolean isFirst = true;

            for (int i = 0; i != attributeTypeAndValues.length; ++i) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    stringBuffer.append(", ");
                }

                appendTypeAndValue(stringBuffer, attributeTypeAndValues[i]);
            }
        } else if (rdn.getFirst() != null) {
            appendTypeAndValue(stringBuffer, rdn.getFirst());
        }
    }

    private void appendTypeAndValue(StringBuffer stringBuffer, AttributeTypeAndValue attributeTypeAndValue) {
        stringBuffer.append(getRdnCode(attributeTypeAndValue));
        stringBuffer.append('=');
        stringBuffer.append(IETFUtils.valueToString(attributeTypeAndValue.getValue()));
    }

}
