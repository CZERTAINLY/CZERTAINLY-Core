package com.czertainly.core.api.cmp.message.util;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.cmp.CMPCertificate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CertificationUtil {
    private static CertificateFactory certificateFactory;
    private static Provider BOUNCY_CASTLE_PROVIDER;

    /**
     * function converts CMPCertificates to X509 certificates
     *
     * @param certs list of certificates to convert
     * @return list of converted certificates
     * @throws CertificateException if certificate failed
     */
    public static List<X509Certificate> toX509Certificates(final CMPCertificate[] certs)
            throws CertificateException {
        final ArrayList<X509Certificate> listOfConvertedCerts = new ArrayList<>(certs.length);
        for (final CMPCertificate aktCert : certs) {
            listOfConvertedCerts.add(toX509Certificate(aktCert));
        }
        return listOfConvertedCerts;
    }

    /**
     * function converts CMPCertificate to X509 certificate
     *
     * @param cert certificate to convert
     * @return converted certificate
     * @throws CertificateException if certificate failed
     */
    public static X509Certificate toX509Certificate(final CMPCertificate cert) throws CertificateException {
        try {
            return toX509Certificate(cert.getEncoded(ASN1Encoding.DER));
        } catch (final IOException ex) {
            throw new CertificateException(ex);
        }
    }

    /**
     * function convert bytes to X509 certificate
     *
     * @param encoded byte string to encode
     * @return converted certificate
     * @throws CertificateException if certificate failed from encoded data
     */
    public static X509Certificate toX509Certificate(final byte[] encoded) throws CertificateException {
        return (X509Certificate) getCertificateFactory().generateCertificate(new ByteArrayInputStream(encoded));
    }

    /**
     * Function to retrieve the static certificate factory object
     *
     * @return static certificate factory object
     * @throws CertificateException thrown if the certificate factory could not be
     *                              instantiated
     * @throws CertificateException            in case of an error
     */
    public static synchronized CertificateFactory getCertificateFactory() throws CertificateException {
        if (certificateFactory == null) {
            certificateFactory = CertificateFactory.getInstance("X.509", BouncyCastleUtil.getBouncyCastleProvider());
        }
        return certificateFactory;
    }

}
