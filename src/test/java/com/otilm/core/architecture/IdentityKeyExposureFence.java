package com.otilm.core.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
     * The stored-value vocabulary: every spelling the code base can produce for a value that lives in a column.
     *
     * <p>
     * {@code identity_key} (SQL), {@code identityKey} (Java member), {@code IDENTITY_KEY} (constant),
     * {@code getIdentityKey} (accessor), {@code identity-key} (a JSON or header name). ASCII-only case folding, so the
     * verdict cannot depend on the platform locale.
     *
     * <p>
     * The alias vocabulary is fenced with it. {@code crypto_asset_alias.absorbed_key} and {@code canonical_key}
     * <em>hold identity-key values</em> — {@code canonical_key} is a foreign key onto {@code crypto_asset.identity_key}
     * — so a DTO exposing {@code canonicalKey}, a {@code FilterField} over {@code absorbedKey}, or a log line binding
     * either would ship exactly the hash whose low-entropy preimage falls to a dictionary attack, while passing a fence
     * that only knew the word "identity".
     */
    private static final String STORED_VALUE_VOCABULARY = "identity[_\\-\\s]?key|absorbed[_\\-\\s]?key"
            + "|canonical[_\\-\\s]?key";

    /**
     * The pre-image vocabulary: the spellings for the material itself, ahead of the key it hashes to.
     *
     * <p>
     * The key is one dictionary attack away from the material; the pre-image <em>is</em> the material, spelled out.
     * {@code keyedPayload} is here for the same reason: since core#2165 it is the node the material pre-image is built
     * from, and it deliberately keeps a producer's uncontracted members — which can be an inlined plaintext — because
     * R2 and R15 name the five reference fields as the whole of what may be stripped before a hash. The stored payload
     * is the one that drops them, so {@code storedPayload} is <b>not</b> fenced: naming it is the correct choice, and
     * fencing the safe spelling would train readers to reach for the unsafe one.
     *
     * <p>
     * {@code (?!slot)} excludes {@code PreImageSlot}, the type that renders a slot, from roughly forty call sites
     * across this package. The type name is not the value, and without the lookahead the fence would flag every one of
     * them and be turned off.
     *
     * <p>
     * The residual is stated rather than closed: a member called {@code key} or {@code value} alone still carries the
     * same content past a lexical rule, and fencing those spellings would flag every public key in the code base.
     */
    private static final String PRE_IMAGE_VOCABULARY = "pre[_\\-\\s]?image(?!slot)|keyed[_\\-\\s]?payload";

    /**
     * Every fenced spelling, composed from the two vocabularies so they cannot drift apart.
     *
     * <p>
     * A file's allowlist entry names the vocabulary it may use, and this pattern is what decides whether anything
     * <em>else</em> is left on the line after that vocabulary is discounted — see {@link #sourceFileViolations}.
     */
    private static final Pattern IDENTITY_KEY = Pattern
            .compile(STORED_VALUE_VOCABULARY + "|" + PRE_IMAGE_VOCABULARY, Pattern.CASE_INSENSITIVE);

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
     * The production sources allowed to name a fenced value, each mapped to the <em>one vocabulary</em> it may name.
     *
     * <p>
     * <b>Scoped, not per file.</b> A bare path exemption is broader than any reason for granting it: allowlisting the
     * identity calculator so it may name the pre-image it builds would also let it name the {@code identity_key} it
     * produces, which is the single worst place in the code base to open that hole. This class said so before the entry
     * existed -- "it would have covered a future code-level mention in the one file best placed to leak the value it
     * computes" -- and the first attempt at core#2165 item 20 granted exactly that exemption anyway. A mention is
     * exempt only when the line names nothing outside its file's own vocabulary.
     *
     * <p>
     * Persistence has to name the stored value: the column, its query, its writer and the translator that recognises
     * its unique constraint by name. The identity layer has to name the pre-image: the record that carries it, and the
     * two classes that build and hash the keyed payload. Everything else naming either is a leak.
     *
     * <p>
     * The exemption covers <em>naming</em> only. The logging rule carries no allowlist, so none of these files may put
     * a fenced value in a log line.
     */
    static final Map<String, Pattern> SOURCE_ALLOWLIST = Map
            .ofEntries(storedValue("src/main/java/com/otilm/core/dao/entity/cbom/CryptoAsset.java"),
                    storedValue("src/main/java/com/otilm/core/dao/entity/cbom/CryptoAssetAlias.java"),
                    storedValue("src/main/java/com/otilm/core/dao/repository/cbom/CryptoAssetRepository.java"),
                    storedValue("src/main/java/com/otilm/core/dao/repository/cbom/CryptoAssetAliasRepository.java"),
                    storedValue("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetWriter.java"),
                    storedValue("src/main/java/com/otilm/core/service/writer/cbom/CryptoAssetAliasWriter.java"),
                    storedValue("src/main/java/com/otilm/core/dao/CryptoAssetConstraintTranslator.java"),
                    preImage("src/main/java/com/otilm/core/cbom/asset/identity/CryptoAssetIdentity.java"),
                    preImage("src/main/java/com/otilm/core/cbom/asset/identity/MaterialRedaction.java"),
                    preImage("src/main/java/com/otilm/core/cbom/asset/identity/AssetNormalizer.java"));

    private static Map.Entry<String, Pattern> storedValue(String path) {
        return Map.entry(path, Pattern.compile(STORED_VALUE_VOCABULARY, Pattern.CASE_INSENSITIVE));
    }

    private static Map.Entry<String, Pattern> preImage(String path) {
        return Map.entry(path, Pattern.compile(PRE_IMAGE_VOCABULARY, Pattern.CASE_INSENSITIVE));
    }

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

    /**
     * Whether an allowlisted file's line names only the vocabulary that file is entitled to.
     *
     * <p>
     * The file's own vocabulary is discounted and the line re-tested, so a persistence source may say
     * {@code identityKey} and an identity source may say {@code preImage}, while either saying the other's is a
     * violation on the spot. That is what makes the entry an exemption for a reason rather than for a file.
     */
    private static boolean namesNothingBeyondItsVocabulary(Pattern exempt, String line) {
        return exempt != null && !mentionsIdentityKey(exempt.matcher(line).replaceAll(""));
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
        Pattern exempt = SOURCE_ALLOWLIST.get(path);
        List<String> violations = new ArrayList<>();
        int openLoggingParens = 0;
        boolean inTextBlock = false;
        boolean inBlockComment = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean documentation = isDocumentation(line, inBlockComment);
            inBlockComment = blockCommentStateAfter(line, inBlockComment);
            if (documentation) {
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
                } else if (!namesNothingBeyondItsVocabulary(exempt, line)) {
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
     *
     * <p>
     * Whether a line continues a block comment is a fact about the lines before it, not about its own first character.
     * Reading a leading {@code *} as documentation exempted a legal continuation line whose first token is the
     * multiplication operator -- and this codebase's formatter puts operators at line starts, so
     * {@code * scale(identityKey));} as the argument line of a wrapped logging call scored nothing at all. That is the
     * dual of the leading-block-comment bypass, one token over, so the fix is the one that closes the family: carry the
     * state rather than infer it, as the text-block tracking above already does.
     */
    static boolean isDocumentation(String line, boolean inBlockComment) {
        String trimmed = line.strip();
        if (inBlockComment) {
            return closesWithNothingAfterIt(trimmed);
        }
        if (trimmed.startsWith("//")) {
            return true;
        }
        // A line opening a block comment is documentation only if nothing follows the comment's close. Treating any
        // /*-starting line as a comment exempted the whole line, so `/* re-keyed */ asset.getIdentityKey());` -- a
        // legal argument line of a wrapped logging call -- was reported as nothing at all.
        return trimmed.startsWith("/*") && closesWithNothingAfterIt(trimmed);
    }

    private static boolean closesWithNothingAfterIt(String trimmed) {
        int close = trimmed.indexOf("*/");
        return close < 0 || trimmed.substring(close + 2).isBlank();
    }

    /**
     * Whether a block comment is open at the end of this line, given whether one was open at its start.
     *
     * <p>
     * String literals are blanked first, so a literal containing the delimiters cannot open a comment that swallows the
     * lines after it. That direction matters more than the other: a falsely opened comment exempts what follows, and an
     * exemption is the one way this fence can fail without saying so.
     */
    static boolean blockCommentStateAfter(String line, boolean inBlockComment) {
        String code = LITERAL.matcher(line).replaceAll("\"\"");
        boolean open = inBlockComment;
        for (int i = 0; i < code.length() - 1; i++) {
            if (!open && code.startsWith("/*", i)) {
                open = true;
                i++;
            } else if (open && code.startsWith("*/", i)) {
                open = false;
                i++;
            }
        }
        return open;
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
