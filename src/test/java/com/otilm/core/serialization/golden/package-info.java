/**
 * Golden-file JSON tests baselining Jackson 2 output ahead of the Spring Boot 4.1 / Jackson 3 migration
 * (OmniTrustILM/core#1941, prep issue #1992). Jackson 3 changes defaults that no compiler catches — a renamed key, a
 * dropped null, a date rendered as a number, a missing polymorphic discriminator — and tests asserting on objects
 * rather than on JSON pass straight through all of it.
 *
 * <p>
 * Covered surfaces, in descending blast radius: {@code jsonb} columns, the {@code ResponseAttributeSerializer}
 * redaction, the shapes {@code OutboundSecretContainment} inspects, the {@code @JsonTypeInfo} hierarchies shared with
 * ~40 connector repositories, and the REST/ACME wire contracts.
 *
 * <h2>Hand-written serializers</h2> The {@code interfaces} library writes directly against Jackson 2 internals, so
 * these cannot survive the upgrade unmodified. Which ones actually run is not visible from the annotations:
 * <ul>
 * <li>{@code ResponseAttributeSerializer} — live and security-critical; declared on the
 * {@code ResponseAttributeV2.content} <i>field</i>, so the subclass cancellation below does not reach it.</li>
 * <li>{@code AttributeContentDeserializer} — live; picks the v2 or v3 content model purely from whether a
 * {@code contentType} property is present.</li>
 * <li>{@code BaseAttributeDeserializer} — live; it, not {@code @JsonSubTypes}, resolves an attribute's concrete
 * class.</li>
 * <li>{@code BaseAttributeSerializer} — dormant; every concrete subclass cancels it with a bare {@code @JsonSerialize},
 * and it would throw if reached for a polymorphic type.</li>
 * </ul>
 * Relatedly, {@code BaseAttributeV2}'s {@code @JsonSubTypes} registrations are vestigial — none of the named classes
 * extend it, so deserializing through that type fails with an unresolvable type id.
 *
 * <h2>When a golden fails</h2> During the migration a diff is a finding to explain, not a test to update: trace it to a
 * documented Jackson 3 behaviour change and decide deliberately before regenerating with
 * {@code -Dgolden.regenerate=true}, reviewing the regenerated diff in the PR. Outside the migration, a failure means an
 * ordinary change altered a wire or column shape.
 *
 * <h2>Mapper parity</h2> Each golden is produced by the writer that actually serves its surface, via
 * {@code GoldenMappers} — the platform has three and they disagree, so the wrong one baselines a shape production never
 * emits. Notably, {@code jsonb} columns go through Hibernate's own mapper with Jackson's defaults (numeric timestamps,
 * nulls included), because the only {@code HibernatePropertiesCustomizer} in the repository is test-profile-only. That
 * gap is OmniTrustILM/core#2000; the column goldens deliberately baseline production.
 *
 * <p>
 * These are plain JUnit 5 tests with no application context, so they add nothing to {@code ContextSignatureGuardTest}.
 * Keep it that way.
 */
package com.otilm.core.serialization.golden;
