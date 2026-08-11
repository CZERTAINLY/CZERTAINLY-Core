package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.api.model.client.attribute.ResponseAttributeV2;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.CredentialAttributeContentData;
import com.otilm.api.model.common.attribute.common.content.data.ProtectionLevel;
import com.otilm.api.model.common.attribute.common.content.data.SecretAttributeContentData;
import com.otilm.api.model.common.attribute.v2.DataAttributeV2;
import com.otilm.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.otilm.api.model.common.attribute.v2.content.CredentialAttributeContentV2;
import com.otilm.api.model.common.attribute.v2.content.SecretAttributeContentV2;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the redaction performed by {@code ResponseAttributeSerializer}, the hand-written serializer that strips secret
 * material out of API responses.
 * <p>
 * This is the platform's <b>primary</b> secret redaction, and it is easy to overlook. Unlike the class-level
 * {@code @JsonSerialize} on {@code BaseAttribute} — which every concrete subclass cancels, leaving it dormant — this
 * one is declared on a <i>field</i>, {@code ResponseAttributeV2.content}. Property-level annotations are not subject to
 * that cancellation, so it is unconditionally live on every v2 response attribute the platform emits.
 * <p>
 * What it does is nulls the {@code data} of secret content before it reaches the wire, keyed on the attribute's
 * {@code contentType}: {@code SECRET} strips the secret directly, {@code CREDENTIAL} descends into the credential's own
 * attributes and strips each nested secret. Everything else passes through untouched. That means a secret is kept out
 * of an API response by a {@code contentType} string comparison inside hand-written serializer code — not by any
 * annotation, and not by {@code OutboundSecretContainment}, which guards a different path entirely.
 * <p>
 * It is written against Jackson 2 internals that Jackson 3 reworks: {@code StdSerializer},
 * {@code JsonGenerator.getCurrentValue()} to reach back to the enclosing bean, a privately constructed
 * {@code ObjectMapper}, and the checked {@code IOException} contract. It cannot survive the upgrade unmodified, and if
 * a rewrite silently stopped firing, secrets would flow into API responses with every test still green. These goldens
 * are what would catch that.
 */
class ResponseAttributeRedactionGoldenTest {

    private static final String SECRET_VALUE = "s3cr3t-must-never-reach-the-wire";

    private final ObjectMapper mapper = GoldenMappers.web();

    /**
     * The {@code SECRET} branch. Redaction works by nulling the content's {@code data}, and because
     * {@code SecretAttributeContentV2} is {@code @JsonInclude(NON_NULL)} the nulled field then disappears from the
     * output entirely — so the wire shows a content entry carrying only its {@code reference}. The entry itself
     * survives, which is deliberate: consumers still learn a secret attribute exists, just not its value.
     * <p>
     * Note the two-step nature of that outcome: the serializer nulls, and the inclusion setting removes. Only the first
     * step is the redaction. If Jackson 3 changed inclusion handling the key could come back as an explicit null —
     * still safe — but the shape would change, and this golden is what would surface it.
     */
    @Test
    void secretContentIsRedactedOutOfTheWireEntirely() {
        ResponseAttributeV2 attribute = responseAttribute(AttributeContentType.SECRET, new SecretAttributeContentV2(
                "ref-secret", new SecretAttributeContentData(SECRET_VALUE, ProtectionLevel.ENCRYPTED)));

        GoldenJson.assertMatchesGolden("redaction-response-attribute-secret", mapper, attribute);

        assertThat(serialized(attribute))
                .describedAs("the secret value must not appear anywhere in the serialized response")
                .doesNotContain(SECRET_VALUE);

        JsonNode content = mapper.valueToTree(attribute).path("content").get(0);
        assertThat(content.path("data").isMissingNode())
                .describedAs("the secret's data must be gone from the wire, not merely emptied")
                .isTrue();
        assertThat(content.path("reference").asText())
                .describedAs("the content entry itself must survive so consumers still see the attribute exists")
                .isEqualTo("ref-secret");
    }

    /**
     * The {@code CREDENTIAL} branch, which is the subtler one: the secret is not at the top level but nested inside the
     * credential's own attribute list, and the serializer has to walk into it. A rewrite that handled the flat
     * {@code SECRET} case but missed this nesting would leak.
     */
    @Test
    void secretsNestedInsideCredentialContentAreAlsoRedacted() {
        DataAttributeV2 nestedSecret = new DataAttributeV2();
        nestedSecret.setUuid("7e3b1f80-0000-4000-8000-000000000002");
        nestedSecret.setName("password");
        nestedSecret.setType(AttributeType.DATA);
        nestedSecret.setContentType(AttributeContentType.SECRET);
        nestedSecret
                .setContent(List
                        .of(new SecretAttributeContentV2("ref-nested-secret",
                                new SecretAttributeContentData(SECRET_VALUE, ProtectionLevel.ENCRYPTED))));

        CredentialAttributeContentData credentialData = new CredentialAttributeContentData();
        credentialData.setUuid("7e3b1f80-0000-4000-8000-000000000003");
        credentialData.setName("Connector Credential");
        credentialData.setKind("Basic");
        credentialData.setAttributes(List.of(nestedSecret));

        ResponseAttributeV2 attribute = responseAttribute(AttributeContentType.CREDENTIAL,
                new CredentialAttributeContentV2("ref-credential", credentialData));

        GoldenJson.assertMatchesGolden("redaction-response-attribute-credential", mapper, attribute);

        assertThat(serialized(attribute))
                .describedAs("a secret nested inside credential content must be redacted too, not just a top-level one")
                .doesNotContain(SECRET_VALUE);
    }

    /**
     * The pass-through branch, and the negative control for the two above. Without it, the redaction tests would still
     * pass if the serializer degenerated into dropping or nulling <i>every</i> content regardless of type — which would
     * be a data-loss bug rather than a leak, and equally worth catching.
     */
    @Test
    void nonSecretContentPassesThroughUnredacted() {
        ResponseAttributeV2 attribute = responseAttribute(AttributeContentType.STRING,
                new BaseAttributeContentV2<>("ref-string", "an ordinary public value"));

        GoldenJson.assertMatchesGolden("redaction-response-attribute-passthrough", mapper, attribute);

        JsonNode data = mapper.valueToTree(attribute).path("content").get(0).path("data");
        assertThat(data.asText())
                .describedAs("ordinary content must reach the wire intact; redacting everything would be data loss")
                .isEqualTo("an ordinary public value");
    }

    /**
     * The serializer reaches the enclosing attribute through {@code JsonGenerator.getCurrentValue()} in order to read
     * its {@code contentType}, then branches on a null check before comparing. Pinning the null-contentType path keeps
     * that guard honest: if the upgrade broke the back-reference, {@code contentType} would read as null here and every
     * attribute would silently take this unredacted branch — including the secret-bearing ones.
     */
    @Test
    void aNullContentTypeTakesTheUnredactedBranchWhichIsWhyTheBackReferenceMatters() {
        ResponseAttributeV2 attribute = responseAttribute(null,
                new BaseAttributeContentV2<>("ref-untyped", "value with no declared content type"));

        GoldenJson.assertMatchesGolden("redaction-response-attribute-null-content-type", mapper, attribute);
    }

    private static ResponseAttributeV2 responseAttribute(AttributeContentType contentType,
            BaseAttributeContentV2<?> content) {
        ResponseAttributeV2 attribute = new ResponseAttributeV2();
        attribute.setUuid(UUID.fromString("7e3b1f80-0000-4000-8000-000000000001"));
        attribute.setName("credentialAttribute");
        attribute.setLabel("Credential");
        attribute.setType(AttributeType.DATA);
        attribute.setContentType(contentType);
        attribute.setContent(List.of(content));
        return attribute;
    }

    private String serialized(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize response attribute", e);
        }
    }
}
