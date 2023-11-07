package com.czertainly.core.util;

import com.czertainly.core.service.CertificateService;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateException;

public class CustomNameStyleTest {
     private static final Logger logger = LoggerFactory.getLogger(CustomNameStyleTest.class);

    @Test
    public void testCustomNameStyle() throws CertificateException {

        X500Principal x500Principal = new X500Principal("CN=Demo Client Sub CA,O=3Key Company s.r.o.");
        logger.info(X500Name.getInstance(CzertainlyX500NameStyle.INSTANCE, x500Principal.getEncoded()).toString());
        x500Principal = new X500Principal("CN=Demo Client Sub CA,EMAIL=3key@company.com");
        logger.info(X500Name.getInstance(CzertainlyX500NameStyle.INSTANCE, x500Principal.getEncoded()).toString());
        logger.info(X500Name.getInstance(CzertainlyX500NameStyle.INSTANCE, new X500Principal("CN=Certificate Authority; O=Organization;L=Location, ST=State, C=US").getEncoded()).toString());
        String string = X500Name.getInstance(CzertainlyX500NameStyle.INSTANCE, new X500Principal("CN=Certificate Authority;O=Organization;L=Location,ST=State, C=US").getEncoded()).toString();
        logger.info(string);
        String stringMod = X500Name.getInstance(CzertainlyX500NameStyle.INSTANCE, new X500Principal("CN=Certificate Authority; L=Location,ST=State, C=US;O=Organization").getEncoded()).toString();
        logger.info(stringMod);
        String stringMod2 = X500Name.getInstance(CzertainlyX500NameStyle.INSTANCE, new X500Principal("CN=Certificate Authority; O=Organization; L=Location,ST=State; C=US").getEncoded()).toString();
        logger.info(stringMod2);
        logger.info(X500Name.getInstance(CzertainlyX500NameStyle.INSTANCE, new X500Principal("CN=Example Root CA; O=Example Corp; EMAIL=admin@example.com,C=US").getEncoded()).toString());
//        logger.info(X500Name.getInstance(X500NameStyleCustom.INSTANCE, new X500Principal("E=3key@company.com, CN=Demo Client Sub CA").getEncoded()).toString());
        logger.info("========");
//        new X500Principal("E=3key@company.com, CN=Demo Client Sub CA");
        String[] dnOrderings = {
                "CN=SSL Issuer; OU=IT Security; O=SecureCorp; L=City; ST=State; C=US; emailAddress=admin@example.com",
                "emailAddress=admin@example.com; CN=SSL Issuer; OU=IT Security; O=SecureCorp; L=City; ST=State; C=US",
                "OU=IT Security; CN=SSL Issuer; O=SecureCorp; L=City; ST=State; C=US; emailAddress=admin@example.com",
                "emailAddress=admin@example.com; OU=IT Security; CN=SSL Issuer; O=SecureCorp; L=City; ST=State; C=US",
                "O=SecureCorp; emailAddress=admin@example.com; CN=SSL Issuer; OU=IT Security; L=City; ST=State; C=US",
                "emailAddress=admin@example.com; O=SecureCorp; CN=SSL Issuer; OU=IT Security; L=City; ST=State; C=US",
                "L=City; emailAddress=admin@example.com; CN=SSL Issuer; OU=IT Security; O=SecureCorp; ST=State; C=US",
                "emailAddress=admin@example.com; L=City; CN=SSL Issuer; OU=IT Security; O=SecureCorp; ST=State; C=US",
                "ST=State; C=US; emailAddress=admin@example.com; CN=SSL Issuer; OU=IT Security; O=SecureCorp; L=City",
                "emailAddress=admin@example.com; ST=State; C=US; CN=SSL Issuer; OU=IT Security; O=SecureCorp; L=City"
        };
        for (String dnOrdering: dnOrderings) {
            logger.info(getDnWithCustomStyle(dnOrdering));
        }
        logger.info("========");
        String[] dnOrderings2 = {
                "CN=SSL Issuer; OU=IT Security; O=SecureCorp; L=City; ST=State; C=US",
                "OU=IT Security; CN=SSL Issuer; O=SecureCorp; L=City; ST=State; C=US",
                "O=SecureCorp; CN=SSL Issuer; OU=IT Security; L=City; ST=State; C=US",
                "L=City; CN=SSL Issuer; OU=IT Security; O=SecureCorp; ST=State; C=US",
                "ST=State; C=US,CN=SSL Issuer; OU=IT Security; O=SecureCorp; L=City",
        };
        for (String dnOrdering: dnOrderings2) {
            logger.info(new X500Principal(dnOrdering).toString());
            logger.info(ASN1Sequence.getInstance(new X500Principal(dnOrdering).getEncoded()).toString());
            logger.info("Default: " + getDnWithCustomStyle(dnOrdering));
            logger.info("Normalized: " + getDnWithCustomStyleNormalized(dnOrdering));

        }

        logger.info(getDnWithCustomStyle("2.5.4.77=SSL Issuer; OU=IT Security; O=SecureCorp; L=City; ST=State; C=US"));
        logger.info(getDnWithCustomStyleNormalized("2.5.4.77=SSL Issuer; OU=IT Security; O=SecureCorp; L=City; ST=State; C=US"));

        logger.info(getDnWithCustomStyle("CN=SSL Issuer; O=SecureCorp;OU=IT Security;L=City; ST=State; C=US; emailAddress=admin@example.com"));
        logger.info(getDnWithCustomStyle("CN=SSL Issuer; OU=IT Security;O=SecureCorp;L=City; ST=State; C=US; emailAddress=admin@example.com"));

        }
    private String getDnWithCustomStyle(String dn){
        return X500Name.getInstance(CzertainlyX500NameStyle.DEFAULT_INSTANCE, new X500Principal(dn).getEncoded()).toString();
    }

    private String getDnWithCustomStyleNormalized(String dn){
        return X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED_INSTANCE, new X500Principal(dn).getEncoded()).toString();
    }
    }


