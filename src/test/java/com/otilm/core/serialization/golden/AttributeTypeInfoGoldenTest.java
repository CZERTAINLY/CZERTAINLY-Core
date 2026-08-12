package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.BaseAttribute;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.CodeBlockAttributeContentData;
import com.otilm.api.model.common.attribute.common.content.data.FileAttributeContentData;
import com.otilm.api.model.common.attribute.common.content.data.ProgrammingLanguageEnum;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins every polymorphic {@code @JsonTypeInfo} hierarchy: the discriminator's property name, emitted value and
 * placement are three independently breakable wire contracts shared with ~40 connector repositories.
 * <p>
 * All the hierarchies here use {@code As.EXISTING_PROPERTY} with {@code visible = true}, so a document that loses its
 * discriminator deserializes to the {@code defaultImpl} instead of failing.
 */
class AttributeTypeInfoGoldenTest {

    private static final ZonedDateTime FIXED_INSTANT = ZonedDateTime.of(2026, 1, 15, 9, 30, 0, 0, ZoneOffset.UTC);

    private final ObjectMapper mapper = GoldenMappers.web();

    /**
     * One golden per {@code BaseAttributeContentV3} subtype. The date/time subtypes are the ones that flip to numeric
     * timestamps if the {@code JavaTimeModule} or {@code WRITE_DATES_AS_TIMESTAMPS} setting is lost.
     */
    @ParameterizedTest(name = "contentType discriminator: {1}")
    @MethodSource("contentV3Subtypes")
    void baseAttributeContentV3SubtypeKeepsItsDiscriminatorAndPayloadShape(String goldenSuffix, String contentTypeCode,
            BaseAttributeContentV3<?> content) {
        String golden = "attribute-content-v3-" + goldenSuffix;

        GoldenJson.assertMatchesGoldenAndRoundTrips(golden, mapper, content, BaseAttributeContentV3.class);

        assertDiscriminator(content, "contentType", contentTypeCode);
    }

    private static Stream<Arguments> contentV3Subtypes() {
        return Stream
                .of(Arguments.of("boolean", "boolean", new BooleanAttributeContentV3("ref-boolean", Boolean.TRUE)),
                        Arguments
                                .of("codeblock", "codeblock",
                                        new CodeBlockAttributeContentV3("ref-codeblock",
                                                new CodeBlockAttributeContentData(ProgrammingLanguageEnum.JAVA,
                                                        "cmV0dXJuIDA7"))),
                        Arguments.of("date", "date", new DateAttributeContentV3(LocalDate.of(2026, Month.JANUARY, 15))),
                        Arguments
                                .of("datetime", "datetime",
                                        new DateTimeAttributeContentV3("ref-datetime", FIXED_INSTANT)),
                        Arguments.of("file", "file", fileContent()),
                        Arguments.of("float", "float", new FloatAttributeContentV3("ref-float", 1.5f)),
                        Arguments.of("integer", "integer", new IntegerAttributeContentV3("ref-integer", 42)),
                        Arguments
                                .of("object", "object",
                                        new ObjectAttributeContentV3("ref-object", "opaque-serializable")),
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
        ResourceSimpleContentData data = new ResourceSimpleContentData(AttributeResource.AUTHORITY,
                "6f1a4d3c-0000-4000-8000-000000000001", "Issuing Authority", null);
        return new ResourceObjectContent("ref-resource", data);
    }

    /**
     * One golden per {@code ResourceObjectContentData} subtype. Four discriminator values (AUTHORITY, ENTITY, LOCATION,
     * CREDENTIAL) map to the same {@code ResourceSimpleContentData} class, so the emitted value comes from the
     * instance's {@code resource} field rather than from the subtype registration.
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
        return Stream
                .of(Arguments
                        .of("authority", "authorities",
                                simpleResource(AttributeResource.AUTHORITY, "6f1a4d3c-0000-4000-8000-000000000001",
                                        "Issuing Authority")),
                        Arguments
                                .of("entity", "entities",
                                        simpleResource(AttributeResource.ENTITY, "6f1a4d3c-0000-4000-8000-000000000002",
                                                "Entity Instance")),
                        Arguments
                                .of("location", "locations",
                                        simpleResource(AttributeResource.LOCATION,
                                                "6f1a4d3c-0000-4000-8000-000000000003", "Primary Location")),
                        Arguments
                                .of("credential", "credentials",
                                        simpleResource(AttributeResource.CREDENTIAL,
                                                "6f1a4d3c-0000-4000-8000-000000000004", "Connector Credential")),
                        Arguments.of("certificate", "certificates", certificateResource()),
                        // Populated content is a leak shape, covered by SecretContainmentGoldenTest.
                        Arguments
                                .of("secret", "secrets", new ResourceSecretContentData(
                                        "6f1a4d3c-0000-4000-8000-000000000006", "Vault Secret", null)));
    }

    /** The uuid is passed in rather than derived, so goldens survive {@code AttributeResource} gaining constants. */
    private static ResourceSimpleContentData simpleResource(AttributeResource resource, String uuid, String name) {
        return new ResourceSimpleContentData(resource, uuid, name, null);
    }

    private static ResourceCertificateContentData certificateResource() {
        ResourceCertificateContentData data = new ResourceCertificateContentData();
        data.setUuid("6f1a4d3c-0000-4000-8000-000000000005");
        data.setName("TLS Server Certificate");
        return data;
    }

    /**
     * One golden per {@code BaseAttributeV2} subtype. The {@code type} discriminator carries a {@code defaultImpl} of
     * {@code DataAttributeV2}, so a lost discriminator loses the attribute's identity deep inside the platform rather
     * than failing at the parse boundary.
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
     * Reads the attribute back and requires the original class, since the failure mode above is a read-side one.
     * <p>
     * The base type is {@code BaseAttribute}, not {@code BaseAttributeV2}: the latter's {@code @JsonSubTypes} names
     * classes that do not extend it, so reading through it fails with an unresolvable type id. The live read path is
     * {@code BaseAttributeDeserializer}, which switches on {@code version} and {@code type} by hand.
     */
    private void assertResolvesBackToItsOwnSubtype(Object attribute) {
        try {
            String serialized = mapper.writeValueAsString(attribute);
            Object reread = mapper.readValue(serialized, BaseAttribute.class);

            assertThat(reread)
                    .describedAs("%s did not resolve back to its own subtype; BaseAttributeDeserializer switches on "
                            + "'version' and 'type' by hand, so either changing shape yields the wrong class rather "
                            + "than an error", attribute.getClass().getSimpleName())
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

        return Stream
                .of(Arguments.of(AttributeType.DATA.getCode(), data),
                        Arguments.of(AttributeType.GROUP.getCode(), group),
                        Arguments.of(AttributeType.INFO.getCode(), info),
                        Arguments.of(AttributeType.META.getCode(), metadata),
                        Arguments.of(AttributeType.CUSTOM.getCode(), custom));
    }

    /**
     * {@code BaseAttribute} declares {@code @JsonSerialize(using = BaseAttributeSerializer.class)} and every subclass
     * re-declares a bare {@code @JsonSerialize}, cancelling the inherited {@code using}.
     * <p>
     * That cancellation binds to the <i>declared</i> type, so an uncancelling supertype still reaches the hand-written
     * serializer — see {@code JsonColumnGoldenTest}. Under the concrete class the bean serializer must win, and field
     * order is where the two differ most visibly.
     */
    @Test
    void concreteAttributesUseTheBeanSerializerNotTheHandWrittenOne() {
        DataAttributeV2 sparse = new DataAttributeV2();
        sparse.setName("sparseAttribute");
        sparse.setType(AttributeType.DATA);
        sparse.setContentType(AttributeContentType.STRING);

        GoldenJson.assertMatchesGolden("attribute-v2-bean-serializer-output", mapper, sparse);

        JsonNode tree = mapper.valueToTree(sparse);
        assertThat(tree.has("description"))
                .describedAs("the bean serializer honours NON_NULL; the hand-written one would emit an explicit null")
                .isFalse();
        assertThat(tree.fieldNames())
                .toIterable()
                .describedAs("the hand-written serializer leads with 'type' and 'version', the bean serializer "
                        + "follows declaration order")
                .startsWith("name", "version", "type");
    }

    /** Reads the tree rather than substring-matching, so placement is asserted too. */
    private void assertDiscriminator(Object value, String property, String expectedValue) {
        JsonNode tree = mapper.valueToTree(value);

        assertThat(tree.isObject())
                .describedAs("%s must serialize as a JSON object for its discriminator to be a property at all",
                        value.getClass().getSimpleName())
                .isTrue();
        assertThat(tree.has(property))
                .describedAs("%s lost its '%s' discriminator; consumers would fall back to the defaultImpl instead "
                        + "of failing", value.getClass().getSimpleName(), property)
                .isTrue();
        assertThat(tree.get(property).asText())
                .describedAs("%s emitted an unexpected '%s' discriminator value", value.getClass().getSimpleName(),
                        property)
                .isEqualTo(expectedValue);
    }
}
