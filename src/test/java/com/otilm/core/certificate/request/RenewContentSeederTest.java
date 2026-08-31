package com.otilm.core.certificate.request;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.connector.v3.certificate.X509RequestContent;
import com.otilm.api.model.core.certificate.GeneralNameType;
import com.otilm.api.model.core.enums.CertificateRequestFormat;
import com.otilm.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.CertificateContent;
import com.otilm.core.dao.entity.CertificateRequestEntity;
import com.otilm.core.util.CertificateTestUtil;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Optional;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenewContentSeederTest {

    @BeforeAll
    static void addBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void seedsFromPredecessorCertificate_whenTheRequestCarriesNoCsr() throws Exception {
        // given — the rekey path passes a null request DTO
        Certificate oldCertificate = certificateEntity(CertificateTestUtil
                .createCertificateWithSubjectAndSans("CN=old.example.com",
                        new GeneralName(GeneralName.dNSName, "old.example.com")));
        Certificate newCertificate = new Certificate();

        // when
        Optional<X509RequestContent> seeded = RenewContentSeeder.seed(oldCertificate, newCertificate, null);

        // then
        assertThat(seeded).isPresent();
        assertThat(seeded.get().getSubject().getFirst().getValue()).isEqualTo("old.example.com");
        assertThat(seeded.get().getSubjectAltNames()).singleElement().satisfies(s -> {
            assertThat(s.getType()).isEqualTo(GeneralNameType.DNS);
            assertThat(s.getValue()).isEqualTo("old.example.com");
        });
    }

    @Test
    void seedsFromPredecessorCertificate_whenRenewReusesTheStoredCsr() throws Exception {
        // given — a renew DTO with no CSR of its own; a CA-rewritten DN travels on the certificate, not the CSR
        Certificate oldCertificate = certificateEntity(
                CertificateTestUtil.createCertificateWithSubjectAndSans("CN=rewritten-by-ca.example.com"));
        Certificate newCertificate = new Certificate();
        newCertificate.setCertificateRequest(csrEntity(pkcs10("CN=as-requested.example.com")));

        // when
        Optional<X509RequestContent> seeded = RenewContentSeeder
                .seed(oldCertificate, newCertificate, ClientCertificateRenewRequestDto.builder().build());

        // then
        assertThat(seeded).isPresent();
        assertThat(seeded.get().getSubject().getFirst().getValue()).isEqualTo("rewritten-by-ca.example.com");
    }

    @Test
    void seedsFromSuppliedCsr_whenTheOperatorProvidedOneOnRenew() throws Exception {
        // given — renew validates only the public key, so a supplied CSR may carry a different identity;
        // structured content must not override what the operator asked for
        Certificate oldCertificate = certificateEntity(CertificateTestUtil
                .createCertificateWithSubjectAndSans("CN=old.example.com",
                        new GeneralName(GeneralName.dNSName, "old.example.com")));
        String supplied = pkcs10("CN=new.example.com");
        Certificate newCertificate = new Certificate();
        newCertificate.setCertificateRequest(csrEntity(supplied));

        // when
        Optional<X509RequestContent> seeded = RenewContentSeeder
                .seed(oldCertificate, newCertificate,
                        ClientCertificateRenewRequestDto.builder().request(supplied).build());

        // then
        assertThat(seeded).isPresent();
        assertThat(seeded.get().getSubject().getFirst().getValue()).isEqualTo("new.example.com");
    }

    @Test
    void seedsNothing_whenAPredecessorSanCannotBeRepresented() throws Exception {
        // given — an x400Address SAN; sending the rest would be an identity narrower than the predecessor's
        Certificate oldCertificate = certificateEntity(CertificateTestUtil
                .createCertificateWithSubjectAndSans("CN=old.example.com",
                        new GeneralName(GeneralName.dNSName, "old.example.com"),
                        new GeneralName(GeneralName.x400Address, new DERSequence())));

        // when
        Optional<X509RequestContent> seeded = RenewContentSeeder.seed(oldCertificate, new Certificate(), null);

        // then — the connector falls back to parsing the CSR the wire already carries
        assertThat(seeded).isEmpty();
    }

    @Test
    void seedsNothing_whenTheStoredCertificateCannotBeParsed() {
        // given
        Certificate oldCertificate = new Certificate();
        CertificateContent content = new CertificateContent();
        content.setContent("bm90LWEtY2VydA==");
        oldCertificate.setCertificateContent(content);

        // when
        Optional<X509RequestContent> seeded = RenewContentSeeder.seed(oldCertificate, new Certificate(), null);

        // then — a broken predecessor must not fail a renew that works today
        assertThat(seeded).isEmpty();
    }

    @Test
    void rekeySanExtensions_carriesThePredecessorSan() throws Exception {
        // given
        X509Certificate oldCertificate = CertificateTestUtil
                .createCertificateWithSubjectAndSans("CN=old.example.com",
                        new GeneralName(GeneralName.dNSName, "old.example.com"),
                        new GeneralName(GeneralName.iPAddress, "10.0.0.1"));

        // when
        Extensions extensions = RenewContentSeeder.rekeySanExtensions(oldCertificate);

        // then
        assertThat(extensions).isNotNull();
        GeneralNames sans = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
        assertThat(sans.getNames()).hasSize(2);
        assertThat(extensions.getExtensionOIDs()).containsExactly(Extension.subjectAlternativeName);
    }

    @Test
    void rekeySanExtensions_returnsNull_whenThePredecessorHasNoSan() throws Exception {
        // given
        X509Certificate oldCertificate = CertificateTestUtil.createCertificateWithSubjectAndSans("CN=old.example.com");

        // when
        Extensions extensions = RenewContentSeeder.rekeySanExtensions(oldCertificate);

        // then — the CSR is built exactly as before this change for a certificate with no SAN
        assertThat(extensions).isNull();
    }

    @Test
    void rekeySanExtensions_failsClosed_whenASanCannotBeRepresented() throws Exception {
        // given — the platform builds this CSR itself, so there is no CSR for the connector to fall back on
        X509Certificate oldCertificate = CertificateTestUtil
                .createCertificateWithSubjectAndSans("CN=old.example.com",
                        new GeneralName(GeneralName.x400Address, new DERSequence()));

        // when / then
        assertThatThrownBy(() -> RenewContentSeeder.rekeySanExtensions(oldCertificate))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("x400Address");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static Certificate certificateEntity(X509Certificate certificate) throws Exception {
        CertificateContent content = new CertificateContent();
        content.setContent(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        Certificate entity = new Certificate();
        entity.setCertificateContent(content);
        return entity;
    }

    private static CertificateRequestEntity csrEntity(String base64Csr) throws NoSuchAlgorithmException {
        CertificateRequestEntity entity = new CertificateRequestEntity();
        entity.setContent(base64Csr);
        entity.setCertificateRequestFormat(CertificateRequestFormat.PKCS10);
        return entity;
    }

    private static String pkcs10(String subjectDn) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDn),
                kp.getPublic());
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen
                .addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.dNSName, "supplied.example.com")));
        builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        return Base64.getEncoder().encodeToString(builder.build(signer).getEncoded());
    }
}
