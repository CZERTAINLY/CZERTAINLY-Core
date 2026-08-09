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
 * <p>Four surfaces are covered, in rough order of how expensive a silent regression would be:
 * <ul>
 *   <li>{@link com.otilm.core.serialization.golden.JsonColumnGoldenTest} — {@code jsonb} columns. Worst blast radius:
 *       the JSON mapping <i>is</i> the schema, there is no migration guarding it, and drift splits the table into
 *       old-shape and new-shape rows.</li>
 *   <li>{@link com.otilm.core.serialization.golden.SecretContainmentGoldenTest} — the shapes
 *       {@code OutboundSecretContainment} inspects. Drift here is a security regression that leaves the guard
 *       passing while inspecting nothing.</li>
 *   <li>{@link com.otilm.core.serialization.golden.AttributeTypeInfoGoldenTest} — every {@code @JsonTypeInfo}
 *       hierarchy. Shared with ~40 connector repositories that will not upgrade in lockstep.</li>
 *   <li>{@link com.otilm.core.serialization.golden.AcmeProtocolGoldenTest} and
 *       {@link com.otilm.core.serialization.golden.ApiContractGoldenTest} — the REST and ACME wire contracts.</li>
 * </ul>
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
 * Goldens are produced with the mappers production actually uses, via
 * {@code GoldenMappers}: the Spring wire mapper for REST DTOs and JSON columns, and the bare mapper for ACME
 * payloads. Producing them with a fresh {@code new ObjectMapper()} would baseline a mapper nothing uses and would
 * miss precisely the customizations most likely to move.
 *
 * <h2>Test-context budget</h2>
 * These are plain JUnit 5 tests with no Spring annotations and no application context, so they add nothing to the
 * count guarded by {@code ContextSignatureGuardTest}. The wire mapper is obtained by calling
 * {@code WebAppConfig#jsonObjectMapper} directly, which takes no collaborators. Keep it that way: adding a
 * {@code @SpringBootTest} here would buy a context boot for no benefit.
 */
package com.otilm.core.serialization.golden;
