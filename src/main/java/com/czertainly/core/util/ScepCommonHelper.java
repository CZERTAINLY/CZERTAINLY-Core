package com.czertainly.core.util;

import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;

public class ScepCommonHelper {
    public static final String id_Verisign = "2.16.840.1.113733";

    public static final String id_pki = id_Verisign + ".1";

    public static final String id_attributes = id_pki + ".9";

    public static final String id_scep = id_pki + ".24";

    public static final String id_messageType = id_attributes + ".2";
    public static final String id_pkiStatus = id_attributes + ".3";
    public static final String id_failInfo = id_attributes + ".4";
    public static final String id_senderNonce = id_attributes + ".5";
    public static final String id_recipientNonce = id_attributes + ".6";
    public static final String id_transId = id_attributes + ".7";
    public static final String id_scep_failInfoText = id_scep + ".1";
    public static final int SCEP_TYPE_PKCSREQ = 19;
    public static final int SCEP_TYPE_POLL_CERT = 20;
    public static final int SCEP_TYPE_RENEWAL = 17;

    public static final List<JcaX509CertificateHolder> convertToX509CertificateHolder(List<X509Certificate> certificateChain) throws CertificateEncodingException {
        List<JcaX509CertificateHolder> certificateHolderChain = new ArrayList();
        Iterator iterator = certificateChain.iterator();

        while (iterator.hasNext()) {
            X509Certificate certificate = (X509Certificate) iterator.next();
            certificateHolderChain.add(new JcaX509CertificateHolder(certificate));
        }

        return certificateHolderChain;
    }

    public static String getRandomNonce() {
        byte[] senderNonce = new byte[16];
        Random randomSource = new Random();
        randomSource.nextBytes(senderNonce);
        return new String(Base64.getEncoder().encode(senderNonce));
    }
}
