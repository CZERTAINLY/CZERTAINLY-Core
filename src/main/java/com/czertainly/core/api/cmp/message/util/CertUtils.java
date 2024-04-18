package com.czertainly.core.api.cmp.message.util;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CertUtils {
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
     * Function to convert: from list of X509 certificates to list of CMPCertificates
     *
     * @param certs certificates to convert
     * @return list of converted certificates
     * @throws CertificateException if certificate could not be converted
     */
    public static CMPCertificate[] toCmpCertificates(final List<X509Certificate> certs) throws CertificateException {
        final CMPCertificate[] ret = new CMPCertificate[certs.size()];
        int index = 0;
        for (final X509Certificate aktCert : certs) {
            ret[index++] = toCmpCertificate(aktCert);
        }
        return ret;
    }

    /**
     * Function to convert: from single X509 certificate to CMPCertificate
     *
     * @param cert certificate to convert
     * @return converted certificate
     * @throws CertificateException if certificate could not be converted
     */
    public static CMPCertificate toCmpCertificate(final Certificate cert) throws CertificateException {
        return CMPCertificate.getInstance(cert.getEncoded());
    }

    /**
     * Function to create the static certificate factory object
     *
     * @return static certificate factory object
     * @throws CertificateException thrown if the factory could not be created
     * @throws CertificateException in case of an error
     */
    public static synchronized CertificateFactory getCertificateFactory() throws CertificateException {
        if (certificateFactory == null) {
            try {
                certificateFactory = CertificateFactory.getInstance("X.509",
                        BouncyCastleProvider.PROVIDER_NAME);// BouncyCastleUtil.getBouncyCastleProvider()
            } catch (NoSuchProviderException e) {
                throw new CertificateException(e);
            }
        }
        return certificateFactory;
    }

    /**
     * Checks whether given X.509 certificate is intermediate certificate and not
     * self-signed.
     *
     * @param cert certificate to be checked
     * @return <code>true</code> if the certificate is intermediate and not
     *         self-signed
     */
    public static boolean isIntermediateCertificate(final X509Certificate cert) {
        try {
            // Try to verify certificate signature with its own public key
            final PublicKey key = cert.getPublicKey();
            cert.verify(key);
            // self-signed
            return false;
        } catch (final SignatureException | InvalidKeyException keyEx) {
            // Invalid key --> definitely not self-signed
            return true;
        } catch (CertificateException | NoSuchAlgorithmException | NoSuchProviderException e) {
            // processing error, could be self-signed
            return false;
        }
    }
}
