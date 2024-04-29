package com.czertainly.core.service.cmp.util;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DEROctetString;
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

import static com.czertainly.core.service.cmp.message.PkiMessageDumper.ifNotNull;

public class CertUtil {
    private static CertificateFactory certificateFactory;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     * @return array of converted certificates
     * @throws CertificateException if certificate could not be converted
     */
    public static CMPCertificate[] toCmpCertificates(List<X509Certificate> certs) throws CertificateException {
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
    public static boolean isIntermediateCertificate(X509Certificate cert) {
        try {
            cert.verify(cert.getPublicKey());// true=self-signed (certificate signature with its own public key)
            return false;
        } catch (final SignatureException | InvalidKeyException keyEx) {
            return true;// invalid key == it is not self-signed
        } catch (CertificateException | NoSuchAlgorithmException | NoSuchProviderException e) {
            return false;// could be self-signed
        }
    }

    /**
     * Extract SubjectKeyIdentifier from a given <code>certificate</code>
     *
     * @param certificate to fetch the SubjectKeyIdentifier from
     * @return SubjectKeyIdentifier encoded as DEROctetString
     */
    public static DEROctetString extractSubjectKeyIdentifierFromCert(X509Certificate certificate) {
        return ifNotNull(
                certificate.getExtensionValue(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier.getId()),
                x -> new DEROctetString(
                        ASN1OctetString.getInstance(ASN1OctetString.getInstance(x).getOctets())
                        .getOctets()));
    }

    public static byte[] generateRandomBytes(int length) {
        final byte[] generated = new byte[length];
        SECURE_RANDOM.nextBytes(generated);
        return generated;
    }
}
