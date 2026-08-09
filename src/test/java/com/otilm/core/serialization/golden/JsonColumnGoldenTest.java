package com.otilm.core.serialization.golden;

import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.client.attribute.RequestAttributeV3;
import com.otilm.api.model.common.attribute.common.AttributeContent;
import com.otilm.api.model.common.attribute.common.AttributeType;
import com.otilm.api.model.common.attribute.common.MetadataAttribute;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.v2.content.BaseAttributeContentV2;
import com.otilm.api.model.common.attribute.v3.MetadataAttributeV3;
import com.otilm.api.model.common.attribute.v3.content.BaseAttributeContentV3;
import com.otilm.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.otilm.api.model.core.compliance.ComplianceStatus;
import com.otilm.core.dao.converter.ObjectToJsonConverter;
import com.otilm.core.model.compliance.ComplianceResultDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON written into the platform's {@code jsonb} columns.
 * <p>
 * This is the surface with the longest blast radius. An API shape change breaks a caller loudly and immediately; a
 * <i>column</i> shape change is silent and retroactive — rows written before the upgrade stay in the old shape, rows
 * written after use the new one, and the mismatch only surfaces when some unlucky read hits an old row. There is no
 * schema migration guarding these columns, because Hibernate hands the value straight to Jackson, so the JSON
 * mapping <i>is</i> the schema.
 * <p>
 * Every case here uses the Spring wire mapper, which is the same instance Hibernate's JSON type and
 * {@link ObjectToJsonConverter} resolve at runtime. Each is a full round-trip: write, compare to golden, read back,
 * re-write, compare again. The read-back leg is the one that matters most for a column, because it models exactly
 * what the application does to a stored row on every load-and-save cycle.
 */
class JsonColumnGoldenTest {

    private static final OffsetDateTime FIXED_TIMESTAMP =
            OffsetDateTime.of(2026, 1, 15, 9, 30, 0, 123_000_000, ZoneOffset.UTC);

    private final ObjectMapper mapper = GoldenMappers.web();

    /**
     * Backs {@code Certificate.complianceResult}, {@code Secret.complianceResult},
     * {@code CryptographicKeyItem.complianceResult} and {@code CertificateRequestEntity.complianceResult}.
     * <p>
     * Its {@code timestamp} carries an explicit {@code @JsonFormat} pattern, which is the interesting part: that
     * annotation is what currently decides the rendering, overriding the mapper's own date handling. A change in how
     * a major Jackson version reconciles an explicit pattern with the {@code JavaTimeModule} would rewrite every
     * compliance timestamp in the database.
     */
    @Test
    void complianceResultColumnKeepsItsShapeAndRoundTrips() {
        ComplianceResultDto result = new ComplianceResultDto();
        result.setStatus(ComplianceStatus.OK);
        result.setTimestamp(FIXED_TIMESTAMP);
        result.setMessage("All rules satisfied");

        GoldenJson.assertMatchesGoldenAndRoundTrips(
                "column-compliance-result", mapper, result, ComplianceResultDto.class);

        assertThat(mapper.valueToTree(result).path("timestamp").asText())
                .describedAs("the @JsonFormat pattern must keep winning over the mapper's date handling, or every "
                        + "stored compliance timestamp silently changes format")
                .isEqualTo("2026-01-15T09:30:00.123Z");
    }

    /**
     * Backs {@code Certificate.pendingRevokeAttributes}, {@code Secret2SyncVaultProfile.secretAttributes},
     * {@code ComplianceProfileRule.attributes} and {@code ProtocolCertificateAssociations.customAttributes}.
     * <p>
     * Stored polymorphically: {@code RequestAttribute} discriminates on {@code version} with a {@code defaultImpl} of
     * {@code RequestAttributeV2}. A stored V3 attribute that lost its discriminator would not fail on read — it would
     * come back as a V2 attribute, quietly downgrading persisted data.
     */
    @Test
    void requestAttributeListColumnKeepsItsShapeAndRoundTrips() {
        List<RequestAttribute> attributes = List.of(new RequestAttributeV3(
                UUID.fromString("3a8f61d2-0000-4000-8000-000000000001"),
                "revokeReason",
                AttributeContentType.STRING,
                List.of(new StringAttributeContentV3("ref-reason", "keyCompromise"))));

        String golden = "column-request-attributes";
        GoldenJson.assertMatchesGolden(golden, mapper, attributes);
        assertListRoundTrips(golden, attributes, new TypeReference<List<RequestAttribute>>() {
        });
    }

    /** Backs {@code DiscoveryCertificate.meta}. */
    @Test
    void metadataAttributeListColumnKeepsItsShapeAndRoundTrips() {
        MetadataAttributeV3 metadata = new MetadataAttributeV3();
        metadata.setUuid("3a8f61d2-0000-4000-8000-000000000002");
        metadata.setName("discoverySource");
        metadata.setDescription("Where the certificate was discovered");
        metadata.setType(AttributeType.META);
        metadata.setContentType(AttributeContentType.STRING);
        metadata.setContent(List.of(new StringAttributeContentV3("ref-source", "network-scan")));

        List<MetadataAttribute> meta = List.of(metadata);

        String golden = "column-metadata-attributes";
        GoldenJson.assertMatchesGolden(golden, mapper, meta);
        assertListRoundTrips(golden, meta, new TypeReference<List<MetadataAttribute>>() {
        });
    }

    /** Backs {@code AttributeContentItem.json}, the per-item content storage behind every custom attribute value. */
    @Test
    void attributeContentItemColumnKeepsItsShapeAndRoundTrips() {
        BaseAttributeContentV3<String> content = new StringAttributeContentV3("ref-item", "stored content value");

        GoldenJson.assertMatchesGoldenAndRoundTrips(
                "column-attribute-content-item", mapper, content, AttributeContent.class);
    }

    /**
     * {@link ObjectToJsonConverter} backs {@code Approval.objectData}, {@code ScheduledJob.objectData},
     * {@code ConditionItem.value} and {@code ExecutionItem.data}. It is the sharpest edge in this file: it writes an
     * untyped {@code Object} and reads it back as {@code Serializable}, so no target type guides deserialization and
     * the result is whatever Jackson's default binding produces — a {@code LinkedHashMap}, not the original class.
     * <p>
     * That asymmetry is existing, intended behaviour, and the point of this test is to hold the resulting shape
     * still. Jackson 3 changes default binding in this exact area, so a drift here would rewrite stored approval and
     * scheduled-job payloads with no compiler error and no failing mapping anywhere.
     */
    @Test
    void objectToJsonConverterKeepsItsUntypedColumnShapeAcrossTheSerializableReadBack() {
        ObjectToJsonConverter converter = new ObjectToJsonConverter();
        ReflectionTestUtils.setField(converter, "objectMapper", mapper);

        ComplianceResultDto stored = new ComplianceResultDto();
        stored.setStatus(ComplianceStatus.NOK);
        stored.setTimestamp(FIXED_TIMESTAMP);
        stored.setMessage("Rule violated");

        String column = converter.convertToDatabaseColumn(stored);
        Object readBack = converter.convertToEntityAttribute(column);

        GoldenJson.assertMatchesGolden("column-object-converter-untyped", mapper, readBack);

        assertThat(readBack)
                .describedAs("the converter reads back as Serializable with no target type, so the concrete class is "
                        + "lost by design; this pins the shape that loss produces")
                .isInstanceOf(java.util.Map.class);
        assertThat(converter.convertToDatabaseColumn(readBack))
                .describedAs("a stored value must survive a load-and-save cycle byte-identically, or every save "
                        + "rewrites the row into a slightly different shape")
                .isEqualTo(column);
    }

    /**
     * Pins the branch decision inside {@code AttributeContentDeserializer}, the hand-written deserializer registered
     * on {@code AttributeContent} — the declared type of the {@code AttributeContentItem.json} column.
     * <p>
     * Unlike the dormant custom <i>serializer</i>, this deserializer is live on every read of that column, and it
     * chooses between the v2 and v3 content models by a single rule: whether a {@code contentType} property is
     * present and non-null. There is no discriminator and no registered subtype resolution involved — just that
     * check. So a stored v3 content whose {@code contentType} stopped being written would come back as v2 content,
     * silently downgrading the stored value rather than failing.
     * <p>
     * It is also written against Jackson 2 APIs that Jackson 3 changes — {@code JsonParser.getCodec()},
     * {@code JsonDeserializer}, and the checked {@code IOException} contract — so it cannot survive the upgrade
     * unmodified. Whoever rewrites it needs this branch behaviour written down, because nothing else records it.
     */
    @Test
    void attributeContentDeserializerStillPicksTheContentModelFromContentTypePresence() throws Exception {
        String withContentType = mapper.writeValueAsString(new StringAttributeContentV3("ref-item", "v3 content"));
        String withoutContentType = "{\"reference\":\"ref-item\",\"data\":\"v2 content\"}";

        assertThat(mapper.readValue(withContentType, AttributeContent.class))
                .describedAs("a present contentType selects the v3 content model")
                .isInstanceOf(BaseAttributeContentV3.class);
        assertThat(mapper.readValue(withoutContentType, AttributeContent.class))
                .describedAs("an absent contentType falls back to the v2 content model; if a v3 value ever lost its "
                        + "contentType it would be silently downgraded here rather than rejected")
                .isInstanceOf(BaseAttributeContentV2.class);
    }

    /**
     * Round-trip a collection column through its generic element type. A {@code Class} token cannot express
     * {@code List<T>}, and reading a polymorphic list as a raw {@code List} would erase the element type and hide
     * exactly the discriminator regression these goldens exist to catch.
     */
    private <T> void assertListRoundTrips(String goldenName, List<T> value, TypeReference<List<T>> elementType) {
        try {
            String serialized = mapper.writeValueAsString(value);
            List<T> reread = mapper.readValue(serialized, elementType);

            assertThat(mapper.writeValueAsString(reread))
                    .describedAs("round-tripping column golden '%s.json' changed its JSON shape, so a stored row would "
                            + "mutate on every load-and-save cycle", goldenName)
                    .isEqualTo(serialized);
        } catch (Exception e) {
            throw new IllegalStateException("Column golden '" + goldenName + "' failed to round-trip", e);
        }
    }
}
