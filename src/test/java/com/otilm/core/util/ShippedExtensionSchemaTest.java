package com.otilm.core.util;

import com.otilm.api.model.core.oid.OidCategory;
import com.otilm.api.model.core.oid.SystemOid;
import com.otilm.core.oid.OidHandler;
import com.otilm.core.oid.OidRecord;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.NameConstraints;
import org.bouncycastle.asn1.x509.PrivateKeyUsagePeriod;
import org.bouncycastle.asn1.x509.SubjectDirectoryAttributes;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks every Core-shipped extension schema twice over: the tree it accepts really encodes to the extension's ASN.1,
 * proven by handing the bytes back to BouncyCastle's typed class rather than to this codec, and a plausible mistake is
 * rejected. A schema that accepts the wrong shape is worse than none, because it reads as a guarantee.
 */
class ShippedExtensionSchemaTest {

    // The OidHandler cache is process-wide static state shared across the whole test JVM.
    private static Map<String, OidRecord> savedExtensionCache;

    @BeforeAll
    static void snapshotExtensionCache() {
        Map<String, OidRecord> existing = OidHandler.getOidCache(OidCategory.CERTIFICATE_EXTENSION);
        savedExtensionCache = existing == null ? null : new HashMap<>(existing);
    }

    @AfterAll
    static void restoreExtensionCache() {
        OidHandler
                .cacheOidCategory(OidCategory.CERTIFICATE_EXTENSION,
                        savedExtensionCache != null ? savedExtensionCache : new HashMap<>());
    }

    @BeforeEach
    void clearExtensionRegistry() {
        // A registry entry would shadow the shipped resource, which is what these tests are about.
        OidHandler.cacheOidCategory(OidCategory.CERTIFICATE_EXTENSION, new HashMap<>());
    }

    private static byte[] encode(String tree) {
        return AsnJsonCodec.encodeFromString(tree);
    }

    private static void assertAccepts(String oid, String tree) {
        assertThat(ExtensionSchemas.validateShape(oid, AsnJsonCodec.parse(tree)))
                .as("schema for %s should accept %s", oid, tree)
                .isEmpty();
    }

    private static void assertRejects(String oid, String tree) {
        assertThat(ExtensionSchemas.validateShape(oid, AsnJsonCodec.parse(tree)))
                .as("schema for %s should reject %s", oid, tree)
                .isNotEmpty();
    }

    @Test
    void everySystemCertificateExtensionShipsASchema() {
        List<SystemOid> extensions = Arrays
                .stream(SystemOid.values())
                .filter(oid -> oid.getCategory() == OidCategory.CERTIFICATE_EXTENSION)
                .toList();
        assertThat(extensions).isNotEmpty();

        assertThat(extensions)
                .allSatisfy(oid -> assertThat(ExtensionSchemas.shippedSchema(oid.getOid()))
                        .as("no shipped schema for %s (%s)", oid.getOid(), oid.getDisplayName())
                        .isPresent());
    }

    @Test
    void keyUsage() throws Exception {
        String tree = "{\"bitString\":{\"value\":\"gA==\",\"padBits\":7}}";
        assertAccepts("2.5.29.15", tree);

        KeyUsage parsed = KeyUsage.getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(parsed.hasUsages(KeyUsage.digitalSignature)).isTrue();
        assertThat(parsed.hasUsages(KeyUsage.keyCertSign)).isFalse();

        assertRejects("2.5.29.15", "{\"octetString\":\"gA==\"}");
        assertRejects("2.5.29.15", "{\"bitString\":{\"value\":\"gA==\",\"padBits\":9}}");
    }

    @Test
    void extendedKeyUsage() throws Exception {
        String tree = "{\"sequence\":[{\"oid\":\"1.3.6.1.5.5.7.3.1\"}]}";
        assertAccepts("2.5.29.37", tree);

        ExtendedKeyUsage parsed = ExtendedKeyUsage.getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(parsed.hasKeyPurposeId(KeyPurposeId.id_kp_serverAuth)).isTrue();

        // RFC 5280 4.2.1.12: one or more purposes.
        assertRejects("2.5.29.37", "{\"sequence\":[]}");
        assertRejects("2.5.29.37", "{\"sequence\":[{\"utf8String\":\"serverAuth\"}]}");
    }

    @Test
    void basicConstraints() throws Exception {
        String tree = "{\"sequence\":[{\"boolean\":true},{\"integer\":3}]}";
        assertAccepts("2.5.29.19", tree);

        BasicConstraints parsed = BasicConstraints.getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(parsed.isCA()).isTrue();
        assertThat(parsed.getPathLenConstraint()).isEqualTo(java.math.BigInteger.valueOf(3));

        // An end-entity constraint is the empty sequence: X.690 11.5 forbids encoding cA's DEFAULT FALSE.
        assertAccepts("2.5.29.19", "{\"sequence\":[]}");
        assertRejects("2.5.29.19", "{\"sequence\":[{\"boolean\":false}]}");
        assertRejects("2.5.29.19", "{\"sequence\":[{\"boolean\":true},{\"integer\":-1}]}");
    }

    @Test
    void subjectKeyIdentifier() throws Exception {
        String tree = "{\"octetString\":\"AQID\"}";
        assertAccepts("2.5.29.14", tree);

        SubjectKeyIdentifier parsed = SubjectKeyIdentifier.getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(parsed.getKeyIdentifier()).isEqualTo(new byte[]{1, 2, 3});

        assertRejects("2.5.29.14", "{\"sequence\":[{\"octetString\":\"AQID\"}]}");
    }

    @Test
    void tlsFeature() throws Exception {
        String tree = "{\"sequence\":[{\"integer\":5}]}";
        assertAccepts("1.3.6.1.5.5.7.1.24", tree);

        ASN1Sequence parsed = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(ASN1Integer.getInstance(parsed.getObjectAt(0)).intValueExact()).isEqualTo(5);

        assertRejects("1.3.6.1.5.5.7.1.24", "{\"sequence\":[{\"integer\":65536}]}");
    }

    @Test
    void privateKeyUsagePeriod() throws Exception {
        // Both members are IMPLICIT context-tagged, so explicit must be false or the DER means something else.
        String tree = "{\"sequence\":["
                + "{\"tagged\":{\"tagNo\":0,\"explicit\":false,\"value\":{\"generalizedTime\":\"20260101000000Z\"}}},"
                + "{\"tagged\":{\"tagNo\":1,\"explicit\":false,\"value\":{\"generalizedTime\":\"20270101000000Z\"}}}]}";
        assertAccepts("2.5.29.16", tree);

        PrivateKeyUsagePeriod parsed = PrivateKeyUsagePeriod.getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(parsed.getNotBefore()).isNotNull();
        assertThat(parsed.getNotAfter()).isNotNull();
        assertThat(parsed.getNotBefore().getTimeString()).isEqualTo("20260101000000Z");

        assertRejects("2.5.29.16", "{\"sequence\":[{\"tagged\":{\"tagNo\":0,\"explicit\":true,"
                + "\"value\":{\"generalizedTime\":\"20260101000000Z\"}}}]}");
        assertRejects("2.5.29.16", "{\"sequence\":[{\"generalizedTime\":\"20260101000000Z\"}]}");
    }

    @Test
    void nameConstraints() throws Exception {
        String tree = "{\"sequence\":[{\"tagged\":{\"tagNo\":0,\"explicit\":false,\"value\":{\"sequence\":["
                + "{\"sequence\":[{\"tagged\":{\"tagNo\":2,\"explicit\":false,"
                + "\"value\":{\"ia5String\":\"example.com\"}}}]}]}}}]}";
        assertAccepts("2.5.29.30", tree);

        NameConstraints parsed = NameConstraints.getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(parsed.getPermittedSubtrees()).hasSize(1);
        GeneralName base = parsed.getPermittedSubtrees()[0].getBase();
        assertThat(base.getTagNo()).isEqualTo(GeneralName.dNSName);
        assertThat(base.getName().toString()).isEqualTo("example.com");
        assertThat(parsed.getExcludedSubtrees()).isNull();

        // Neither subtree list may be empty, and the outer members are context-tagged.
        assertRejects("2.5.29.30", "{\"sequence\":[]}");
        assertRejects("2.5.29.30",
                "{\"sequence\":[{\"tagged\":{\"tagNo\":0,\"explicit\":false,\"value\":{\"sequence\":[]}}}]}");
        assertRejects("2.5.29.30", "{\"sequence\":[{\"sequence\":[]}]}");
    }

    @Test
    void subjectDirectoryAttributes() throws Exception {
        String tree = "{\"sequence\":[{\"sequence\":[{\"oid\":\"2.5.4.3\"},{\"set\":[{\"utf8String\":\"x\"}]}]}]}";
        assertAccepts("2.5.29.9", tree);

        SubjectDirectoryAttributes parsed = SubjectDirectoryAttributes
                .getInstance(ASN1Primitive.fromByteArray(encode(tree)));
        assertThat(parsed.getAttributes()).hasSize(1);

        // An Attribute is exactly a type and its values, and the values set holds at least one.
        assertRejects("2.5.29.9", "{\"sequence\":[]}");
        assertRejects("2.5.29.9", "{\"sequence\":[{\"sequence\":[{\"oid\":\"2.5.4.3\"}]}]}");
        assertRejects("2.5.29.9", "{\"sequence\":[{\"sequence\":[{\"oid\":\"2.5.4.3\"},{\"set\":[]}]}]}");
    }

    @Test
    void tlsFeatureRejectsAnEmptySequence() {
        // RFC 7633 Features is a SEQUENCE OF INTEGER; an empty one asserts nothing.
        assertRejects("1.3.6.1.5.5.7.1.24", "{\"sequence\":[]}");
    }

    @Test
    void privateKeyUsagePeriodRejectsReversedAndDuplicateMembers() {
        // notBefore [0] precedes notAfter [1] and each appears at most once; a homogeneous items rule let
        // [1],[0] and [0],[0] through, and both encode a malformed sequence.
        String zero = "{\"tagged\":{\"tagNo\":0,\"explicit\":false,"
                + "\"value\":{\"generalizedTime\":\"20260101000000Z\"}}}";
        String one = "{\"tagged\":{\"tagNo\":1,\"explicit\":false,"
                + "\"value\":{\"generalizedTime\":\"20270101000000Z\"}}}";

        assertRejects("2.5.29.16", "{\"sequence\":[" + one + "," + zero + "]}");
        assertRejects("2.5.29.16", "{\"sequence\":[" + zero + "," + zero + "]}");
        assertAccepts("2.5.29.16", "{\"sequence\":[" + zero + "," + one + "]}");
        assertAccepts("2.5.29.16", "{\"sequence\":[" + one + "]}");
    }

    @Test
    void nameConstraintsRejectsReversedOuterFields() {
        String permitted = "{\"tagged\":{\"tagNo\":0,\"explicit\":false,\"value\":{\"sequence\":[{\"sequence\":"
                + "[{\"tagged\":{\"tagNo\":2,\"explicit\":false,\"value\":{\"ia5String\":\"a.test\"}}}]}]}}}";
        String excluded = permitted.replace("\"tagNo\":0", "\"tagNo\":1");

        assertAccepts("2.5.29.30", "{\"sequence\":[" + permitted + "," + excluded + "]}");
        assertRejects("2.5.29.30", "{\"sequence\":[" + excluded + "," + permitted + "]}");
        assertRejects("2.5.29.30", "{\"sequence\":[" + permitted + "," + permitted + "]}");
    }

    @Test
    void nameConstraintsConstrainsTheSubtreeMinimumAndMaximum() {
        // Only the base was constrained, so positions two and three accepted anything.
        String base = "{\"tagged\":{\"tagNo\":2,\"explicit\":false,\"value\":{\"ia5String\":\"a.test\"}}}";
        String minimum = "{\"tagged\":{\"tagNo\":0,\"explicit\":false,\"value\":{\"integer\":1}}}";

        assertAccepts("2.5.29.30", nameConstraintsWithSubtree(base + "," + minimum));
        assertRejects("2.5.29.30", nameConstraintsWithSubtree(base + ",{\"utf8String\":\"oops\"}"));
        assertRejects("2.5.29.30", nameConstraintsWithSubtree(
                base + ",{\"tagged\":{\"tagNo\":0,\"explicit\":false," + "\"value\":{\"integer\":-1}}}"));
    }

    private static String nameConstraintsWithSubtree(String subtreeMembers) {
        return "{\"sequence\":[{\"tagged\":{\"tagNo\":0,\"explicit\":false,\"value\":{\"sequence\":"
                + "[{\"sequence\":[" + subtreeMembers + "]}]}}}]}";
    }

}
