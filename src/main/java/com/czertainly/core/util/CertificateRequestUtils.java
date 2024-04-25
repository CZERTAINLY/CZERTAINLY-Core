package com.czertainly.core.util;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContent;
import com.czertainly.api.model.core.enums.CertificateRequestFormat;
import com.czertainly.core.attribute.CsrAttributes;
import com.czertainly.core.model.request.CertificateRequest;
import com.czertainly.core.model.request.CrmfCertificateRequest;
import com.czertainly.core.model.request.Pkcs10CertificateRequest;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringWriter;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

public class CertificateRequestUtils {
    private static final Logger logger = LoggerFactory.getLogger(CertificateRequestUtils.class);

    public static JcaPKCS10CertificationRequest csrStringToJcaObject(String csr) throws IOException {
        csr = normalizeCsrContent(csr);
        logger.debug("Decoding Base64-encoded CSR: " + csr);
        byte[] decoded = Base64.getDecoder().decode(csr);
        return new JcaPKCS10CertificationRequest(decoded);
    }

    /**
     * Function to convert the content from bytearray to string
     * @param encoded Encoded content
     * @return CSR String
     * @throws IOException when there is an issue reading the content
     */
    public static String byteArrayCsrToString(byte[] encoded) throws IOException {
        PemObject pemObject = new PemObject("CERTIFICATE REQUEST", encoded);
        StringWriter str = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(str);
        pemWriter.writeObject(pemObject);
        pemWriter.close();
        str.close();
        return normalizeCsrContent(str.toString());
    }


    public static PublicKey publicKeyObjectFromString(String publicKey, String pqcAlgorithm) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] publicBytes = Base64.getDecoder().decode(publicKey);

        PublicKey publicKeyObject;
        try {
            String oid = org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(publicBytes)
                    .getAlgorithm().getAlgorithm().toString();
            publicKeyObject = KeyFactory.getInstance(oid, new org.bouncycastle.jce.provider.BouncyCastleProvider())
                    .generatePublic(new X509EncodedKeySpec(publicBytes));
        } catch (NoSuchAlgorithmException e) {
            try {

                publicKeyObject = KeyFactory.getInstance(pqcAlgorithm, BouncyCastlePQCProvider.PROVIDER_NAME)
                        .generatePublic(new X509EncodedKeySpec(publicBytes));
            } catch (Exception e1){
                logger.error(e1.getMessage());
                throw new NoSuchAlgorithmException();
            }
        }
        return publicKeyObject;
    }

    public static X500Principal buildSubject(List<RequestAttributeDto> attributes) {

        // Get the data for the attributes
        String commonName = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                        CsrAttributes.COMMON_NAME_ATTRIBUTE_NAME,
                        attributes,
                        StringAttributeContent.class)
                .getData();

        String organizationalUnit = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                        CsrAttributes.ORGANIZATION_UNIT_ATTRIBUTE_NAME,
                        attributes,
                        StringAttributeContent.class)
                .getData();

        String organization = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                        CsrAttributes.ORGANIZATION_ATTRIBUTE_NAME,
                        attributes,
                        StringAttributeContent.class)
                .getData();

        String locality = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                        CsrAttributes.LOCALITY_ATTRIBUTE_NAME,
                        attributes,
                        StringAttributeContent.class)
                .getData();

        String state = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                        CsrAttributes.STATE_ATTRIBUTE_NAME,
                        attributes,
                        StringAttributeContent.class)
                .getData();

        String country = AttributeDefinitionUtils.getSingleItemAttributeContentValue(
                        CsrAttributes.COUNTRY_ATTRIBUTE_NAME,
                        attributes,
                        StringAttributeContent.class)
                .getData();

        StringBuilder nameBuilder = new StringBuilder();
        if (commonName != null) {
            nameBuilder.append("CN=").append(commonName);
        }
        if (organizationalUnit != null) {
            nameBuilder.append(", OU=").append(organizationalUnit);
        }
        if (organization != null) {
            nameBuilder.append(", O=").append(organization);
        }
        if (locality != null) {
            nameBuilder.append(", L=").append(locality);
        }
        if (state != null) {
            nameBuilder.append(", ST=").append(state);
        }
        if (country != null) {
            nameBuilder.append(", C=").append(country);
        }
        return new X500Principal(nameBuilder.toString());
    }

    public static String normalizeCsrContent(String csr) {
        csr = csr.replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replace("\r", "").replace("\n", "")
                .replace("-----END CERTIFICATE REQUEST-----", "");

        // some of the X.509 CSRs can have different headers and footers
        csr = csr.replace("-----BEGIN NEW CERTIFICATE REQUEST-----", "")
                .replace("\r", "").replace("\n", "")
                .replace("-----END NEW CERTIFICATE REQUEST-----", "");
        return csr;
    }

    public static CertificateRequest createCertificateRequest(byte[] certificateRequest, CertificateRequestFormat format) throws IOException {
        switch (format) {
            case PKCS10 -> {
                return new Pkcs10CertificateRequest(certificateRequest);
            }
            case CRMF -> {
                return new CrmfCertificateRequest(certificateRequest);
            }
            default -> throw new IllegalArgumentException("Unsupported certificate request format: " + format);
        }
    }

}
