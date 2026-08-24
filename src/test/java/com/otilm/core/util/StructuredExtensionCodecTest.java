package com.otilm.core.util;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.common.attribute.v3.mapping.ExtendedKeyUsageMappedField;
import com.otilm.api.model.common.attribute.v3.mapping.ExtensionMappedField;
import com.otilm.api.model.common.attribute.v3.mapping.KeyUsageMappedField;
import com.otilm.api.model.core.certificate.CertificateKeyUsage;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredExtensionCodecTest {

    @Nested
    class Discrimination {

        @Test
        void oidForNamesTheTargetExtension() {
            assertThat(StructuredExtensionCodec.oidFor(new KeyUsageMappedField())).isEqualTo("2.5.29.15");
            assertThat(StructuredExtensionCodec.oidFor(new ExtendedKeyUsageMappedField())).isEqualTo("2.5.29.37");
        }

        @Test
        void oidForReturnsNullForAnUnstructuredTarget() {
            ExtensionMappedField field = new ExtensionMappedField();
            field.setExtensionOid("2.5.29.19");
            assertThat(StructuredExtensionCodec.oidFor(field)).isNull();
        }

        @Test
        void structuredTargetNameIsOperatorFacing() {
            assertThat(StructuredExtensionCodec.structuredTargetName("2.5.29.15")).isEqualTo("Key Usage");
            assertThat(StructuredExtensionCodec.structuredTargetName("2.5.29.37")).isEqualTo("Extended Key Usage");
            assertThat(StructuredExtensionCodec.structuredTargetName("2.5.29.19")).isNull();
        }
    }

    @Nested
    class ContentConversion {

        @Test
        void mapsKeyUsageCodesToTheEnum() {
            assertThat(StructuredExtensionCodec.toKeyUsages(List.of("digitalSignature", "cRLSign")))
                    .containsExactly(CertificateKeyUsage.DIGITAL_SIGNATURE, CertificateKeyUsage.CRL_SIGN);
        }

        @Test
        void rejectsAnUnknownKeyUsageCode() {
            assertThatThrownBy(() -> StructuredExtensionCodec.toKeyUsages(List.of("notABit")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("notABit");
        }

        @Test
        void rejectsAPurposeThatIsNotAnOid() {
            assertThatThrownBy(() -> StructuredExtensionCodec.toPurposeOids(List.of("serverAuth")))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("serverAuth");
        }
    }

    @Nested
    class KeyUsageEncoding {

        // A wrong bit layout is the one failure that produces plausible-looking output, so every vector
        // here is the literal DER a correct encoder emits.
        @Test
        void digitalSignatureIsBitZeroWithSevenUnusedBits() {
            assertThat(hex(StructuredExtensionCodec.encodeKeyUsage(usages("digitalSignature"))))
                    .isEqualTo("03 02 07 80");
        }

        @Test
        void keyCertSignIsBitFive() {
            assertThat(hex(StructuredExtensionCodec.encodeKeyUsage(usages("keyCertSign")))).isEqualTo("03 02 02 04");
        }

        @Test
        void twoBitsShareOneOctet() {
            assertThat(hex(StructuredExtensionCodec.encodeKeyUsage(usages("digitalSignature", "keyEncipherment"))))
                    .isEqualTo("03 02 05 A0");
        }

        @Test
        void decipherOnlySpillsIntoASecondOctet() {
            assertThat(hex(StructuredExtensionCodec.encodeKeyUsage(usages("decipherOnly"))))
                    .isEqualTo("03 03 07 00 80");
        }

        @Test
        void bitOrderDoesNotDependOnTheOrderOfTheSelection() {
            assertThat(hex(StructuredExtensionCodec.encodeKeyUsage(usages("keyEncipherment", "digitalSignature"))))
                    .isEqualTo("03 02 05 A0");
        }

        @Test
        void anEmptyListEncodesToNothing() {
            assertThat(StructuredExtensionCodec.encodeKeyUsage(List.of())).isNull();
        }
    }

    @Nested
    class ExtendedKeyUsageEncoding {

        @Test
        void serverAuthIsASequenceOfOneOid() {
            assertThat(hex(StructuredExtensionCodec.encodeExtendedKeyUsage(List.of("1.3.6.1.5.5.7.3.1"))))
                    .isEqualTo("30 0A 06 08 2B 06 01 05 05 07 03 01");
        }

        @Test
        void anEmptyPurposeListEncodesToNothing() {
            assertThat(StructuredExtensionCodec.encodeExtendedKeyUsage(List.of())).isNull();
        }
    }

    @Nested
    class Decoding {

        @Test
        void everyKeyUsageBitRoundTrips() {
            List<CertificateKeyUsage> all = List.of(CertificateKeyUsage.values());
            String encoded = StructuredExtensionCodec.encodeKeyUsage(all);

            StructuredExtensionCodec.Decoded<CertificateKeyUsage> decoded = StructuredExtensionCodec
                    .decodeKeyUsage(encoded);

            assertThat(decoded.values()).containsExactlyElementsOf(all);
            assertThat(decoded.unrepresentable()).isEmpty();
        }

        @Test
        void purposeOidsRoundTrip() {
            List<String> purposes = List.of("1.3.6.1.5.5.7.3.1", "1.3.6.1.5.5.7.3.3");
            String encoded = StructuredExtensionCodec.encodeExtendedKeyUsage(purposes);
            assertThat(StructuredExtensionCodec.decodeExtendedKeyUsage(encoded)).containsExactlyElementsOf(purposes);
        }

        @Test
        void anUnmodelledKeyUsageBitIsReportedRatherThanDropped() {
            // BIT STRING, 12 significant bits, bit 9 set: no CertificateKeyUsage models it. Dropping it
            // would let a strict policy pass a CSR asking for something the platform cannot even name.
            String value = Base64.getEncoder().encodeToString(new byte[]{0x03, 0x03, 0x04, 0x00, 0x40});

            StructuredExtensionCodec.Decoded<CertificateKeyUsage> decoded = StructuredExtensionCodec
                    .decodeKeyUsage(value);

            assertThat(decoded.values()).isEmpty();
            assertThat(decoded.unrepresentable()).containsExactly("bit 9");
        }

        @Test
        void malformedAsn1Throws() {
            String notAsn1 = Base64.getEncoder().encodeToString(new byte[]{0x2A, 0x2A, 0x2A});
            assertThatThrownBy(() -> StructuredExtensionCodec.decodeKeyUsage(notAsn1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void anExtensionOidWithNoStructuredTargetIsRejected() {
            assertThatThrownBy(() -> StructuredExtensionCodec.decodeKeyUsage("AAA="))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Interoperability {

        // Verified against a CSR generated by OpenSSL 2026-08-24:
        // openssl req -new -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
        // -addext "extendedKeyUsage=serverAuth,codeSigning"
        // A golden vector only proves the codec is self-consistent; these prove it agrees with the world.
        private static final String OPENSSL_KEY_USAGE = "AwIFoA==";
        private static final String OPENSSL_EXTENDED_KEY_USAGE = "MBQGCCsGAQUFBwMBBggrBgEFBQcDAw==";

        @Test
        void writesTheSameKeyUsageBytesOpensslWrites() {
            assertThat(StructuredExtensionCodec.encodeKeyUsage(usages("digitalSignature", "keyEncipherment")))
                    .isEqualTo(OPENSSL_KEY_USAGE);
        }

        @Test
        void readsBackTheKeyUsageOpensslWrote() {
            assertThat(StructuredExtensionCodec.decodeKeyUsage(OPENSSL_KEY_USAGE).values())
                    .containsExactly(CertificateKeyUsage.DIGITAL_SIGNATURE, CertificateKeyUsage.KEY_ENCIPHERMENT);
        }

        @Test
        void writesTheSameExtendedKeyUsageBytesOpensslWrites() {
            assertThat(
                    StructuredExtensionCodec.encodeExtendedKeyUsage(List.of("1.3.6.1.5.5.7.3.1", "1.3.6.1.5.5.7.3.3")))
                    .isEqualTo(OPENSSL_EXTENDED_KEY_USAGE);
        }

        @Test
        void readsBackTheExtendedKeyUsageOpensslWrote() {
            assertThat(StructuredExtensionCodec.decodeExtendedKeyUsage(OPENSSL_EXTENDED_KEY_USAGE))
                    .containsExactly("1.3.6.1.5.5.7.3.1", "1.3.6.1.5.5.7.3.3");
        }
    }

    private static List<CertificateKeyUsage> usages(String... codes) {
        return StructuredExtensionCodec.toKeyUsages(List.of(codes));
    }

    /** Renders base64 inner DER as space-separated uppercase hex, so a wrong byte is obvious in the diff. */
    private static String hex(String base64) {
        StringBuilder rendered = new StringBuilder();
        for (byte b : Base64.getDecoder().decode(base64)) {
            if (!rendered.isEmpty()) {
                rendered.append(' ');
            }
            rendered.append("%02X".formatted(b));
        }
        return rendered.toString();
    }
}
