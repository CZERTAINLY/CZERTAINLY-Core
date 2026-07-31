package com.otilm.core.service.cmp.message.validator.impl;

import com.otilm.api.interfaces.core.cmp.error.CmpProcessingException;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIHeaderBuilder;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cmp.RevDetails;
import org.bouncycastle.asn1.cmp.RevReqContent;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.crmf.CertTemplateBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BodyRevocationValidator#validateIn}.
 *
 * <p>Regression coverage for OmniTrustILM/core#1925: the CMP {@code rr} validator used to
 * reject a revocation request that omitted {@code crlEntryDetails} / {@code reasonCode},
 * although RFC 4210 &sect;5.3.9 marks {@code crlEntryDetails} OPTIONAL (see the validator's
 * own Javadoc), while at the same time <em>accepting</em> reason codes 7 (unused) and 8
 * (removeFromCRL) that {@link com.otilm.api.model.core.authority.CertificateRevocationReason}
 * cannot map. This validator must now treat a missing reason as valid and reject only the
 * reason codes the platform cannot represent.</p>
 */
class BodyRevocationValidatorTest {

    @Test
    void acceptsRr_withoutCrlEntryDetails() {
        PKIMessage msg = rrMessage(fullCertTemplate(), null);

        // RFC 4210 §5.3.9: crlEntryDetails is OPTIONAL — a reason-less rr is valid.
        assertThatCode(() -> new BodyRevocationValidator().validateIn(msg, null))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsRr_withCrlEntryDetails_butNoReasonCode() {
        PKIMessage msg = rrMessage(fullCertTemplate(), nonReasonExtensions());

        // crlEntryDetails may carry other crlEntryExtensions (e.g. invalidityDate) without a
        // reasonCode — that is still a valid, reason-less revocation request.
        assertThatCode(() -> new BodyRevocationValidator().validateIn(msg, null))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsRr_withSupportedReasonCode_superseded() {
        PKIMessage msg = rrMessage(fullCertTemplate(), reasonCodeExtensions(4));

        // superseded (4) is a mappable reason — preserved accept behaviour.
        assertThatCode(() -> new BodyRevocationValidator().validateIn(msg, null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRr_withUnusedReasonCode_7() {
        PKIMessage msg = rrMessage(fullCertTemplate(), reasonCodeExtensions(7));

        // reasonCode 7 is unused per RFC 5280 — no CertificateRevocationReason maps to it.
        assertThatThrownBy(() -> new BodyRevocationValidator().validateIn(msg, null))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("is not supported");
    }

    @Test
    void rejectsRr_withRemoveFromCrlReasonCode_8() {
        PKIMessage msg = rrMessage(fullCertTemplate(), reasonCodeExtensions(8));

        // reasonCode 8 (removeFromCRL) is an un-revoke this path cannot perform — reject
        // rather than silently record it as UNSPECIFIED.
        assertThatThrownBy(() -> new BodyRevocationValidator().validateIn(msg, null))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("is not supported");
    }

    @Test
    void rejectsRr_withReasonCodeAboveRange_11() {
        PKIMessage msg = rrMessage(fullCertTemplate(), reasonCodeExtensions(11));

        assertThatThrownBy(() -> new BodyRevocationValidator().validateIn(msg, null))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("is not supported");
    }

    @Test
    void rejectsRr_withoutSerialNumber() {
        PKIMessage msg = rrMessage(certTemplateWithoutSerialNumber(), reasonCodeExtensions(4));

        // The serialNumber guard must still fire ahead of any crlEntryDetails handling.
        assertThatThrownBy(() -> new BodyRevocationValidator().validateIn(msg, null))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("SerialNumber");
    }

    // ------------------------------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------------------------------

    private static PKIMessage rrMessage(CertTemplate certTemplate, Extensions crlEntryDetails) {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(certTemplate);
        if (crlEntryDetails != null) {
            v.add(crlEntryDetails);
        }
        RevReqContent content = new RevReqContent(RevDetails.getInstance(new DERSequence(v)));
        return pkiMessage(new PKIBody(PKIBody.TYPE_REVOCATION_REQ, content));
    }

    private static CertTemplate fullCertTemplate() {
        return new CertTemplateBuilder()
                .setIssuer(new X500Name("CN=ManagementCA"))
                .setSubject(new X500Name("CN=user"))
                .setSerialNumber(new ASN1Integer(BigInteger.valueOf(123456)))
                .build();
    }

    private static CertTemplate certTemplateWithoutSerialNumber() {
        return new CertTemplateBuilder()
                .setIssuer(new X500Name("CN=ManagementCA"))
                .setSubject(new X500Name("CN=user"))
                .build();
    }

    private static Extensions reasonCodeExtensions(long code) {
        try {
            ExtensionsGenerator g = new ExtensionsGenerator();
            g.addExtension(Extension.reasonCode, false, new ASN1Enumerated(BigInteger.valueOf(code)));
            return g.generate();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Extensions nonReasonExtensions() {
        try {
            ExtensionsGenerator g = new ExtensionsGenerator();
            g.addExtension(Extension.invalidityDate, false, new DERGeneralizedTime("20260101000000Z"));
            return g.generate();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PKIMessage pkiMessage(PKIBody body) {
        PKIHeader header = new PKIHeaderBuilder(
                PKIHeader.CMP_2000,
                new GeneralName(new X500Name("CN=test-sender")),
                new GeneralName(new X500Name("CN=test-recipient")))
                .setTransactionID(new DEROctetString(new byte[]{1, 2, 3, 4}))
                .build();
        return new PKIMessage(header, body);
    }
}
