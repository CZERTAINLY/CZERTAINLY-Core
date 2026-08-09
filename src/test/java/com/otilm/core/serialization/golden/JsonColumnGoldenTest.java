package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON written into the platform's {@code jsonb} columns.
 * <p>
 * This is the surface with the longest blast radius. An API shape change breaks a caller loudly and immediately; a
 * <i>column</i> shape change is silent and retroactive — rows written before the upgrade keep the old shape, rows
 * written after use the new one, and the mismatch only surfaces when some unlucky read hits an old row. No schema
 * migration guards these columns, because Hibernate hands the value straight to a JSON mapper, so the JSON mapping
 * <i>is</i> the schema.
 * <p>
 * <b>Columns are not serialized by the wire mapper.</b> A {@code @JdbcTypeCode(SqlTypes.JSON)} field goes through
 * Hibernate's own {@code FormatMapper}, and Spring's {@code ObjectMapper} bean reaches it only via a
 * {@code HibernatePropertiesCustomizer} that production does not register — {@link GoldenMappers#hibernateJson()}
 * has the full account. These goldens therefore go through the real {@code FormatMapper} interface, which is what
 * production actually calls. The one exception is {@link ObjectToJsonConverter}, a JPA {@code AttributeConverter}
 * that genuinely injects the Spring bean, and which is tested against the wire mapper for exactly that reason.
 */
class JsonColumnGoldenTest {

    private static final OffsetDateTime FIXED_TIMESTAMP =
            OffsetDateTime.of(2026, 1, 15, 9, 30, 0, 123_000_000, ZoneOffset.UTC);

    private final JacksonJsonFormatMapper columnMapper = GoldenMappers.hibernateJson();

    private final ObjectMapper webMapper = GoldenMappers.web();

    /**
     * The divergence itself, pinned as a test rather than left as a comment.
     * <p>
     * Serializing one object through both writers shows they disagree: the column mapper keeps Jackson's default
     * inclusion and emits the null members the wire mapper omits. This is the assertion that fails first if someone
     * "fixes" the missing {@code HibernatePropertiesCustomizer} without regenerating the column goldens — a change
     * worth making deliberately, but not by accident, since it rewrites the shape of every row written afterwards.
     */
    @Test
    void columnMapperAndWireMapperDisagreeAboutNullInclusion() {
        ComplianceResultDto sparse = new ComplianceResultDto();
        sparse.setStatus(ComplianceStatus.OK);
        sparse.setTimestamp(FIXED_TIMESTAMP);

        assertThat(column(sparse))
                .describedAs("the column mapper keeps Jackson's default inclusion, so unset members are written")
                .contains("\"message\":null");
        assertThat(web(sparse))
                .describedAs("the wire mapper omits them; a column baselined against the wire mapper would pin a "
                        + "shape production never writes")
                .doesNotContain("\"message\"");
    }

    /**
     * Backs {@code Certificate.complianceResult}, {@code Secret.complianceResult},
     * {@code CryptographicKeyItem.complianceResult} and {@code CertificateRequestEntity.complianceResult}.
     * <p>
     * Its {@code timestamp} carries an explicit {@code @JsonFormat} pattern, which is the interesting part: that
     * annotation decides the rendering regardless of which mapper writes it. A change in how a major Jackson version
     * reconciles an explicit pattern with the {@code JavaTimeModule} would rewrite every compliance timestamp in the
     * database.
     */
    @Test
    void complianceResultColumnKeepsItsShapeAndRoundTrips() {
        ComplianceResultDto result = new ComplianceResultDto();
        result.setStatus(ComplianceStatus.OK);
        result.setTimestamp(FIXED_TIMESTAMP);
        result.setMessage("All rules satisfied");

        assertColumnGoldenAndRoundTrip("column-compliance-result", result, ComplianceResultDto.class);

        assertThat(column(result))
                .describedAs("the @JsonFormat pattern must keep winning over the mapper's date handling, or every "
                        + "stored compliance timestamp silently changes format")
                .contains("\"timestamp\":\"2026-01-15T09:30:00.123Z\"");
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

        assertColumnGoldenAndRoundTrip("column-request-attributes", attributes,
                new TypeReference<List<RequestAttribute>>() {
                }.getType());
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

        assertColumnGoldenAndRoundTrip("column-metadata-attributes", List.of(metadata),
                new TypeReference<List<MetadataAttribute>>() {
                }.getType());
    }

    /** Backs {@code AttributeContentItem.json}, the per-item content storage behind every custom attribute value. */
    @Test
    void attributeContentItemColumnKeepsItsShapeAndRoundTrips() {
        BaseAttributeContentV3<String> content = new StringAttributeContentV3("ref-item", "stored content value");

        assertColumnGoldenAndRoundTrip("column-attribute-content-item", content, AttributeContent.class);
    }

    /**
     * Pins the branch decision inside {@code AttributeContentDeserializer}, the hand-written deserializer registered
     * on {@code AttributeContent} — the declared type of the {@code AttributeContentItem.json} column.
     * <p>
     * Unlike the dormant custom <i>serializer</i>, this deserializer runs on every read of that column, and it
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
    void attributeContentDeserializerStillPicksTheContentModelFromContentTypePresence() {
        String withContentType = column(new StringAttributeContentV3("ref-item", "v3 content"));
        String withoutContentType = "{\"reference\":\"ref-item\",\"data\":\"v2 content\"}";

        assertThat(columnMapper.<AttributeContent>fromString(withContentType, AttributeContent.class))
                .describedAs("a present contentType selects the v3 content model")
                .isInstanceOf(BaseAttributeContentV3.class);
        assertThat(columnMapper.<AttributeContent>fromString(withoutContentType, AttributeContent.class))
                .describedAs("an absent contentType falls back to the v2 content model; if a v3 value ever lost its "
                        + "contentType it would be silently downgraded here rather than rejected")
                .isInstanceOf(BaseAttributeContentV2.class);
    }

    /**
     * {@link ObjectToJsonConverter} backs {@code Approval.objectData}, {@code ScheduledJob.objectData},
     * {@code ConditionItem.value} and {@code ExecutionItem.data}. It is the one column path that genuinely uses the
     * <b>wire</b> mapper, because it is a JPA {@code AttributeConverter} with an {@code @Autowired ObjectMapper}
     * rather than a {@code @JdbcTypeCode} field routed through Hibernate's {@code FormatMapper}.
     * <p>
     * It is also the sharpest edge here: it writes an untyped {@code Object} and reads it back as
     * {@code Serializable}, so no target type guides deserialization and the result is whatever Jackson's default
     * binding produces — a map, not the original class. That asymmetry is existing, intended behaviour, and the
     * point is to hold the resulting shape still. Jackson 3 changes default binding in this exact area, so drift
     * would rewrite stored approval and scheduled-job payloads with no compiler error anywhere.
     */
    @Test
    void objectToJsonConverterKeepsItsUntypedColumnShapeAcrossTheSerializableReadBack() {
        ObjectToJsonConverter converter = new ObjectToJsonConverter();
        ReflectionTestUtils.setField(converter, "objectMapper", webMapper);

        ComplianceResultDto stored = new ComplianceResultDto();
        stored.setStatus(ComplianceStatus.NOK);
        stored.setTimestamp(FIXED_TIMESTAMP);
        stored.setMessage("Rule violated");

        String persisted = converter.convertToDatabaseColumn(stored);
        Object readBack = converter.convertToEntityAttribute(persisted);

        GoldenJson.assertMatchesGolden("column-object-converter-untyped", webMapper, readBack);

        assertThat(readBack)
                .describedAs("the converter reads back as Serializable with no target type, so the concrete class is "
                        + "lost by design; this pins the shape that loss produces")
                .isInstanceOf(Map.class);
        assertThat(converter.convertToDatabaseColumn(readBack))
                .describedAs("a stored value must survive a load-and-save cycle byte-identically, or every save "
                        + "rewrites the row into a slightly different shape")
                .isEqualTo(persisted);
    }

    /**
     * Compare a column payload against its golden and require it to survive a load-and-save cycle unchanged.
     * <p>
     * The read-back leg is what matters most for a column: it models exactly what the application does to a stored
     * row every time it loads and saves one. A write-only golden would not notice a type whose read and write sides
     * disagree, which for a column means the row silently mutates on each cycle.
     */
    private void assertColumnGoldenAndRoundTrip(String goldenName, Object value, Type readAs) {
        String serialized = column(value);
        GoldenJson.assertRawJsonMatchesGolden(goldenName, serialized);

        assertThat(column(columnMapper.fromString(serialized, readAs)))
                .describedAs("round-tripping column golden '%s.json' as %s changed its JSON shape, so a stored row "
                        + "would mutate on every load-and-save cycle", goldenName, readAs.getTypeName())
                .isEqualTo(serialized);
    }

    private String column(Object value) {
        return columnMapper.toString(value, value.getClass());
    }

    private String web(Object value) {
        try {
            return webMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize through the wire mapper", e);
        }
    }
}
