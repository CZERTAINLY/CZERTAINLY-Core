/**
 * Golden-file JSON serialization tests that baseline Jackson 2 behaviour ahead of the Spring Boot 4.1 / Jackson 3
 * migration (OmniTrustILM/core#1941, prep issue #1992).
 *
 * <h2>What these tests are for</h2>
 * Jackson 3 is a major version with changed defaults. Most of what it changes has no compiler signature: a renamed
 * key, a dropped null, a date rendered as a number instead of a string, a polymorphic discriminator that stops being
 * emitted. None of that fails to build, and unit tests that assert on objects rather than on JSON keep passing
 * through all of it. These goldens are the layer that does not.
 *
 * <p>Five surfaces are covered, in rough order of how expensive a silent regression would be:
 * <ul>
 *   <li>{@link com.otilm.core.serialization.golden.JsonColumnGoldenTest} — {@code jsonb} columns. Worst blast radius:
 *       the JSON mapping <i>is</i> the schema, there is no migration guarding it, and drift splits the table into
 *       old-shape and new-shape rows.</li>
 *   <li>{@link com.otilm.core.serialization.golden.ResponseAttributeRedactionGoldenTest} — the hand-written
 *       {@code ResponseAttributeSerializer} that strips secrets out of API responses. This is the platform's primary
 *       redaction, live via a field-level annotation, and if it stopped firing secrets would flow onto the wire.</li>
 *   <li>{@link com.otilm.core.serialization.golden.SecretContainmentGoldenTest} — the shapes
 *       {@code OutboundSecretContainment} inspects. Drift here leaves the guard passing while inspecting nothing.</li>
 *   <li>{@link com.otilm.core.serialization.golden.AttributeTypeInfoGoldenTest} — every {@code @JsonTypeInfo}
 *       hierarchy. Shared with ~40 connector repositories that will not upgrade in lockstep.</li>
 *   <li>{@link com.otilm.core.serialization.golden.AcmeProtocolGoldenTest} and
 *       {@link com.otilm.core.serialization.golden.ApiContractGoldenTest} — the REST and ACME wire contracts.</li>
 * </ul>
 *
 * <h2>Hand-written serializers are the sharpest edge</h2>
 * The {@code interfaces} library contains custom serializers and deserializers written directly against Jackson 2
 * internals — {@code StdSerializer}, {@code JsonParser.getCodec()}, {@code JsonGenerator.getCurrentValue()},
 * privately constructed {@code ObjectMapper}s and the checked {@code IOException} contract. Jackson 3 changes all of
 * these, so they cannot survive the upgrade unmodified and someone will have to rewrite them. Which of them are
 * actually live is not obvious from reading the annotations, so it is recorded here:
 * <ul>
 *   <li>{@code ResponseAttributeSerializer} — <b>live</b>, and security-critical. Declared on the
 *       {@code ResponseAttributeV2.content} field, so the annotation cancellation described below does not apply.</li>
 *   <li>{@code AttributeContentDeserializer} — <b>live</b> on the {@code AttributeContentItem} column and on
 *       controllers taking {@code AttributeContent}. Picks the v2 or v3 content model purely from whether a
 *       {@code contentType} property is present.</li>
 *   <li>{@code BaseAttributeDeserializer} — <b>live</b>. It, not {@code @JsonSubTypes}, is what resolves an attribute
 *       to its concrete class, switching on the {@code version} and {@code type} fields by hand.</li>
 *   <li>{@code BaseAttributeSerializer} — <b>dormant</b>. Every concrete subclass cancels it with a bare
 *       {@code @JsonSerialize}. It would throw if it were ever reached for a polymorphic type.</li>
 * </ul>
 * Note also that {@code BaseAttributeV2}'s {@code @JsonSubTypes} registrations are vestigial: none of the classes it
 * names actually extend it, so deserializing through that type fails with an unresolvable type id.
 *
 * <h2>When a golden fails</h2>
 * During the migration, a golden diff is a <b>finding to explain, not a test to update</b>. Trace it to a documented
 * Jackson 3 behaviour change, decide deliberately whether to accept or fix it, record it on the migration issue, and
 * only then regenerate. Regenerating first destroys the only record of what changed.
 *
 * <p>Outside the migration, a golden failing means an ordinary code change altered a wire or column shape — which is
 * exactly the notification these tests exist to give.
 *
 * <h2>Regenerating</h2>
 * Run the suite with {@code -Dgolden.regenerate=true}; every golden the run touches is rewritten from current
 * behaviour into {@code src/test/resources/golden}. Always review the resulting diff in the pull request — an
 * unreviewed regeneration is indistinguishable from having no baseline at all.
 *
 * <h2>Mapper parity</h2>
 * Goldens are produced with the serializer that actually writes each surface, via {@code GoldenMappers}. The
 * platform has <b>three</b> distinct JSON writers and they do not agree, so picking the wrong one silently
 * baselines a shape production never emits:
 * <ul>
 *   <li><b>Wire mapper</b> ({@code WebAppConfig#jsonObjectMapper}) — every {@code @RestController} response body,
 *       REST DTOs and ACME protocol documents alike, plus {@code ObjectToJsonConverter} and
 *       {@code OutboundSecretContainment}. ISO-8601 dates, {@code NON_NULL} inclusion.</li>
 *   <li><b>Hibernate's {@code FormatMapper}</b> — every {@code @JdbcTypeCode(SqlTypes.JSON)} column. Production
 *       registers no {@code HibernatePropertiesCustomizer}, so Hibernate builds its own mapper with Jackson's
 *       defaults: numeric timestamps, nulls included. The opposite of the wire mapper on both counts.</li>
 *   <li><b>The bare {@code AcmeJsonProcessor} mapper</b> — one call only, the inbound JWS envelope. It does
 *       <i>not</i> serialize the ACME protocol documents, which go out through the wire mapper.</li>
 * </ul>
 * Note the consequence of the second point: the only {@code HibernatePropertiesCustomizer} in the repository is
 * {@code JsonFormatMapperTestConfig}, annotated {@code @Profile("test")}, so integration tests write {@code jsonb}
 * through the wire mapper while production writes it through Hibernate's. That gap is OmniTrustILM/core#2000; the
 * column goldens here deliberately baseline <i>production</i>.
 *
 * <h2>Test-context budget</h2>
 * These are plain JUnit 5 tests with no Spring annotations and no application context, so they add nothing to the
 * count guarded by {@code ContextSignatureGuardTest}. The wire mapper is obtained by calling
 * {@code WebAppConfig#jsonObjectMapper} directly, which takes no collaborators. Keep it that way: adding a
 * {@code @SpringBootTest} here would buy a context boot for no benefit.
 */
package com.otilm.core.serialization.golden;
