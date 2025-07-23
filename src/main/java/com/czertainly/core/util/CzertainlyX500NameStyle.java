package com.czertainly.core.util;

import com.czertainly.core.service.OidEntryService;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStrictStyle;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class CzertainlyX500NameStyle extends BCStrictStyle {

    // Kept here because of migration
    public static final CzertainlyX500NameStyle DEFAULT = new CzertainlyX500NameStyle(false, null);
    public static final CzertainlyX500NameStyle NORMALIZED = new CzertainlyX500NameStyle(true, null);
    private final boolean normalizedStyle;
    private final String delimiter;

    private final OidEntryService oidEntryService;

    public CzertainlyX500NameStyle(boolean normalizedStyle, OidEntryService oidEntryService) {
        this.oidEntryService = oidEntryService;
        this.normalizedStyle = normalizedStyle;
        this.delimiter = normalizedStyle ? "," : ", ";
    }

    @Override
    public String toString(X500Name x500Name) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean isFirstRdn = true;
        RDN[] rdNs = x500Name.getRDNs();
        if (this.normalizedStyle) {
            Arrays.sort(rdNs, Comparator.comparing((RDN obj) -> obj.getFirst().getType().getId()).thenComparing(obj -> obj.getFirst().getValue().toString()));
        } else {
            Collections.reverse(Arrays.asList(rdNs));
        }
        for (RDN rdn : rdNs) {
            if (isFirstRdn) {
                isFirstRdn = false;
            } else {
                stringBuilder.append(this.delimiter);
            }

            appendRDN(stringBuilder, rdn);
        }

        return stringBuilder.toString();
    }

    private String getRdnCode(AttributeTypeAndValue attributeTypeAndValue) {
        ASN1ObjectIdentifier type = attributeTypeAndValue.getType();
        if (this.normalizedStyle) return type.getId();
        try {
            return oidEntryService.getCode(type.getId());
        } catch (IllegalArgumentException e) {
            if (this.defaultSymbols.get(type) != null)
                return (String) this.defaultSymbols.get(type);
            else return type.getId();
        }
    }

    private void appendRDN(StringBuilder stringBuilder, RDN rdn) {
        if (rdn.isMultiValued()) {
            AttributeTypeAndValue[] attributeTypeAndValues = rdn.getTypesAndValues();
            boolean isFirst = true;

            for (int i = 0; i != attributeTypeAndValues.length; ++i) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    stringBuilder.append('+');
                }
                appendTypeAndValue(stringBuilder, attributeTypeAndValues[i]);
            }
        } else if (rdn.getFirst() != null) {
            appendTypeAndValue(stringBuilder, rdn.getFirst());
        }
    }

    private void appendTypeAndValue(StringBuilder stringBuilder, AttributeTypeAndValue attributeTypeAndValue) {
        stringBuilder.append(getRdnCode(attributeTypeAndValue));
        stringBuilder.append('=');
        stringBuilder.append(attributeTypeAndValue.getValue().toString());
    }

}
