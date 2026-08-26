package com.otilm.core.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The rule kernel behind the crypto-asset identity-key redaction fence.
 *
 * <p>
 * {@code crypto_asset.identity_key} is a hash over a low-entropy preimage: handed the key, an attacker recovers the
 * material by dictionary attack. The redaction ruling in core#2070 rests on the key never leaving the database, and on
 * nothing else. This class holds the decision procedure; {@link IdentityKeyExposureFenceArchTest} feeds it the real
 * code base and {@link IdentityKeyExposureFenceSelfTest} feeds it planted leaks, so the fence is proven able to fail.
 *
 * <p>
 * Every method is static and pure over its arguments — no classpath scanning, no file reads outside the explicit
 * {@link #sourceFileViolations(Path, List)} entry point — which is what lets the self-test exercise the same code the
 * real scan runs.
 */
final class IdentityKeyExposureFence {

    /**
     * Matches the identity key under every spelling the code base can produce: {@code identity_key} (SQL),
     * {@code identityKey} (Java member), {@code IDENTITY_KEY} (constant), {@code getIdentityKey} (accessor),
     * {@code identity-key} (a JSON or header name). ASCII-only case folding, so the verdict cannot depend on the
     * platform locale.
     */
    private static final Pattern IDENTITY_KEY = Pattern.compile("identity[_\\-\\s]?key", Pattern.CASE_INSENSITIVE);

    /**
     * Any method call named after a log level. Deliberately loose — it matches {@code log.debug(}, {@code logger.warn(}
     * and the wrapped {@code logger.getLogger().debug(} alike, because the point is to catch the bound value reaching
     * an appender, whatever the logger handle is called.
     */
    private static final Pattern LOGGING_CALL = Pattern
            .compile("\\.\\s*(trace|debug|info|warn|error)\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * Packages whose declarations reach a client: the DTO/model layer, the API layer, and the imported contract
     * artifact. {@code com.otilm.api.model} is scanned too, so a future contract revision cannot land the leak in a
     * published DTO without failing this build.
     */
    private static final List<String> FENCED_PACKAGE_PREFIXES = List
            .of("com.otilm.core.model", "com.otilm.core.api", "com.otilm.api.model");

    /**
     * The only production sources allowed to name the identity key. Persistence has to: the column, its query, its
     * writer, the calculator that produces the value and the translator that recognises its unique constraint by name.
     * Everything else naming it is a leak.
     */
    static final Set<String> SOURCE_ALLOWLIST = Set
            .of("src/main/java/com/otilm/core/cbom/asset/CryptoAssetIdentityCalculator.java",
                    "src/main/java/com/otilm/core/dao/entity/cbom/CryptoAsset.java",
                    "src/main/java/com/otilm/core/dao/entity/cbom/CryptoAssetAlias.java",
                    "src/main/java/com/otilm/core/dao/repository/cbom/CryptoAssetRepository.java",
                    "src/main/java/com/otilm/core/dao/repository/cbom/CryptoAssetAliasRepository.java",
                    "src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java",
                    "src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetAliasWriter.java",
                    "src/main/java/com/otilm/core/dao/CryptoAssetConstraintTranslator.java");

    private IdentityKeyExposureFence() {
    }

    /** One declared member, reduced to what the fence needs to judge it. */
    record MemberRef(String declaringClass, String packageName, String kind, String name) {

        @Override
        public String toString() {
            return declaringClass + "." + name + " (" + kind + ")";
        }
    }

    static boolean mentionsIdentityKey(String text) {
        return text != null && IDENTITY_KEY.matcher(text).find();
    }

    static boolean isFencedPackage(String packageName) {
        return packageName != null
                && FENCED_PACKAGE_PREFIXES.stream().anyMatch(prefix -> isWithinPackage(packageName, prefix));
    }

    private static boolean isWithinPackage(String packageName, String prefix) {
        return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
    }

    /**
     * Members declared in a fenced package whose name names the identity key. A getter is as much a leak as the field:
     * Jackson serialises from the getter.
     */
    static List<String> declaredMemberViolations(Collection<MemberRef> members) {
        return members
                .stream()
                .filter(member -> isFencedPackage(member.packageName()))
                .filter(member -> mentionsIdentityKey(member.name()))
                .map(member -> member + " declares the crypto-asset identity key in a client-facing package")
                .toList();
    }

    /**
     * Violations in one production source file: the identity key named at all outside {@link #SOURCE_ALLOWLIST}, or
     * named on a logging call in any file at all. The logging rule carries no allowlist — the files that legitimately
     * hold the value are exactly the files from which it could reach an appender.
     *
     * @param repoRelativePath the file's path relative to the repository root, with {@code /} separators
     * @param lines the file's lines, 1-based in the reported message
     */
    static List<String> sourceFileViolations(Path repoRelativePath, List<String> lines) {
        String path = repoRelativePath.toString().replace('\\', '/');
        boolean allowlisted = SOURCE_ALLOWLIST.contains(path);
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isDocumentation(line) || !mentionsIdentityKey(line)) {
                continue;
            }
            if (LOGGING_CALL.matcher(line).find()) {
                violations.add(path + ":" + (i + 1) + " logs the crypto-asset identity key: " + line.strip());
            } else if (!allowlisted) {
                violations
                        .add(path + ":" + (i + 1) + " names the crypto-asset identity key outside persistence: "
                                + line.strip());
            }
        }
        return violations;
    }

    /**
     * Whether the line is documentation rather than code. A comment cannot disclose a value, and the reason the key is
     * fenced is exactly what a comment must be free to explain -- a rule that forbade naming it in prose would forbid
     * documenting the rule. Only whole comment lines are exempt: a trailing comment shares its line with code, and a
     * string literal containing a double slash must not be able to hide the rest of its line.
     */
    static boolean isDocumentation(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    /**
     * Every violation across a production source tree. {@code sourceRoot} is resolved against the working directory,
     * which Maven sets to the module root, so the walked paths are already the repository-relative form the allowlist
     * is written in.
     */
    static List<String> sourceTreeViolations(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalStateException("Production source root not found: " + sourceRoot.toAbsolutePath());
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                violations.addAll(sourceFileViolations(file, Files.readAllLines(file, StandardCharsets.UTF_8)));
            }
        }
        return violations;
    }
}
