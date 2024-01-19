package com.czertainly.core.util;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.ArrayList;
import java.util.List;

public class CrlUtil {
    private static final Logger logger = LoggerFactory.getLogger(CrlUtil.class);
    //CRL Timeout setting when initiating URL Connection. If the connection takes more than 30 seconds, it is determined as not reachable
    private static final Integer CRL_CONNECTION_TIMEOUT = 1000; //milliseconds

    private CrlUtil() {
    }

    public static List<String> getCDPFromCertificate(byte[] extensionToDownloadFrom) throws IOException {

        if (extensionToDownloadFrom == null) {
            return new ArrayList<>();
        }
        ASN1InputStream oAsnInStream = new ASN1InputStream(
                new ByteArrayInputStream(extensionToDownloadFrom));
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
        return crlUrls;
    }

    public static X509CRL getX509Crl(String crlUrl) throws IOException, CertificateException {
        X509CRL X509Crl;
        URL url = new URL(crlUrl);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(CRL_CONNECTION_TIMEOUT);
        CertificateFactory cf = CertificateFactory.getInstance("X509");

        try (DataInputStream inStream = new DataInputStream(connection.getInputStream())) {
            X509Crl = (X509CRL) cf.generateCRL(inStream);
        } catch (CRLException e) {
            throw new CertificateException("File " + e.getMessage() + " not found");
        }
        return X509Crl;
    }



}
