package com.czertainly.core.util;

import com.czertainly.api.model.core.certificate.X500RdnType;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStrictStyle;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class CzertainlyX500NameStyle extends BCStrictStyle {

    public static final CzertainlyX500NameStyle DEFAULT = new CzertainlyX500NameStyle(false);
    public static final CzertainlyX500NameStyle NORMALIZED = new CzertainlyX500NameStyle(true);
    private final boolean normalized;
    private final String delimiter;


    public CzertainlyX500NameStyle(boolean normalized) {
        this.normalized = normalized;
        if (normalized) this.delimiter = ","; else this.delimiter = ", ";
    }

    @Override
    public String toString(X500Name x500Name) {
        if (this.normalized) return toStringNormalized(x500Name);
        else  {
            return toStringDefault(x500Name);
        }
    }

    private String toStringNormalized(X500Name x500Name) {
        StringBuffer stringBuffer = new StringBuffer();
        boolean isFirstRdn = true;
        RDN[] rdNs = x500Name.getRDNs();
        Arrays.sort(rdNs, Comparator.comparing((RDN obj) -> obj.getFirst().getType().getId()).thenComparing(obj -> obj.getFirst().getValue().toString()));
        for (RDN rdn : rdNs) {
            if (isFirstRdn) {
                isFirstRdn = false;
            } else {
                stringBuffer.append(this.delimiter);
            }

            appendRDN(stringBuffer, rdn);
        }

        return stringBuffer.toString();
    }

    private String toStringDefault(X500Name x500Name) {
        StringBuffer stringBuffer = new StringBuffer();
        boolean isFirstRdn = true;

        RDN[] rdNs = x500Name.getRDNs();
        // Make the ordering last-to-first
        Collections.reverse(Arrays.asList(rdNs));
        for (RDN rdn : rdNs) {
            if (isFirstRdn) {
                isFirstRdn = false;
            } else {
                stringBuffer.append(this.delimiter);
            }

            appendRDN(stringBuffer, rdn);
        }

        return stringBuffer.toString();
    }

    private String getRdnCode(AttributeTypeAndValue attributeTypeAndValue) {
        ASN1ObjectIdentifier type = attributeTypeAndValue.getType();
        if (this.normalized) return type.getId();
        try {
            return X500RdnType.fromOID(type.toString()).getCode();
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
                    stringBuffer.append(this.delimiter);
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
        stringBuffer.append(attributeTypeAndValue.getValue().toString());
    }

}
