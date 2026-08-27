package com.otilm.core.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
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
     * Matches an identity-key value under every spelling the code base can produce: {@code identity_key} (SQL),
     * {@code identityKey} (Java member), {@code IDENTITY_KEY} (constant), {@code getIdentityKey} (accessor),
     * {@code identity-key} (a JSON or header name). ASCII-only case folding, so the verdict cannot depend on the
     * platform locale.
     *
     * <p>
     * The alias vocabulary is fenced too. {@code crypto_asset_alias.absorbed_key} and {@code canonical_key} <em>hold
     * identity-key values</em> — {@code canonical_key} is a foreign key onto {@code crypto_asset.identity_key} — so a
     * DTO exposing {@code canonicalKey}, a {@code FilterField} over {@code absorbedKey}, or a log line binding either
     * would ship exactly the hash whose low-entropy preimage falls to a dictionary attack, while passing a fence that
     * only knew the word "identity".
     */
    private static final Pattern IDENTITY_KEY = Pattern
            .compile("identity[_\\-\\s]?key|absorbed[_\\-\\s]?key|canonical[_\\-\\s]?key", Pattern.CASE_INSENSITIVE);

    /**
     * Any method call named after a log level. Deliberately loose — it matches {@code log.debug(}, {@code logger.warn(}
     * and the wrapped {@code logger.getLogger().debug(} alike, because the point is to catch the bound value reaching
     * an appender, whatever the logger handle is called.
     */
    private static final Pattern LOGGING_CALL = Pattern
            .compile("\\.\\s*(trace|debug|info|warn|error|logEvent|log)\\s*\\("
                    + "|\\bSystem\\s*\\.\\s*(out|err)\\s*\\.\\s*(print|println|printf|format)\\s*\\("
                    + "|\\.\\s*printStackTrace\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * A string or character literal, escapes included. Blanked out before parentheses are counted, so a parenthesis
     * inside a message template — {@code log.debug("(")} — cannot skew the depth that decides where a logging call
     * ends. Only the depth count sees the blanked form; the identity-key test still reads the raw line, because a key
     * named inside a string literal is exactly as disclosed as one named outside it.
     */
    private static final Pattern LITERAL = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'");

    /** Text-block delimiter. Checked on the raw line, before literals are blanked, which would eat it. */
    private static final String TEXT_BLOCK = "\"\"\"";

    /**
     * Packages whose declarations reach a client: the DTO/model layer, the API layer, and the imported contract
     * artifact. {@code com.otilm.api.model} is scanned too, so a future contract revision cannot land the leak in a
     * published DTO without failing this build.
     */
    private static final List<String> FENCED_PACKAGE_PREFIXES = List
            .of("com.otilm.core.model", "com.otilm.core.api", "com.otilm.api.model");

    /**
     * The only production sources allowed to name the identity key. Persistence has to: the column, its query, its
     * writer and the translator that recognises its unique constraint by name. Everything else naming it is a leak.
     *
     * <p>
     * The calculator that produces the value is deliberately <em>not</em> here. It names the key only in documentation,
     * which the rule exempts anyway, so the entry granted an exemption nothing used -- and it would have covered a
     * future code-level mention in the one file best placed to leak the value it computes. Re-adding it later is a
     * reviewed one-liner; leaving it in is a hole nobody would notice opening.
     */
    static final Set<String> SOURCE_ALLOWLIST = Set
            .of("src/main/java/com/otilm/core/dao/entity/cbom/CryptoAsset.java",
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
     * named anywhere inside a logging call in any file at all. The logging rule carries no allowlist — the files that
     * legitimately hold the value are exactly the files from which it could reach an appender.
     *
     * <p>
     * A logging call is tracked across lines by parenthesis depth, not matched line by line. A formatter is free to put
     * {@code logger.debug(} on one line and the bound argument on the next, and a rule that only looked at single lines
     * would let that formatting decide whether the leak is reported — which would make the acceptance criterion a
     * property of the code style rather than of the code.
     *
     * @param repoRelativePath the file's path relative to the repository root, with {@code /} separators
     * @param lines the file's lines, 1-based in the reported message
     */
    static List<String> sourceFileViolations(Path repoRelativePath, List<String> lines) {
        String path = repoRelativePath.toString().replace('\\', '/');
        boolean allowlisted = SOURCE_ALLOWLIST.contains(path);
        List<String> violations = new ArrayList<>();
        int openLoggingParens = 0;
        boolean inTextBlock = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isDocumentation(line)) {
                continue;
            }
            // A text block spans lines, so its body is not a literal this line's regex can blank, and its prose can
            // carry an unbalanced ')' -- "steps: a) ... b) ..." -- which would close an open logging call early and
            // let the binding line through. Only the code outside the delimiters counts: the head of the line that
            // opens one (which is where `logger.debug(` sits) and the tail of the line that closes it (which is where
            // the bound argument sits).
            int delimiter = line.indexOf(TEXT_BLOCK);
            String countable;
            if (delimiter < 0) {
                countable = inTextBlock ? "" : line;
            } else if (inTextBlock) {
                inTextBlock = false;
                countable = line.substring(delimiter + TEXT_BLOCK.length());
            } else {
                inTextBlock = true;
                countable = line.substring(0, delimiter);
            }
            String code = LITERAL.matcher(countable).replaceAll("\"\"");
            boolean insideLoggingCall = openLoggingParens > 0 || LOGGING_CALL.matcher(code).find();
            if (mentionsIdentityKey(line)) {
                if (insideLoggingCall) {
                    violations.add(path + ":" + (i + 1) + " logs the crypto-asset identity key: " + line.strip());
                } else if (!allowlisted) {
                    violations
                            .add(path + ":" + (i + 1) + " names the crypto-asset identity key outside persistence: "
                                    + line.strip());
                }
            }
            openLoggingParens = remainingLoggingParens(code, openLoggingParens);
        }
        return violations;
    }

    /**
     * How many parentheses of a logging call are still open at the end of this line, given how many were open at its
     * start. Counting begins at the {@code (} of a logging call and stops when that call closes, so ordinary
     * parenthesised code between two logging statements is never mistaken for an open call.
     *
     * <p>
     * A count that drifts can only drift toward reporting more, never less: an unbalanced line leaves the call open and
     * keeps the following lines under the logging rule, which is the direction a fence should fail in.
     */
    private static int remainingLoggingParens(String code, int carriedDepth) {
        Matcher call = LOGGING_CALL.matcher(code);
        int depth = carriedDepth;
        int callOpensAt = call.find() ? call.end() - 1 : -1;
        for (int i = 0; i < code.length(); i++) {
            if (depth == 0) {
                if (callOpensAt < 0) {
                    return 0;
                }
                if (i < callOpensAt) {
                    continue;
                }
            }
            char character = code.charAt(i);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
            if (depth <= 0) {
                depth = 0;
                callOpensAt = call.find(i + 1) ? call.end() - 1 : -1;
            }
        }
        return depth;
    }

    /**
     * Whether the line is documentation rather than code. A comment cannot disclose a value, and the reason the key is
     * fenced is exactly what a comment must be free to explain -- a rule that forbade naming it in prose would forbid
     * documenting the rule. Only whole comment lines are exempt: a trailing comment shares its line with code, and a
     * string literal containing a double slash must not be able to hide the rest of its line.
     */
    static boolean isDocumentation(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
            return true;
        }
        // A line opening a block comment is documentation only if nothing follows the comment's close. Treating any
        // /*-starting line as a comment exempted the whole line, so `/* re-keyed */ asset.getIdentityKey());` -- a
        // legal argument line of a wrapped logging call -- was reported as nothing at all.
        if (!trimmed.startsWith("/*")) {
            return false;
        }
        int close = trimmed.indexOf("*/");
        return close < 0 || trimmed.substring(close + 2).isBlank();
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
