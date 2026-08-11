package com.otilm.core.serialization.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
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
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pins the ACME (RFC 8555) JSON wire contract — the only JSON protocol core speaks, and one parsed strictly by
 * third-party clients (certbot, acme.sh, cert-manager) that will not upgrade in lockstep with us.
 * <p>
 * These documents go out as {@code ResponseEntity} bodies, so they are serialized by the wire mapper and inherit its
 * {@code NON_NULL} inclusion — not by the bare {@code AcmeJsonProcessor} mapper, which serves only the inbound JWS
 * envelope covered below.
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

    /** The ACME error channel: clients branch on the {@code type} URN to decide whether to retry or fail. */
    @Test
    void problemDocumentKeepsItsWireShapeIncludingNestedSubproblems() {
        ProblemDocument problem = new ProblemDocument(Problem.MALFORMED);
        problem.setInstance("https://acme.example/acme/order/1");
        problem
                .setSubproblems(List
                        .of(new ProblemDocument("urn:ietf:params:acme:error:rejectedIdentifier", "Rejected Identifier",
                                "The identifier is not accepted by this ACME profile")));

        GoldenJson.assertMatchesGoldenAndRoundTrips("acme-problem-document", mapper, problem, ProblemDocument.class);

        JsonNode tree = mapper.valueToTree(problem);
        assertThat(tree.path("type").asText())
                .describedAs("clients branch on the problem type URN")
                .startsWith("urn:ietf:params:acme:error:");
    }

    /**
     * Records two behaviours of the inbound envelope mapper that differ from the wire mapper: no {@code NON_NULL}
     * inclusion, and Jackson's default {@code FAIL_ON_UNKNOWN_PROPERTIES}, which makes an ACME client adding a member
     * to a payload a parse failure. That strictness is a live interoperability constraint on an externally-facing
     * parser, and exactly the kind of default a major version revisits.
     */
    @Test
    void inboundJwsEnvelopeMapperKeepsItsDefaultsAndRejectsUnknownMembers() {
        ObjectMapper envelopeMapper = GoldenMappers.acmeJwsEnvelope();

        assertThat(envelopeMapper.valueToTree(new DirectoryMeta()).isEmpty())
                .describedAs("the envelope mapper applies no NON_NULL inclusion")
                .isFalse();
        assertThat(GoldenMappers.web().valueToTree(new DirectoryMeta()).isEmpty())
                .describedAs("the wire mapper omits null fields; the two genuinely differ")
                .isTrue();

        assertThatExceptionOfType(UnrecognizedPropertyException.class)
                .describedAs("the inbound envelope parser rejects an undeclared member rather than ignoring it")
                .isThrownBy(() -> envelopeMapper
                        .readValue("{\"type\":\"dns\",\"value\":\"host.example.com\",\"unknownMember\":\"x\"}",
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
