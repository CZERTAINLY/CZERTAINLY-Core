package com.otilm.core.architecture;

import com.otilm.core.architecture.IdentityKeyExposureFence.AccessorCall;
import com.otilm.core.architecture.IdentityKeyExposureFence.MemberRef;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The planted-leak control for {@link IdentityKeyExposureFenceArchTest}.
 *
 * <p>
 * A green fence proves nothing unless the fence can go red. Each test here plants a leak the real scan would find and
 * asserts the kernel reports it, then plants the nearest legitimate neighbour and asserts the kernel stays silent — so
 * the fence is shown to discriminate, not merely to accept.
 *
 * <p>
 * The leaks are synthetic inputs to the kernel rather than real classes or files: a genuinely leaking DTO committed to
 * a fenced package would fail the real scan for everyone, which is not a control but a broken build.
 */
class IdentityKeyExposureFenceSelfTest {

    @Test
    void everySpellingOfTheIdentityKeyIsRecognised() {
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("identityKey")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("identity_key")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("IDENTITY_KEY")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("getIdentityKey")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("identity-key")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("uq_crypto_asset_identity_key")).isTrue();
    }

    /**
     * The pre-image vocabulary, which is fenced ahead of the key because the pre-image is the material itself.
     *
     * <p>
     * {@code keyedPayload} is in it for the same reason: it is the node the material pre-image is built from and it
     * keeps a producer's uncontracted members, which can be an inlined plaintext.
     */
    @Test
    void everySpellingOfThePreImageIsRecognised() {
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("preImage")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("pre_image")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("PRE_IMAGE")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("pre-image")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("getPreImage")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("dnPreImage")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("keyedPayload")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("keyed_payload")).isTrue();
    }

    @Test
    void unrelatedNamesAreNotRecognised() {
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("keyIdentity")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("identity")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("publicKey")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey(null)).isFalse();
    }

    /**
     * The two spellings the fence must <em>not</em> match, and the reason each exclusion is load-bearing.
     *
     * <p>
     * {@code PreImageSlot} is the type that renders a slot, named at roughly forty call sites: without the
     * {@code (?!slot)} lookahead the fence flags every one of them and gets turned off. {@code storedPayload} is the
     * payload that drops uncontracted members, so naming it is the correct choice — fencing the safe spelling would
     * train a reader to reach for the unsafe one.
     */
    @Test
    void theTypeNameAndTheSafePayloadAreNotTheValue() {
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("PreImageSlot")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("PreImageSlot.of(kind)")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("storedPayload")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("redaction.storedPayload()")).isFalse();
    }

    /**
     * An allowlist entry exempts one vocabulary, not one file.
     *
     * <p>
     * This is the hole the first attempt at core#2165 item 20 opened: allowlisting the identity calculator so it may
     * name the pre-image it builds also exempted the {@code identity_key} it produces, in the one file best placed to
     * leak that value. A persistence source naming a pre-image fails the same way in the other direction.
     */
    @Test
    void anAllowlistedFileMayNameOnlyItsOwnVocabulary() {
        Path calculator = Path.of("src/main/java/com/otilm/core/cbom/asset/identity/CryptoAssetIdentity.java");
        Path entity = Path.of("src/main/java/com/otilm/core/dao/entity/cbom/CryptoAsset.java");

        assertThat(IdentityKeyExposureFence.sourceFileViolations(calculator, List.of("String preImage = built[0];")))
                .describedAs("the identity layer builds the pre-image")
                .isEmpty();
        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(calculator, List.of("String identityKey = digest(preImage);")))
                .describedAs("and must not name the key it produces")
                .hasSize(1);
        assertThat(IdentityKeyExposureFence.sourceFileViolations(entity, List.of("private String identityKey;")))
                .describedAs("persistence holds the stored value")
                .isEmpty();
        assertThat(IdentityKeyExposureFence.sourceFileViolations(entity, List.of("private String preImage;")))
                .describedAs("and has no business holding the material")
                .hasSize(1);
    }

    /** The logging rule carries no allowlist, so an allowlisted file cannot log what it may legitimately name. */
    @Test
    void anAllowlistedFileStillMayNotLogItsOwnVocabulary() {
        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(
                        Path.of("src/main/java/com/otilm/core/cbom/asset/identity/MaterialRedaction.java"),
                        List.of("log.debug(\"keyed payload {}\", keyedPayload);")))
                .describedAs("naming it is allowed here; logging it is allowed nowhere")
                .hasSize(1);
    }

    @Test
    void aPlantedDtoFieldIsReported() {
        MemberRef planted = new MemberRef("com.otilm.core.model.cbom.CryptoAssetDto", "com.otilm.core.model.cbom",
                "field", "identityKey");

        assertThat(IdentityKeyExposureFence.declaredMemberViolations(List.of(planted)))
                .singleElement()
                .asString()
                .contains("CryptoAssetDto.identityKey");
    }

    @Test
    void aPlantedGetterInTheContractArtifactIsReported() {
        MemberRef planted = new MemberRef("com.otilm.api.model.core.cbom.CbomAssetDto", "com.otilm.api.model.core.cbom",
                "method", "getIdentityKey");

        assertThat(IdentityKeyExposureFence.declaredMemberViolations(List.of(planted))).hasSize(1);
    }

    @Test
    void theEntityMayDeclareTheIdentityKey() {
        MemberRef allowed = new MemberRef("com.otilm.core.dao.entity.cbom.CryptoAsset",
                "com.otilm.core.dao.entity.cbom", "field", "identityKey");

        assertThat(IdentityKeyExposureFence.declaredMemberViolations(List.of(allowed))).isEmpty();
    }

    @Test
    void aPlantedSearchAllowlistEntryIsReported() {
        Path searchAllowlist = Path.of("src/main/java/com/otilm/core/enums/FilterField.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(searchAllowlist, List
                        .of("    CRYPTO_ASSET_IDENTITY_KEY(Resource.CBOM, null, null, CryptoAsset_.identityKey,",
                                "            \"Identity\", SearchFieldTypeEnum.STRING),")))
                .hasSize(1);
    }

    @Test
    void aPlantedMentionInAnUnlistedSourceFileIsReported() {
        Path unlisted = Path.of("src/main/java/com/otilm/core/service/impl/CryptoAssetServiceImpl.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(unlisted, List.of("class X {", "  String identityKey = asset.key();", "}")))
                .singleElement()
                .asString()
                .contains("CryptoAssetServiceImpl.java:2")
                .contains("names the crypto-asset identity key outside persistence");
    }

    @Test
    void anAllowlistedSourceFileMayNameTheIdentityKeyButMayNotLogIt() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted, List.of("  repository.upsertIdentity(identityKey);"))).isEmpty();

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted, List.of("  logger.debug(\"keyed as {}\", identityKey);")))
                .singleElement()
                .asString()
                .contains("logs the crypto-asset identity key");
    }

    @Test
    void documentationMayExplainTheRuleButCodeMayNotNameTheKey() {
        Path unlisted = Path.of("src/main/java/com/otilm/core/enums/FilterField.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(unlisted,
                        List
                                .of("// No entry for identity_key, and there must never be one.", "/**",
                                        " * The identity key never leaves the database.", " */",
                                        "/* identityKey is fenced. */")))
                .describedAs("a comment cannot disclose a value, and the reason for the fence must be documentable; "
                        + "the block comment is opened rather than fed as a bare continuation, because a leading "
                        + "asterisk is only documentation when a block comment is actually open")
                .isEmpty();

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(unlisted, List.of("    private String identityKey; // the fenced column")))
                .describedAs("a trailing comment shares its line with code")
                .hasSize(1);
    }

    @Test
    void aWrappedLoggerCallIsAlsoReported() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/dao/entity/cbom/CryptoAsset.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List.of("  logger.getLogger().warn(\"identity_key={}\", this.identityKey);")))
                .hasSize(1);
    }

    /**
     * The leak a line-by-line rule cannot see: the logging call opens on one line and binds the key on another, so
     * neither line carries both halves. Reformatting must not decide whether a disclosure is reported.
     */
    @Test
    void aLoggingCallSplitAcrossLinesIsReported() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List
                                .of("  logger.debug(", "          \"keyed as {} under rule set {}\",",
                                        "          identityKey,", "          rulesetVersion);")))
                .singleElement()
                .asString()
                .contains("CryptoAssetWriter.java:3")
                .contains("logs the crypto-asset identity key");
    }

    /**
     * The converse: once a logging call has closed, an allowlisted file may go on naming the key. Without this the
     * multiline rule would swallow the rest of every file that ever logs.
     */
    @Test
    void aClosedLoggingCallDoesNotFenceTheLinesAfterIt() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List
                                .of("  logger.debug(", "          \"upserting {} assets\",", "          count);",
                                        "  repository.upsertIdentity(identityKey);")))
                .isEmpty();
    }

    /**
     * A parenthesis inside the message template must not close the call early — otherwise a leak on the next line
     * escapes by way of the punctuation in a log message.
     */
    @Test
    void aParenthesisInsideAMessageTemplateDoesNotCloseTheCall() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List.of("  logger.warn(\"merge (re-keyed) produced {}\",", "          identityKey);")))
                .singleElement()
                .asString()
                .contains("CryptoAssetWriter.java:2");
    }

    /**
     * Ordinary parenthesised code between two statements is not an open logging call. Without this the depth counter
     * would drift and fence a whole file after its first {@code if}.
     */
    @Test
    void ordinaryParenthesesAreNotMistakenForALoggingCall() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List.of("  if (asset.isPresent() && (count > 0)) {", "      apply(identityKey);", "  }")))
                .isEmpty();
    }

    /**
     * The alias table holds identity-key values -- {@code canonical_key} is a foreign key onto
     * {@code crypto_asset.identity_key} -- so the alias vocabulary is inside the fence, not beside it.
     */
    @Test
    void theAliasKeySpellingsAreRecognised() {
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("absorbedKey")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("canonical_key")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("getCanonicalKey")).isTrue();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("CANONICAL_KEY")).isTrue();
    }

    @Test
    void aPlantedAliasKeyGetterInAClientFacingPackageIsReported() {
        MemberRef planted = new MemberRef("com.otilm.core.model.cbom.CryptoAssetAliasDto", "com.otilm.core.model.cbom",
                "method", "getCanonicalKey");

        assertThat(IdentityKeyExposureFence.declaredMemberViolations(List.of(planted))).hasSize(1);
    }

    /**
     * The dual of the leading-block-comment bypass. A continuation line whose first token is the multiplication
     * operator is not documentation, and this codebase's formatter is what puts it there.
     */
    @Test
    void aLeadingOperatorIsNotAJavadocContinuation() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List.of("  logger.debug(\"weight {}\", base", "          * scale(identityKey));")))
                .singleElement()
                .asString()
                .contains("CryptoAssetWriter.java:2");
    }

    @Test
    void aRealJavadocContinuationIsStillDocumentation() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List.of("  /**", "   * The identityKey column is set here.", "   */")))
                .describedAs("a continuation line inside an open block comment is documentation, as before")
                .isEmpty();
    }

    /**
     * A literal carrying the delimiters must not open a comment that exempts the lines after it: an exemption is the
     * one way this fence can fail without reporting anything.
     */
    @Test
    void aLiteralHoldingACommentDelimiterDoesNotOpenAComment() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List.of("  String glob = \"/*\";", "  logger.debug(\"keyed as {}\", identityKey);")))
                .singleElement()
                .asString()
                .contains("CryptoAssetWriter.java:2");
    }

    /**
     * A leading block comment used to exempt the whole line, so a commented argument line of a wrapped logging call
     * disclosed the key and reported nothing.
     */
    @Test
    void aLeadingBlockCommentDoesNotExemptTheCodeAfterIt() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted, List
                        .of("  logger.debug(", "          \"keyed as {}\",", "          /* re-keyed */ identityKey);")))
                .singleElement()
                .asString()
                .contains("CryptoAssetWriter.java:3");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted, List.of("  /* the fenced column is set below */")))
                .describedAs("a whole-line block comment is still documentation")
                .isEmpty();
    }

    /**
     * Prose in a text block can carry an unbalanced parenthesis, which used to close the depth counter early and let
     * the binding line through.
     */
    @Test
    void anUnbalancedParenthesisInATextBlockDoesNotCloseTheCall() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List
                                .of("  logger.debug(\"\"\"", "      merge steps: a) re-key b) re-elect",
                                        "      \"\"\", identityKey);")))
                .singleElement()
                .asString()
                .contains("logs the crypto-asset identity key");
    }

    /** An appender is not the only sink: stdout, a stack trace and the audit log disclose just as much. */
    @Test
    void sinksOtherThanTheLoggerAreReported() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/dao/entity/cbom/CryptoAsset.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted, List.of("  System.out.println(identityKey);")))
                .describedAs("stdout")
                .hasSize(1);
        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted, List.of("  auditLog.logEvent(\"keyed\", identityKey);")))
                .describedAs("the platform audit sink")
                .hasSize(1);
    }

    // ---------------------------------------------------------------- key carriers

    private static final String IDENTITY = "com.otilm.core.cbom.asset.identity.CryptoAssetIdentity$Identity";

    private static final String EXTRACTED = "com.otilm.core.cbom.asset.identity.CbomAssetExtractor$ExtractedAsset";

    private static final String EXTRACTOR = "com.otilm.core.cbom.asset.identity.CbomAssetExtractor";

    private static final String CALCULATOR = "com.otilm.core.cbom.asset.identity.CryptoAssetIdentity";

    private static final String WRITER = "com.otilm.core.service.writer.cbom.CryptoAssetWriter";

    private static final String SERVICE = "com.otilm.core.service.impl.CryptoAssetServiceImpl";

    /**
     * The leak the text rules cannot see: {@code Identity.key()} is named so that no regex matches it, and a service
     * reading it puts the value on a line that says nothing a text rule can catch.
     */
    @Test
    void aServiceReadingAKeyCarrierIsReported() {
        assertThat(carrierViolations(SERVICE, IDENTITY, "key"))
                .singleElement()
                .asString()
                .contains("CryptoAssetServiceImpl")
                .contains("CryptoAssetIdentity$Identity.key");
        assertThat(carrierViolations(SERVICE, IDENTITY, "preImage"))
                .describedAs("the pre-image is the worse of the two")
                .hasSize(1);
        assertThat(carrierViolations(SERVICE, EXTRACTED, "identityKey"))
                .describedAs("the extracted record carries the same value on toward persistence")
                .hasSize(1);
    }

    /**
     * A call is exempt for the reason a mention is: the caller's file is allowlisted for what the accessor returns. The
     * same scoping as {@link #anAllowlistedFileMayNameOnlyItsOwnVocabulary}, applied to calls -- the extractor may take
     * the key it hands to persistence and not the material; the identity layer may read the pre-image it builds and not
     * the key it produces.
     */
    @Test
    void aCallerMayReadOnlyTheVocabularyItsFileIsAllowlistedFor() {
        assertThat(carrierViolations(EXTRACTOR, IDENTITY, "key"))
                .describedAs("the extractor hands the key to persistence")
                .isEmpty();
        assertThat(carrierViolations(EXTRACTOR, IDENTITY, "preImage"))
                .describedAs("and has no business reading the material")
                .hasSize(1);
        assertThat(carrierViolations(CALCULATOR, IDENTITY, "preImage"))
                .describedAs("the identity layer builds the pre-image")
                .isEmpty();
        assertThat(carrierViolations(CALCULATOR, IDENTITY, "key"))
                .describedAs("and must not read back the key it produces")
                .hasSize(1);
        assertThat(carrierViolations(WRITER, EXTRACTED, "identityKey"))
                .describedAs("persistence stores the value")
                .isEmpty();
        assertThat(carrierViolations(WRITER, IDENTITY, "preImage")).hasSize(1);
    }

    /** A nested or anonymous class lives in its enclosing class's file, and the allowlist is written in files. */
    @Test
    void aNestedClassIsJudgedByTheFileThatEnclosesIt() {
        assertThat(carrierViolations(EXTRACTOR + "$Walk", IDENTITY, "key")).isEmpty();
        assertThat(carrierViolations(SERVICE + "$1", IDENTITY, "key")).hasSize(1);
        assertThat(IdentityKeyExposureFence.sourcePathOf(EXTRACTOR + "$ExtractedAsset"))
                .isEqualTo("src/main/java/com/otilm/core/cbom/asset/identity/CbomAssetExtractor.java");
    }

    /**
     * The rule is about the carrier, not about the word. An unrelated accessor called {@code key} is nobody's business,
     * and the carriers' harmless components -- the chain step, the guard -- are readable from anywhere.
     */
    @Test
    void anAccessorMerelyNamedKeyOnAnotherTypeIsNotACarrier() {
        assertThat(carrierViolations(SERVICE, "com.otilm.core.model.cbom.CipherSuiteDto", "key")).isEmpty();
        assertThat(carrierViolations(SERVICE, IDENTITY, "step")).isEmpty();
        assertThat(carrierViolations(SERVICE, EXTRACTED, "guard")).isEmpty();
    }

    private static List<String> carrierViolations(String caller, String target, String method) {
        return IdentityKeyExposureFence.keyCarrierCallViolations(List.of(new AccessorCall(caller, target, method)));
    }
}
