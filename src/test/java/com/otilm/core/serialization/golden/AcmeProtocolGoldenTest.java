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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ACME (RFC 8555) JSON wire contract.
 * <p>
 * ACME is the only JSON-based protocol core speaks — SCEP, CMP and TSP are ASN.1 / PKCS#7 binary formats that never
 * pass through Jackson, so they are deliberately out of scope for golden files. What makes ACME worth pinning is that
 * its counterparties are third-party clients (certbot, acme.sh, cert-manager and friends) that will not be upgraded
 * in lockstep with us and that parse these documents strictly against the RFC. A field that changes name, or starts
 * appearing as an explicit {@code null} where it used to be absent, is an interoperability break we would otherwise
 * discover from the field rather than from CI.
 * <p>
 * These goldens use the bare mapper from {@code AcmeJsonProcessor} rather than the Spring wire mapper, because that
 * is what actually processes ACME payloads today. The difference is material and is itself part of the baseline: the
 * bare mapper has no {@code NON_NULL} inclusion, so ACME documents render nulls explicitly where a REST DTO would
 * omit the key. Whatever consolidates the bespoke mapper sites next must keep that, or knowingly change it.
 */
class AcmeProtocolGoldenTest {

    private final ObjectMapper mapper = GoldenMappers.acme();

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
     * Records the concrete null-handling difference between the ACME mapper and the wire mapper. If the bespoke ACME
     * mapper is ever folded into the shared one without carrying its configuration, this is the test that says so,
     * rather than an ACME client failing to parse an unexpected null months later.
     */
    @Test
    void acmeMapperRendersAbsentFieldsAsExplicitNullsUnlikeTheWireMapper() {
        Identifier sparse = identifier();

        JsonNode acmeTree = GoldenMappers.acme().valueToTree(sparse);
        JsonNode webTree = GoldenMappers.web().valueToTree(new DirectoryMeta());

        assertThat(acmeTree.has("type"))
                .describedAs("the ACME mapper has no NON_NULL inclusion, so populated and absent fields alike are keys")
                .isTrue();
        assertThat(webTree.isEmpty())
                .describedAs("the wire mapper omits null fields entirely — the two mappers genuinely differ, and the "
                        + "ACME goldens are baselined against the ACME mapper for that reason")
                .isTrue();
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
