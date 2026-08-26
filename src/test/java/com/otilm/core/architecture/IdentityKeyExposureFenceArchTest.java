package com.otilm.core.architecture;

import com.otilm.core.architecture.IdentityKeyExposureFence.MemberRef;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails the build if {@code crypto_asset.identity_key} can reach a client, a log or the search allowlist.
 *
 * <p>
 * The key is a hash over a low-entropy preimage — algorithm family, parameter set, curve. Handed the key, an attacker
 * recovers the material by dictionary attack, which is why the redaction ruling in core#2070 rests on the value never
 * leaving the database and on nothing else. Three rules, one kernel:
 *
 * <ol>
 * <li>no member naming the key may be declared in a client-facing package, the imported contract artifact
 * included;</li>
 * <li>{@code FilterField} — the search allowlist — must not name it;</li>
 * <li>no production source may name it outside persistence, and none may log it, not even the persistence sources that
 * legitimately hold it.</li>
 * </ol>
 *
 * <p>
 * The decision procedure lives in {@link IdentityKeyExposureFence}, so {@link IdentityKeyExposureFenceSelfTest} can
 * feed it planted leaks and prove this fence is able to fail. A fence that has never been seen to fail is not evidence.
 */
@AnalyzeClasses(packages = {"com.otilm.core", "com.otilm.api"})
class IdentityKeyExposureFenceArchTest {

    private static final Path PRODUCTION_SOURCE_ROOT = Path.of("src/main/java");

    private static final Path SEARCH_ALLOWLIST = Path.of("src/main/java/com/otilm/core/enums/FilterField.java");

    @ArchTest
    static void noClientFacingDeclarationNamesTheIdentityKey(JavaClasses classes) {
        List<MemberRef> members = new ArrayList<>();
        for (JavaClass clazz : classes) {
            for (JavaField field : clazz.getFields()) {
                members.add(new MemberRef(clazz.getName(), clazz.getPackageName(), "field", field.getName()));
            }
            for (JavaMethod method : clazz.getMethods()) {
                members.add(new MemberRef(clazz.getName(), clazz.getPackageName(), "method", method.getName()));
            }
        }

        assertThat(IdentityKeyExposureFence.declaredMemberViolations(members))
                .describedAs("The crypto-asset identity key must not be declared in a model or API package: given the "
                        + "key, its low-entropy preimage falls to a dictionary attack. Keep it on the entity and its "
                        + "persistence path only.")
                .isEmpty();
    }

    @Test
    void theSearchAllowlistDoesNotOfferTheIdentityKey() throws IOException {
        assertThat(IdentityKeyExposureFence
                .sourceFileViolations(SEARCH_ALLOWLIST, Files.readAllLines(SEARCH_ALLOWLIST, StandardCharsets.UTF_8)))
                .describedAs("FilterField is the search allowlist: an entry for the identity key would let a client "
                        + "confirm a guessed key one request at a time.")
                .isEmpty();
    }

    /**
     * An allowlist entry that names no existing file is a hole that looks like a fence: it silently stops exempting
     * anything, and — worse — hides that the source it meant to cover was moved or renamed out from under it. The
     * allowlist is a path list, so nothing but this check can tell the two apart.
     */
    @Test
    void everyAllowlistedSourceExists() {
        assertThat(IdentityKeyExposureFence.SOURCE_ALLOWLIST)
                .describedAs("each allowlisted persistence source must still be where the allowlist says it is")
                .allSatisfy(path -> assertThat(Path.of(path)).exists());
    }

    @Test
    void noProductionSourceNamesOrLogsTheIdentityKeyOutsidePersistence() throws IOException {
        assertThat(IdentityKeyExposureFence.sourceTreeViolations(PRODUCTION_SOURCE_ROOT))
                .describedAs("The identity key may be named only by the persistence sources that must, and may be "
                        + "logged by none of them: a bound parameter in a log line is the same disclosure as a "
                        + "response field.")
                .isEmpty();
    }
}
