package com.otilm.core.serialization.golden;

import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.CodeBlockAttributeContentData;
import com.otilm.api.model.common.attribute.common.content.data.FileAttributeContentData;
import com.otilm.api.model.common.attribute.common.content.data.ProgrammingLanguageEnum;
import com.otilm.api.model.common.attribute.common.BaseAttribute;
import com.otilm.api.model.common.attribute.v2.CustomAttributeV2;
import com.otilm.api.model.common.attribute.v2.DataAttributeV2;
import com.otilm.api.model.common.attribute.v2.GroupAttributeV2;
import com.otilm.api.model.common.attribute.v2.InfoAttributeV2;
import com.otilm.api.model.common.attribute.v2.MetadataAttributeV2;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.BooleanAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.CodeBlockAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.DateAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.DateTimeAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.FileAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.FloatAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.IntegerAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.ObjectAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.ResourceObjectContent;
import com.otilm.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.TextAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.TimeAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.data.ResourceCertificateContentData;
import com.otilm.api.model.common.attribute.v3.content.data.ResourceObjectContentData;
import com.otilm.api.model.common.attribute.v3.content.data.ResourceSecretContentData;
import com.otilm.api.model.common.attribute.v3.content.data.ResourceSimpleContentData;
import com.otilm.api.model.core.auth.AttributeResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON shape of every polymorphic {@code @JsonTypeInfo} hierarchy the platform puts on the wire.
 * <p>
 * These are the highest-risk types in the Jackson 3 migration. A polymorphic type's discriminator has three
 * independently breakable properties — the property <b>name</b>, the emitted <b>value</b>, and its <b>placement</b>
 * in the object — and every one of them is a wire contract shared with ~40 connector repositories. All the
 * hierarchies here use {@code As.EXISTING_PROPERTY} with {@code visible = true}, meaning the discriminator is a real
 * field that Jackson must both write and populate on read; that arrangement is more fragile than a synthetic
 * property, because a change in Jackson's handling can silently produce a document with no discriminator at all,
 * which then deserializes to the {@code defaultImpl} instead of failing.
 */
class AttributeTypeInfoGoldenTest {

    private static final ZonedDateTime FIXED_INSTANT = ZonedDateTime.of(2026, 1, 15, 9, 30, 0, 0, ZoneOffset.UTC);

    private final ObjectMapper mapper = GoldenMappers.web();

    /**
     * One golden per {@code BaseAttributeContentV3} subtype, asserting the {@code contentType} discriminator and the
     * rendering of the payload the subtype wraps. The scalar-rendering half matters as much as the discriminator:
     * the date/time subtypes are the ones that would flip to numeric timestamps if the {@code JavaTimeModule} or the
     * {@code WRITE_DATES_AS_TIMESTAMPS} setting were lost in the upgrade.
     */
    @ParameterizedTest(name = "contentType discriminator: {1}")
    @MethodSource("contentV3Subtypes")
    void baseAttributeContentV3SubtypeKeepsItsDiscriminatorAndPayloadShape(String goldenSuffix,
                                                                          String contentTypeCode,
                                                                          BaseAttributeContentV3<?> content) {
        String golden = "attribute-content-v3-" + goldenSuffix;

        GoldenJson.assertMatchesGoldenAndRoundTrips(golden, mapper, content, BaseAttributeContentV3.class);

        assertDiscriminator(content, "contentType", contentTypeCode);
    }

    private static Stream<Arguments> contentV3Subtypes() {
        return Stream.of(
                Arguments.of("boolean", "boolean", new BooleanAttributeContentV3("ref-boolean", Boolean.TRUE)),
                Arguments.of("codeblock", "codeblock", new CodeBlockAttributeContentV3("ref-codeblock",
                        new CodeBlockAttributeContentData(ProgrammingLanguageEnum.JAVA, "cmV0dXJuIDA7"))),
                Arguments.of("date", "date", new DateAttributeContentV3(LocalDate.of(2026, 1, 15))),
                Arguments.of("datetime", "datetime", new DateTimeAttributeContentV3("ref-datetime", FIXED_INSTANT)),
                Arguments.of("file", "file", fileContent()),
                Arguments.of("float", "float", new FloatAttributeContentV3("ref-float", 1.5f)),
                Arguments.of("integer", "integer", new IntegerAttributeContentV3("ref-integer", 42)),
                Arguments.of("object", "object", new ObjectAttributeContentV3("ref-object", "opaque-serializable")),
                Arguments.of("string", "string", new StringAttributeContentV3("ref-string", "a string value")),
                Arguments.of("text", "text", new TextAttributeContentV3("ref-text", "a longer block of text")),
                Arguments.of("time", "time", new TimeAttributeContentV3(LocalTime.of(9, 30))),
                Arguments.of("resource", "resource", resourceContent()));
    }

    private static FileAttributeContentV3 fileContent() {
        FileAttributeContentData data = new FileAttributeContentData();
        data.setContent("ZmlsZS1ib2R5");
        data.setFileName("bundle.pem");
        data.setMimeType("application/x-pem-file");
        return new FileAttributeContentV3("ref-file", data);
    }

    private static ResourceObjectContent resourceContent() {
        ResourceSimpleContentData data = new ResourceSimpleContentData(
                AttributeResource.AUTHORITY, "6f1a4d3c-0000-4000-8000-000000000001", "Issuing Authority", null);
        return new ResourceObjectContent("ref-resource", data);
    }

    /**
     * One golden per {@code ResourceObjectContentData} subtype. This hierarchy is unusual in that four distinct
     * discriminator values (AUTHORITY, ENTITY, LOCATION, CREDENTIAL) all map to the same {@code
     * ResourceSimpleContentData} class, so the emitted value comes from the instance's {@code resource} field rather
     * than from the subtype registration — pinning each one separately is what proves the mapping did not collapse.
     */
    @ParameterizedTest(name = "resource discriminator: {1}")
    @MethodSource("resourceContentDataSubtypes")
    void resourceObjectContentDataSubtypeKeepsItsDiscriminator(String goldenSuffix, String resourceCode,
                                                               ResourceObjectContentData data) {
        String golden = "resource-content-data-" + goldenSuffix;

        GoldenJson.assertMatchesGoldenAndRoundTrips(golden, mapper, data, ResourceObjectContentData.class);

        assertDiscriminator(data, "resource", resourceCode);
    }

    private static Stream<Arguments> resourceContentDataSubtypes() {
        return Stream.of(
                Arguments.of("authority", "authorities", simpleResource(AttributeResource.AUTHORITY, "Issuing Authority")),
                Arguments.of("entity", "entities", simpleResource(AttributeResource.ENTITY, "Entity Instance")),
                Arguments.of("location", "locations", simpleResource(AttributeResource.LOCATION, "Primary Location")),
                Arguments.of("credential", "credentials", simpleResource(AttributeResource.CREDENTIAL, "Connector Credential")),
                Arguments.of("certificate", "certificates", certificateResource()),
                // Deliberately left with a null content: a populated one is a leak shape that
                // OutboundSecretContainment refuses outright, and is covered by SecretContainmentGoldenTest.
                Arguments.of("secret", "secrets", new ResourceSecretContentData(
                        "6f1a4d3c-0000-4000-8000-000000000006", "Vault Secret", null)));
    }

    private static ResourceSimpleContentData simpleResource(AttributeResource resource, String name) {
        return new ResourceSimpleContentData(resource, "6f1a4d3c-0000-4000-8000-00000000000" + resource.ordinal(), name, null);
    }

    private static ResourceCertificateContentData certificateResource() {
        ResourceCertificateContentData data = new ResourceCertificateContentData();
        data.setUuid("6f1a4d3c-0000-4000-8000-000000000005");
        data.setName("TLS Server Certificate");
        return data;
    }

    /**
     * One golden per {@code BaseAttributeV2} subtype. The {@code type} discriminator carries a {@code defaultImpl} of
     * {@code DataAttributeV2}, which is the dangerous part: if Jackson 3 stopped emitting the discriminator, reads
     * would not fail loudly — every attribute would quietly deserialize as a data attribute, and a metadata or custom
     * attribute would lose its identity somewhere deep inside the platform rather than at the parse boundary.
     */
    @ParameterizedTest(name = "type discriminator: {0}")
    @MethodSource("attributeV2Subtypes")
    void baseAttributeV2SubtypeKeepsItsDiscriminator(String attributeTypeCode, Object attribute) {
        String golden = "attribute-v2-" + attributeTypeCode.toLowerCase();

        GoldenJson.assertMatchesGolden(golden, mapper, attribute);

        assertDiscriminator(attribute, "type", attributeTypeCode);
        assertResolvesBackToItsOwnSubtype(attribute);
    }

    /**
     * Read the serialized attribute back through {@code BaseAttribute} and require the original class.
     * <p>
     * Without this, the surrounding test would only prove the discriminator was <i>written</i> — and weakly, since
     * the expected value comes from the same {@code getCode()} the serializer calls. The failure mode described
     * above is a <i>read</i>-side one, and only deserializing can detect it.
     * <p>
     * The base type here is {@code BaseAttribute}, not {@code BaseAttributeV2}, and that distinction is itself a
     * finding worth recording. {@code BaseAttributeV2} carries a {@code @JsonSubTypes} list naming
     * {@code DataAttributeV2} and friends, but none of them actually extend it — they extend
     * {@code DataAttribute extends BaseAttribute}. Those registrations are therefore vestigial, and deserializing
     * through {@code BaseAttributeV2} fails outright with an unresolvable type id. The live read path is
     * {@code BaseAttribute}, handled by the hand-written {@code BaseAttributeDeserializer}, which ignores
     * {@code @JsonSubTypes} entirely and switches on the {@code version} and {@code type} fields by hand.
     * <p>
     * So this assertion covers the deserializer that actually runs in production — one written against Jackson 2
     * APIs the upgrade must rewrite — rather than an annotation arrangement that never resolves anything.
     */
    private void assertResolvesBackToItsOwnSubtype(Object attribute) {
        try {
            String serialized = mapper.writeValueAsString(attribute);
            Object reread = mapper.readValue(serialized, BaseAttribute.class);

            assertThat(reread)
                    .describedAs("%s did not resolve back to its own subtype; BaseAttributeDeserializer switches on "
                            + "the 'version' and 'type' fields by hand, so either field changing shape silently "
                            + "produces the wrong attribute class rather than an error",
                            attribute.getClass().getSimpleName())
                    .isInstanceOf(attribute.getClass());
        } catch (Exception e) {
            throw new IllegalStateException("Attribute did not survive a polymorphic round trip", e);
        }
    }

    private static Stream<Arguments> attributeV2Subtypes() {
        DataAttributeV2 data = new DataAttributeV2();
        data.setUuid("1b7c9e20-0000-4000-8000-000000000001");
        data.setName("dataAttribute");
        data.setDescription("A data attribute");
        data.setType(AttributeType.DATA);

        GroupAttributeV2 group = new GroupAttributeV2();
        group.setUuid("1b7c9e20-0000-4000-8000-000000000002");
        group.setName("groupAttribute");
        group.setDescription("A group attribute");

        InfoAttributeV2 info = new InfoAttributeV2();
        info.setUuid("1b7c9e20-0000-4000-8000-000000000003");
        info.setName("infoAttribute");
        info.setDescription("An info attribute");

        MetadataAttributeV2 metadata = new MetadataAttributeV2();
        metadata.setUuid("1b7c9e20-0000-4000-8000-000000000004");
        metadata.setName("metadataAttribute");
        metadata.setDescription("A metadata attribute");

        CustomAttributeV2 custom = new CustomAttributeV2();
        custom.setUuid("1b7c9e20-0000-4000-8000-000000000005");
        custom.setName("customAttribute");
        custom.setDescription("A custom attribute");

        return Stream.of(
                Arguments.of(AttributeType.DATA.getCode(), data),
                Arguments.of(AttributeType.GROUP.getCode(), group),
                Arguments.of(AttributeType.INFO.getCode(), info),
                Arguments.of(AttributeType.META.getCode(), metadata),
                Arguments.of(AttributeType.CUSTOM.getCode(), custom));
    }

    /**
     * Pins the fact that concrete attribute classes serialize through Jackson's ordinary bean serializer, <i>not</i>
     * through the hand-written {@code BaseAttributeSerializer} their base class registers.
     * <p>
     * {@code BaseAttribute} declares {@code @JsonSerialize(using = BaseAttributeSerializer.class)}, but every
     * concrete subclass re-declares a bare {@code @JsonSerialize}, which cancels the inherited {@code using}. The
     * custom serializer is therefore dormant on all of them — and it is not merely redundant, it is incompatible:
     * it writes fields in a different order ({@code type} and {@code version} first), emits every field
     * unconditionally including nulls, and implements no type-id handling at all, so it throws outright if it is ever
     * reached for a type that participates in polymorphic typing.
     * <p>
     * This makes annotation-cancellation semantics load-bearing. If a Jackson 3 port changed how a bare
     * {@code @JsonSerialize} interacts with an inherited one, the dormant serializer would wake up and every
     * attribute in the platform would change field order and start carrying explicit nulls — or fail to serialize.
     * Asserting the field order is what detects that, because field order is where the two serializers differ most
     * visibly.
     */
    @Test
    void concreteAttributesUseTheBeanSerializerNotTheDormantHandWrittenOne() {
        DataAttributeV2 sparse = new DataAttributeV2();
        sparse.setName("sparseAttribute");
        sparse.setType(AttributeType.DATA);
        sparse.setContentType(AttributeContentType.STRING);

        GoldenJson.assertMatchesGolden("attribute-v2-bean-serializer-output", mapper, sparse);

        JsonNode tree = mapper.valueToTree(sparse);
        assertThat(tree.has("description"))
                .describedAs("the bean serializer honours NON_NULL, so an unset field is absent; the hand-written "
                        + "serializer would have emitted it as an explicit null")
                .isFalse();
        assertThat(tree.fieldNames()).toIterable()
                .describedAs("field order distinguishes the two serializers: the hand-written one leads with 'type' "
                        + "and 'version', the bean serializer follows declaration order")
                .startsWith("name", "version", "type");
    }

    /**
     * Assert the discriminator is a real, top-level property of the serialized object carrying the expected value.
     * Reading the tree rather than substring-matching the JSON is what makes this an assertion about placement:
     * a discriminator nested one level deeper, or emitted as a wrapper, would still contain the same text.
     */
    private void assertDiscriminator(Object value, String property, String expectedValue) {
        JsonNode tree = mapper.valueToTree(value);

        assertThat(tree.isObject())
                .describedAs("%s must serialize as a JSON object for its discriminator to be a property at all",
                        value.getClass().getSimpleName())
                .isTrue();
        assertThat(tree.has(property))
                .describedAs("%s lost its '%s' discriminator property; consumers would fall back to the defaultImpl "
                        + "instead of failing", value.getClass().getSimpleName(), property)
                .isTrue();
        assertThat(tree.get(property).asText())
                .describedAs("%s emitted an unexpected '%s' discriminator value", value.getClass().getSimpleName(), property)
                .isEqualTo(expectedValue);
    }
}
