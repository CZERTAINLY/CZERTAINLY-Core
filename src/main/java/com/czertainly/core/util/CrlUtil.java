package com.czertainly.core.util;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class CrlUtil {
    private static final Logger logger = LoggerFactory.getLogger(CrlUtil.class);
    //CRL Timeout setting when initiating URL Connection. If the connection takes more than 30 seconds, it is determined as not reachable
    private static final Integer CRL_CONNECTION_TIMEOUT = 30; //seconds

    private CrlUtil() {
    }

    public static List<String> getCDPFromCertificate(X509Certificate certificate) throws IOException {
        logger.debug("Obtaining the CRL Urls from the certificate");

        byte[] crlDistributionPointDerEncodedArray = certificate
                .getExtensionValue(Extension.cRLDistributionPoints.getId());
        if (crlDistributionPointDerEncodedArray == null) {
            return new ArrayList<>();
        }
        ASN1InputStream oAsnInStream = new ASN1InputStream(
                new ByteArrayInputStream(crlDistributionPointDerEncodedArray));
        ASN1Primitive derObjCrlDP = oAsnInStream.readObject();
        DEROctetString dosCrlDP = (DEROctetString) derObjCrlDP;

        oAsnInStream.close();

        byte[] crldpExtOctets = dosCrlDP.getOctets();
        ASN1InputStream oAsnInStream2 = new ASN1InputStream(new ByteArrayInputStream(crldpExtOctets));
        ASN1Primitive derObj2 = oAsnInStream2.readObject();
        CRLDistPoint distPoint = CRLDistPoint.getInstance(derObj2);

        oAsnInStream2.close();

        List<String> crlUrls = new ArrayList<>();
        for (DistributionPoint dp : distPoint.getDistributionPoints()) {
            DistributionPointName dpn = dp.getDistributionPoint();
            // Look for URIs in fullName
            if (dpn != null && dpn.getType() == DistributionPointName.FULL_NAME) {
                GeneralName[] genNames = GeneralNames.getInstance(dpn.getName()).getNames();
                // Look for an URI
                for (int j = 0; j < genNames.length; j++) {
                    if (genNames[j].getTagNo() == GeneralName.uniformResourceIdentifier) {
                        String url = DERIA5String.getInstance(genNames[j].getName()).getString();
                        crlUrls.add(url);
                    }
                }
            }
        }
        logger.debug("Obtained CRL Urls for the certificate");
        return crlUrls;
    }

    public static String checkCertificateRevocationList(X509Certificate certificate, String crlUrl) throws IOException, CertificateException, CRLException {
        logger.debug("Checking CRL URL {}", crlUrl);
        X509CRL crl;
        URL url = new URL(crlUrl);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(CRL_CONNECTION_TIMEOUT);
        CertificateFactory cf = CertificateFactory.getInstance("X509");

        try (DataInputStream inStream = new DataInputStream(connection.getInputStream())) {
            crl = (X509CRL) cf.generateCRL(inStream);
        } catch (FileNotFoundException e) {
            throw new CertificateException("File " + e.getMessage() + " not found");
        }
        logger.debug("Completed CRL check for {}", crlUrl);
        X509CRLEntry crlCertificate = crl.getRevokedCertificate(certificate.getSerialNumber());
        if (crlCertificate == null) {
            return null;
        } else {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            String strDate = dateFormat.format(crlCertificate.getRevocationDate());
            String reason = crlCertificate.getRevocationReason() != null ? crlCertificate.getRevocationReason().toString() : "Unspecified";

            return String.format("Reason: %s. Date: %s", reason, strDate);
        }
    }
}
