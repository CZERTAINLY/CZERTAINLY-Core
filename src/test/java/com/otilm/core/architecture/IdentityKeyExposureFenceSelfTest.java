package com.otilm.core.architecture;

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

    @Test
    void unrelatedNamesAreNotRecognised() {
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("keyIdentity")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("identity")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey("publicKey")).isFalse();
        assertThat(IdentityKeyExposureFence.mentionsIdentityKey(null)).isFalse();
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
    void aWrappedLoggerCallIsAlsoReported() {
        Path allowlisted = Path.of("src/main/java/com/otilm/core/dao/entity/cbom/CryptoAsset.java");

        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(allowlisted,
                        List.of("  logger.getLogger().warn(\"identity_key={}\", this.identityKey);")))
                .hasSize(1);
    }
}
