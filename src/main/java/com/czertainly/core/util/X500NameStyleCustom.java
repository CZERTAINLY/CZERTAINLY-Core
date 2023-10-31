package com.czertainly.core.util;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameStyle;
import org.bouncycastle.asn1.x500.style.BCStrictStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

public class X500NameStyleCustom extends BCStrictStyle {

    public static final X500NameStyle INSTANCE = new X500NameStyleCustom();

    public X500NameStyleCustom() {
    }

    @Override
    public String toString(X500Name x500Name) {
        StringBuffer stringBuffer = new StringBuffer();
        boolean isFirstRdn = true;
        RDN[] rdNs = x500Name.getRDNs();
        for (RDN rdn : rdNs) {
            if (isFirstRdn) {
                isFirstRdn = false;
            } else {
                stringBuffer.append(", ");
            }

            IETFUtils.appendRDN(stringBuffer, rdn, this.defaultSymbols);
        }

        return stringBuffer.toString();
    }

}
