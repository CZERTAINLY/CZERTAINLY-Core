package com.otilm.core.serialization.golden;

import com.otilm.api.model.core.acme.Account;
import com.otilm.api.model.core.acme.AccountStatus;
import com.otilm.api.model.core.acme.Authorization;
import com.otilm.api.model.core.acme.AuthorizationStatus;
import com.otilm.api.model.core.acme.Challenge;
import com.otilm.api.model.core.acme.ChallengeStatus;
import com.otilm.api.model.core.acme.ChallengeType;
import com.otilm.api.model.core.acme.Directory;
import com.otilm.api.model.core.acme.DirectoryMeta;
import com.otilm.api.model.core.acme.Identifier;
import com.otilm.api.model.core.acme.Order;
import com.otilm.api.model.core.acme.OrderStatus;
import com.otilm.api.model.core.acme.Problem;
import com.otilm.api.model.core.acme.ProblemDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pins the ACME (RFC 8555) JSON wire contract.
 * <p>
 * ACME is the only JSON-based protocol core speaks — SCEP, CMP and TSP are ASN.1 / PKCS#7 binary formats that never
 * pass through Jackson, so they are deliberately out of scope for golden files. What makes ACME worth pinning is
 * that its counterparties are third-party clients (certbot, acme.sh, cert-manager and friends) that will not be
 * upgraded in lockstep with us and that parse these documents strictly against the RFC. A field that changes name,
 * or starts appearing as an explicit {@code null} where it used to be absent, is an interoperability break we would
 * otherwise discover from the field rather than from CI.
 * <p>
 * These documents are serialized by the <b>wire mapper</b>, not by the bare mapper inside {@code AcmeJsonProcessor}.
 * {@code AcmeControllerImpl} returns every one of them as a {@code ResponseEntity} body, so they go out through the
 * {@code MappingJackson2HttpMessageConverter} that {@code WebAppConfig} configures — which means ACME responses do
 * inherit {@code NON_NULL} inclusion and the {@code JavaTimeModule}. The bare {@code AcmeJsonProcessor} mapper
 * serves exactly one call in the codebase (the inbound JWS envelope) and is covered separately below; baselining
 * these documents against it would have pinned a shape production never emits.
 */
class AcmeProtocolGoldenTest {

    private final ObjectMapper mapper = GoldenMappers.web();

    @Test
    void directoryKeepsItsWireShape() {
        Directory directory = new Directory();
        directory.setNewNonce("https://acme.example/acme/new-nonce");
        directory.setNewAccount("https://acme.example/acme/new-account");
        directory.setNewOrder("https://acme.example/acme/new-order");
        directory.setNewAuthz("https://acme.example/acme/new-authz");
        directory.setRevokeCert("https://acme.example/acme/revoke-cert");
        directory.setKeyChange("https://acme.example/acme/key-change");

        DirectoryMeta meta = new DirectoryMeta();
        meta.setTermsOfService("https://acme.example/terms");
        meta.setWebsite("https://acme.example");
        meta.setExternalAccountRequired(Boolean.FALSE);
        meta.setCaaIdentities(new String[]{"acme.example"});
        directory.setMeta(meta);

        GoldenJson.assertMatchesGoldenAndRoundTrips("acme-directory", mapper, directory, Directory.class);
    }

    @Test
    void accountKeepsItsWireShape() {
        Account account = new Account();
        account.setStatus(AccountStatus.VALID);
        account.setContact(List.of("mailto:admin@example.com"));
        account.setTermsOfServiceAgreed(true);
        account.setOrders("https://acme.example/acme/orders/1");

        GoldenJson.assertMatchesGoldenAndRoundTrips("acme-account", mapper, account, Account.class);
    }

    @Test
    void orderKeepsItsWireShape() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.setExpires("2026-01-15T09:30:00Z");
        order.setIdentifiers(List.of(identifier()));
        order.setNotBefore("2026-01-15T00:00:00Z");
        order.setNotAfter("2026-04-15T00:00:00Z");
        order.setAuthorizations(List.of("https://acme.example/acme/authz/1"));
        order.setFinalize("https://acme.example/acme/order/1/finalize");
        order.setCertificate("https://acme.example/acme/cert/1");

        GoldenJson.assertMatchesGoldenAndRoundTrips("acme-order", mapper, order, Order.class);
    }

    @Test
    void authorizationKeepsItsWireShape() {
        Authorization authorization = new Authorization();
        authorization.setIdentifier(identifier());
        authorization.setStatus(AuthorizationStatus.PENDING);
        authorization.setExpires("2026-01-15T09:30:00Z");
        authorization.setChallenges(List.of(challenge()));
        authorization.setWildcard(Boolean.FALSE);

        GoldenJson.assertMatchesGoldenAndRoundTrips("acme-authorization", mapper, authorization, Authorization.class);
    }

    @Test
    void challengeKeepsItsWireShape() {
        GoldenJson.assertMatchesGoldenAndRoundTrips("acme-challenge", mapper, challenge(), Challenge.class);
    }

    /**
     * The problem document is the ACME error channel, and its {@code type} field is the URN clients branch on to
     * decide whether to retry, back off, or fail permanently. It is also the surface where a runtime detail could
     * leak outward, so its shape is worth holding still on both counts.
     */
    @Test
    void problemDocumentKeepsItsWireShapeIncludingNestedSubproblems() {
        ProblemDocument problem = new ProblemDocument(Problem.MALFORMED);
        problem.setInstance("https://acme.example/acme/order/1");
        problem.setSubproblems(List.of(new ProblemDocument(
                "urn:ietf:params:acme:error:rejectedIdentifier",
                "Rejected Identifier",
                "The identifier is not accepted by this ACME profile")));

        GoldenJson.assertMatchesGoldenAndRoundTrips("acme-problem-document", mapper, problem, ProblemDocument.class);

        JsonNode tree = mapper.valueToTree(problem);
        assertThat(tree.path("type").asText())
                .describedAs("ACME clients branch on the problem type URN; it must keep both its key and its value")
                .startsWith("urn:ietf:params:acme:error:");
    }

    /**
     * The one place the bare {@code AcmeJsonProcessor} mapper is genuinely used: parsing the outer JWS envelope of
     * an inbound ACME request, through {@code generalBodyJsonParser(request, JwsBody.class)} in
     * {@code AcmeJwsRequest}. Nothing else in the codebase touches that mapper.
     * <p>
     * Pinning it separately keeps the two ACME paths honest, and records two behaviours that differ from the wire
     * mapper. It applies no {@code NON_NULL} inclusion, so an all-null object still renders its keys. And it leaves
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} at Jackson's default, so an inbound document carrying a member the DTO does
     * not declare is <b>rejected</b> rather than ignored.
     * <p>
     * That strictness is worth having written down before the upgrade. It is a live interoperability constraint —
     * an ACME client that adds a member to a payload gets a parse failure — and {@code FAIL_ON_UNKNOWN_PROPERTIES}
     * is precisely the kind of default a major version revisits. Whichever way it moves, that is a behavioural
     * change on an externally-facing parser, and this test is what would surface it.
     */
    @Test
    void inboundJwsEnvelopeMapperKeepsItsDefaultsAndRejectsUnknownMembers() {
        ObjectMapper envelopeMapper = GoldenMappers.acmeJwsEnvelope();

        assertThat(envelopeMapper.valueToTree(new DirectoryMeta()).isEmpty())
                .describedAs("the envelope mapper applies no NON_NULL inclusion, so an all-null object still renders "
                        + "its keys — the opposite of the wire mapper below")
                .isFalse();
        assertThat(GoldenMappers.web().valueToTree(new DirectoryMeta()).isEmpty())
                .describedAs("the wire mapper omits null fields entirely; the two genuinely differ, which is why the "
                        + "protocol documents above are baselined against the wire mapper and this one is not")
                .isTrue();

        assertThatExceptionOfType(UnrecognizedPropertyException.class)
                .describedAs("the inbound envelope parser is strict: an undeclared member is rejected, not ignored")
                .isThrownBy(() -> envelopeMapper.readValue(
                        "{\"type\":\"dns\",\"value\":\"host.example.com\",\"unknownMember\":\"x\"}",
                        Identifier.class));
    }

    private static Identifier identifier() {
        Identifier identifier = new Identifier();
        identifier.setType("dns");
        identifier.setValue("host.example.com");
        return identifier;
    }

    private static Challenge challenge() {
        Challenge challenge = new Challenge();
        challenge.setType(ChallengeType.HTTP01);
        challenge.setUrl("https://acme.example/acme/challenge/1");
        challenge.setStatus(ChallengeStatus.PENDING);
        challenge.setValidated("2026-01-15T09:30:00Z");
        challenge.setToken("evaGxfADs6pSRb2LAv9IZf17Dt3juxGJ-PCt92wr-oA");
        return challenge;
    }
}
