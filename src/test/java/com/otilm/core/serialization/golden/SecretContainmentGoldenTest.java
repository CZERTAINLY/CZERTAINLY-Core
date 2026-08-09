package com.otilm.core.serialization.golden;

import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.client.attribute.RequestAttributeV3;
import com.otilm.api.model.client.connector.v2.attribute.AttributeCallbackResponseDto;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.CredentialAttributeContentData;
import com.otilm.api.model.common.attribute.common.content.data.ProtectionLevel;
import com.otilm.api.model.common.attribute.common.content.data.SecretAttributeContentData;
import com.otilm.api.model.common.attribute.v2.content.CredentialAttributeContentV2;
import com.otilm.api.model.common.attribute.v2.content.SecretAttributeContentV2;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.ResourceObjectContent;
import com.otilm.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.data.ResourceSecretContentData;
import com.otilm.api.model.connector.secrets.content.BasicAuthSecretContent;
import com.otilm.core.attribute.engine.OutboundSecretContainment;
import com.otilm.core.attribute.engine.OutboundSecretLeakException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pins the serialized shapes {@link OutboundSecretContainment} depends on, and proves the guard still rejects each of
 * them.
 * <p>
 * This is the one surface where a Jackson shape change is a <b>security</b> regression rather than a compatibility
 * one. The containment guard is fail-closed by design, but both of its checks are shape-coupled:
 * <ul>
 *   <li>the value-echo check compares string leaves of {@code objectMapper.valueToTree(response)} against recorded
 *       secret values — if a secret field stopped being serialized, or started being rendered differently, the
 *       comparison silently stops matching and the check degrades into a no-op that still passes;</li>
 *   <li>the recording path strips the {@code type}, {@code username} and {@code keyStoreType} keys from the secret
 *       tree to avoid false positives — that stripping is keyed on exact property names, so a discriminator rename
 *       under Jackson 3 would leave the discriminator value recorded as if it were a secret.</li>
 * </ul>
 * A test that only asserted "the guard throws" would keep passing through all of that. These tests therefore pin the
 * serialized shape <i>and</i> assert the guard's behaviour against it.
 */
class SecretContainmentGoldenTest {

    private static final String SECRET_VALUE = "s3cr3t-material-expanded-server-side";

    private final ObjectMapper mapper = GoldenMappers.web();

    private final OutboundSecretContainment containment = new OutboundSecretContainment(GoldenMappers.web());

    @Test
    void populatedResourceSecretContentDataKeepsItsShapeAndIsRefused() {
        ResourceSecretContentData data = populatedResourceSecret();

        GoldenJson.assertMatchesGolden("containment-resource-secret-content-data", mapper, data);

        assertThatExceptionOfType(OutboundSecretLeakException.class)
                .describedAs("a populated ResourceSecretContentData must never reach the FE")
                .isThrownBy(() -> containment.assertNoExpandedSecretOutbound(data, Set.of()));
    }

    @Test
    void credentialAttributeContentKeepsItsShapeAndIsRefused() {
        CredentialAttributeContentData credentialData = new CredentialAttributeContentData();
        credentialData.setUuid("9c2e5a71-0000-4000-8000-000000000002");
        credentialData.setName("Connector Credential");
        credentialData.setKind("Basic");
        CredentialAttributeContentV2 content = new CredentialAttributeContentV2("ref-credential", credentialData);

        GoldenJson.assertMatchesGolden("containment-credential-attribute-content-v2", mapper, content);

        assertThatExceptionOfType(OutboundSecretLeakException.class)
                .describedAs("a CredentialAttributeContentV2 is a secret-bearing shape and must be refused by type alone")
                .isThrownBy(() -> containment.assertNoExpandedSecretOutbound(content, Set.of()));
    }

    /**
     * {@code SecretAttributeContentV2} redeclares the {@code data} field its parent already declares, overriding the
     * accessors to match. Jackson resolves the resulting duplicate property today, but a field/accessor visibility
     * change in a major version is exactly what turns a shadowed property into either a duplicated key or a dropped
     * one — and this particular property is the secret itself.
     */
    @Test
    void secretAttributeContentKeepsItsShadowedDataPropertyAndIsRefused() {
        SecretAttributeContentV2 content = new SecretAttributeContentV2(
                "ref-secret", new SecretAttributeContentData(SECRET_VALUE, ProtectionLevel.ENCRYPTED));

        GoldenJson.assertMatchesGolden("containment-secret-attribute-content-v2", mapper, content);

        JsonNode tree = mapper.valueToTree(content);
        assertThat(tree.path("data").path("secret").asText())
                .describedAs("the shadowed 'data' property must still serialize the secret exactly once, at one path; "
                        + "if this moves, the value-echo scan keeps passing while inspecting nothing")
                .isEqualTo(SECRET_VALUE);

        assertThatExceptionOfType(OutboundSecretLeakException.class)
                .isThrownBy(() -> containment.assertNoExpandedSecretOutbound(content, Set.of()));
    }

    @Test
    void populatedSecretAttributeContentDataIsRefused() {
        SecretAttributeContentData data = new SecretAttributeContentData(SECRET_VALUE, ProtectionLevel.ENCRYPTED);

        GoldenJson.assertMatchesGolden("containment-secret-attribute-content-data", mapper, data);

        assertThatExceptionOfType(OutboundSecretLeakException.class)
                .isThrownBy(() -> containment.assertNoExpandedSecretOutbound(data, Set.of()));
    }

    /**
     * The value-echo check on its own, driven through a response shape the structural check does <i>not</i> reject:
     * a plain string content whose value happens to be a secret expanded earlier in the call. If this stops throwing
     * while the structural tests above still pass, serialization has drifted underneath the echo scan.
     */
    @Test
    void valueEchoCheckStillSeesASecretHiddenInAnOtherwiseInnocentResponse() {
        AttributeCallbackResponseDto response = echoingCallbackResponse();

        GoldenJson.assertMatchesGolden("containment-callback-response-echoing-secret", mapper, response);

        assertThatExceptionOfType(OutboundSecretLeakException.class)
                .describedAs("the response carries no secret-typed shape, so only the value-echo scan can catch it")
                .isThrownBy(() -> containment.assertNoExpandedSecretOutbound(response, Set.of(SECRET_VALUE)));
    }

    /**
     * The negative control for the test above. Without it, the echo test could pass because the guard rejects
     * <i>every</i> response of this shape — which is precisely how a shape-coupled check fails silently.
     */
    @Test
    void anEquivalentResponseWithoutTheSecretIsAllowedThrough() {
        assertThatCode(() -> containment.assertNoExpandedSecretOutbound(
                benignCallbackResponse(), Set.of(SECRET_VALUE)))
                .describedAs("a response with no secret must pass, otherwise the echo test proves nothing")
                .doesNotThrowAnyException();
    }

    /**
     * The recording path feeds the value-echo check. Pinning what it records proves the low-entropy keys the recorder
     * strips ({@code type}, {@code username}, {@code keyStoreType}) are still stripped by those exact names: if a
     * discriminator rename slipped past, the discriminator value itself would land in the recorded set and every
     * later response mentioning it would be refused as a false positive.
     * <p>
     * Driven through the public request-attribute entry point, which is the path the v3 attribute-list endpoints
     * actually take when they resolve an authority's own infrastructure references.
     */
    @Test
    void recordingCapturesSecretLeavesButNotTheLowEntropyDiscriminatorAndUsername() {
        Set<String> recorded = new HashSet<>();

        containment.recordExpandedSecretsFromRequest(List.of(resolvedSecretAttribute()), recorded);

        assertThat(recorded)
                .describedAs("the password is the secret leaf that must be recorded for echo detection")
                .contains("basic-auth-password");
        assertThat(recorded)
                .describedAs("the type discriminator and username are low-entropy and must stay unrecorded, or benign "
                        + "responses echoing them would be refused")
                .doesNotContain("basicAuth", "BASIC_AUTH", "connector-user");
    }

    private static RequestAttribute resolvedSecretAttribute() {
        return new RequestAttributeV3(
                UUID.fromString("9c2e5a71-0000-4000-8000-000000000003"),
                "vaultSecret",
                AttributeContentType.RESOURCE,
                List.of(new ResourceObjectContent("ref-secret-resource", populatedResourceSecret())));
    }

    private static ResourceSecretContentData populatedResourceSecret() {
        return new ResourceSecretContentData(
                "9c2e5a71-0000-4000-8000-000000000001",
                "Vault Secret",
                new BasicAuthSecretContent("connector-user", "basic-auth-password"));
    }

    private static AttributeCallbackResponseDto echoingCallbackResponse() {
        AttributeCallbackResponseDto response = new AttributeCallbackResponseDto();
        response.setContent(List.of(stringContent(SECRET_VALUE)));
        return response;
    }

    private static AttributeCallbackResponseDto benignCallbackResponse() {
        AttributeCallbackResponseDto response = new AttributeCallbackResponseDto();
        response.setContent(List.of(stringContent("a perfectly ordinary dropdown option")));
        return response;
    }

    private static BaseAttributeContentV3<?> stringContent(String value) {
        return new StringAttributeContentV3("ref-option", value);
    }
}
