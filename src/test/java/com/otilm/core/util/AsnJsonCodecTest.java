package com.otilm.core.util;

import com.otilm.api.exception.ValidationException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsnJsonCodecTest {

    @Nested
    class GoldenVectors {

        @Test
        void basicConstraintsCaTruePathLenZero() {
            assertThat(hex("{\"sequence\":[{\"boolean\":true},{\"integer\":0}]}")).isEqualTo("30 06 01 01 FF 02 01 00");
        }

        @Test
        void tlsFeatureStatusRequest() {
            assertThat(hex("{\"sequence\":[{\"integer\":5}]}")).isEqualTo("30 03 02 01 05");
        }

        @Test
        void extendedKeyUsageShapedSequenceOfOids() {
            assertThat(hex("{\"sequence\":[{\"oid\":\"1.3.6.1.5.5.7.3.1\"}]}"))
                    .isEqualTo("30 0A 06 08 2B 06 01 05 05 07 03 01");
        }
    }

    @Nested
    class NodeTypes {

        @Test
        void everyScalarTypeEncodes() {
            assertThat(hex("{\"boolean\":false}")).isEqualTo("01 01 00");
            assertThat(hex("{\"integer\":127}")).isEqualTo("02 01 7F");
            assertThat(hex("{\"oid\":\"2.5.29.19\"}")).isEqualTo("06 03 55 1D 13");
            assertThat(hex("{\"utf8String\":\"a\"}")).isEqualTo("0C 01 61");
            assertThat(hex("{\"ia5String\":\"a\"}")).isEqualTo("16 01 61");
            assertThat(hex("{\"printableString\":\"a\"}")).isEqualTo("13 01 61");
            assertThat(hex("{\"null\":null}")).isEqualTo("05 00");
        }

        @Test
        void octetStringTakesBase64() {
            assertThat(hex("{\"octetString\":\"" + Base64.getEncoder().encodeToString(new byte[]{1, 2}) + "\"}"))
                    .isEqualTo("04 02 01 02");
        }

        @Test
        void bitStringTakesBase64AndPadBits() {
            assertThat(hex("{\"bitString\":{\"value\":\"" + Base64.getEncoder().encodeToString(new byte[]{(byte) 0xA0})
                    + "\",\"padBits\":5}}")).isEqualTo("03 02 05 A0");
        }

        @Test
        void generalizedTimeEncodes() {
            assertThat(hex("{\"generalizedTime\":\"20261231235959Z\"}"))
                    .isEqualTo("18 0F 32 30 32 36 31 32 33 31 32 33 35 39 35 39 5A");
        }

        @Test
        void setEncodes() {
            assertThat(hex("{\"set\":[{\"integer\":1}]}")).isEqualTo("31 03 02 01 01");
        }

        @Test
        void taggedDefaultsToExplicit() {
            assertThat(hex("{\"tagged\":{\"tagNo\":0,\"value\":{\"integer\":1}}}")).isEqualTo("A0 03 02 01 01");
        }

        @Test
        void taggedImplicit() {
            assertThat(hex("{\"tagged\":{\"tagNo\":0,\"explicit\":false,\"value\":{\"integer\":1}}}"))
                    .isEqualTo("80 01 01");
        }
    }

    @Nested
    class Rejections {

        @Test
        void unknownNodeTypeNamesThePath() {
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"sequence\":[{\"int\":2}]}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("$.sequence[0]")
                    .hasMessageContaining("int");
        }

        @Test
        void twoKeysOnOneNodeRejected() {
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"boolean\":true,\"integer\":1}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("exactly one");
        }

        @Test
        void nonJsonRejected() {
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("MAYBAf8CAQA="))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not well-formed JSON");
        }

        @Test
        void malformedOidRejectedWithPath() {
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"sequence\":[{\"oid\":\"serverAuth\"}]}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("$.sequence[0].oid");
        }

        @Test
        void outOfRangePadBitsRejected() {
            // BouncyCastle throws IllegalArgumentException for these; unguarded they would escape as a 500.
            for (String padBits : List.of("9", "-1", "99999999999999999999")) {
                assertThatThrownBy(() -> AsnJsonCodec
                        .encodeFromString("{\"bitString\":{\"value\":\"oA==\",\"padBits\":" + padBits + "}}"))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("$.bitString.padBits");
            }
        }

        @Test
        void padBitsOnEmptyContentRejected() {
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"bitString\":{\"value\":\"\",\"padBits\":5}}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("$.bitString");
        }

        @Test
        void outOfRangeTagNoRejected() {
            for (String tagNo : List.of("-1", "31", "99999999999999999999")) {
                assertThatThrownBy(() -> AsnJsonCodec
                        .encodeFromString("{\"tagged\":{\"tagNo\":" + tagNo + ",\"value\":{\"null\":true}}}"))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("$.tagged.tagNo");
            }
        }

        @Test
        void unparseableGeneralizedTimeRejected() {
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"generalizedTime\":\"hello\"}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("$.generalizedTime");
        }

        @Test
        void charsetInvalidStringsRejected() {
            // The one-arg BC constructors would emit DER that is invalid for the declared string type.
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"printableString\":\"a@b\"}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("$.printableString");
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"ia5String\":\"\u00e9\"}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("$.ia5String");
        }

        @Test
        void badBase64InOctetStringRejectedWithPath() {
            assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"octetString\":\"%%%\"}"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("$.octetString");
        }
    }

    private static String hex(String json) {
        byte[] der = AsnJsonCodec.encodeFromString(json);
        StringBuilder rendered = new StringBuilder();
        for (byte b : der) {
            if (!rendered.isEmpty()) {
                rendered.append(' ');
            }
            rendered.append("%02X".formatted(b));
        }
        return rendered.toString();
    }

    @Test
    void encodesAnAsn1Null() throws Exception {
        assertThat(AsnJsonCodec.encodeFromString("{\"null\":null}")).isEqualTo(new byte[]{0x05, 0x00});
    }

    @Test
    void rejectsANullNodeCarryingAValue() {
        // ASN.1 NULL has no content, so a value here is a mistake; encoding 05 00 anyway would discard it.
        assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"null\":123}"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("$.null");
    }

    @Test
    void sortsSetMembersAsDerRequires() throws Exception {
        // DER orders SET components by encoding, so the declared order is not preserved. Documented, not a bug.
        byte[] declaredOutOfOrder = AsnJsonCodec.encodeFromString("{\"set\":[{\"integer\":5},{\"integer\":1}]}");

        assertThat(declaredOutOfOrder).isEqualTo(new byte[]{0x31, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x05});
    }

    @Test
    void rejectsAnOidNodeTheEncoderCannotRepresent() {
        assertThatThrownBy(() -> AsnJsonCodec.encodeFromString("{\"oid\":\"1.40\"}"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("$.oid");
    }

}
