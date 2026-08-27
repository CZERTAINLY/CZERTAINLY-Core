package com.otilm.core.cbom.asset.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Walks a CycloneDX document's component tree and extracts every cryptographic asset it carries.
 *
 * <p>
 * Version-agnostic by construction. Nothing here reads {@code specVersion}: 1.6 and 1.7 differ in field <em>names</em>
 * -- {@code curve} became {@code ellipticCurve}, and every reference field was renamed -- and those differences are
 * absorbed by the normalizer reading both spellings and by the hashed projection stripping references before any
 * digest. A walker that branched on the declared version would have to be right about a value producers get wrong: one
 * really does ship {@code specVersion} as the number {@code 999}.
 *
 * <p>
 * <b>Nothing a producer can write makes this throw.</b> A component that cannot be keyed is reported as a skip and the
 * walk continues, because one malformed asset must not cost an operator the other four thousand. The skips are values,
 * not log lines: a document is untrusted input, and a walker that logged what it could not parse would print
 * pre-redaction content into an appender the redaction proof does not cover.
 */
public final class CbomAssetExtractor {

    /**
     * How deep a component tree may nest before the walk stops descending.
     *
     * <p>
     * Set to match the JSON parser's own default nesting limit, and that is the point: the parser refuses a document
     * nested deeper than this before the walker ever sees it, so nothing that parses can be truncated here. A tighter
     * bound would silently drop real assets out of a document the parser accepted, which is data loss dressed as
     * robustness -- measured the hard way, with a bound of 64 that would have discarded everything below it.
     *
     * <p>
     * The walk is iterative regardless, so depth costs heap rather than stack. {@code components} nests arbitrarily in
     * both schema versions, and a recursive descent over a deep document would exhaust the stack and take the whole
     * ingest with it -- a denial of service costing one small file.
     */
    private static final int MAX_DEPTH = 1000;

    private final CryptoAssetIdentity identity;

    public CbomAssetExtractor(CryptoAssetIdentity identity) {
        this.identity = identity;
    }

    /**
     * One asset that was extracted, with everything the persistence layer needs to store it.
     *
     * <p>
     * <b>The raw component is deliberately not here.</b> An earlier shape carried it beside the redacted payload, which
     * quietly undid the one property redaction exists for: the plaintext is dropped before identity, persistence,
     * logging or metrics can observe it, and handing the caller the original node put it back within reach of all four.
     * It cost nothing to notice and everything to miss -- a record's generated {@code toString} alone would have
     * printed inlined key material into any log that reported an extraction.
     *
     * <p>
     * {@code retainedProperties} is the redacted payload, which is what {@code crypto_asset_source} stores.
     */
    public record ExtractedAsset(String identityKey, String chainStep, NormalizedAsset normalized, String componentName,
            JsonNode retainedProperties) {
    }

    /**
     * One component that could not be extracted, and why.
     *
     * <p>
     * The reason names the failure class and the component's own name, never the payload. A skip that quoted what it
     * could not parse would be a disclosure channel for exactly the documents least worth trusting.
     */
    public record Skip(String componentName, String reason) {
    }

    /** Everything a document yielded: the assets, and the components that could not be turned into assets. */
    public record Extraction(List<ExtractedAsset> assets, List<Skip> skips, boolean depthLimitReached) {

        public int assetCount() {
            return assets.size();
        }
    }

    /** Extracts with document scope only. Equivalent to passing no batch-scoped refutations. */
    public Extraction extract(JsonNode document) {
        return extract(document, Set.of());
    }

    /**
     * Extracts every cryptographic asset in the document.
     *
     * <p>
     * Output order is document order, and that is a convenience for a reader rather than a guarantee anything depends
     * on: an asset's identity is a function of the asset alone, so permuting the components -- or the documents --
     * cannot change which rows result or what they are keyed as.
     *
     * @param batchRefutedDigests certificate digests a batch-scoped index found contradicted <em>across</em> documents;
     * empty reduces to document-scoped refutation
     */
    public Extraction extract(JsonNode document, Set<String> batchRefutedDigests) {
        if (document == null || !document.isObject()) {
            return new Extraction(List.of(), List.of(), false);
        }

        DocumentScope scope;
        try {
            scope = DocumentScope.of(document, identity.normalizer());
        } catch (RuntimeException e) {
            // The scope is a whole-document derivation, so a failure here is not attributable to one component. The
            // walk still runs, with nothing refuted and no reference resolving, which under-merges rather than
            // over-merges -- the recoverable direction.
            scope = DocumentScope.none();
        }

        List<ExtractedAsset> assets = new ArrayList<>();
        List<Skip> skips = new ArrayList<>();
        Walk walk = walkComponents(document);
        for (JsonNode component : walk.components()) {
            if (!isCryptographicAsset(component)) {
                continue;
            }
            try {
                CryptoAssetIdentity.Identity keyed = identity.of(component, scope, batchRefutedDigests);
                assets
                        .add(new ExtractedAsset(keyed.key(), keyed.step(), keyed.asset(), nameOf(component),
                                keyed.redaction().payload()));
            } catch (RuntimeException e) {
                // Deliberately broad, and deliberately not logged with the throwable. Producer input reaches every
                // derivation below this line; the failure classes are open-ended, and one of them must not be fatal.
                skips.add(new Skip(nameOf(component), e.getClass().getSimpleName()));
            }
        }
        return new Extraction(List.copyOf(assets), List.copyOf(skips), walk.depthLimitReached());
    }

    /**
     * A component this specification has an opinion about.
     *
     * <p>
     * Either declaration is enough. A component <em>typed</em> {@code cryptographic-asset} with no
     * {@code cryptoProperties} at all is not skipped -- it keys on the unroutable backstop tier with its name, and
     * there is one in the validation corpus. And a component carrying {@code cryptoProperties} while typed something
     * else is still a cryptographic asset by the only evidence that matters, since the type field is producer text like
     * any other.
     */
    private static boolean isCryptographicAsset(JsonNode component) {
        JsonNode type = component.get("type");
        boolean declaredType = type != null && type.isTextual() && "cryptographic-asset".equals(type.textValue());
        JsonNode properties = component.get("cryptoProperties");
        return declaredType || (properties != null && properties.isObject());
    }

    private static String nameOf(JsonNode component) {
        JsonNode name = component.get("name");
        return name != null && name.isTextual() ? name.textValue() : "";
    }

    private record Walk(List<JsonNode> components, boolean depthLimitReached) {
    }

    /**
     * Every component in the tree, in document order, without recursing.
     *
     * <p>
     * An explicit stack rather than a recursive descent: see {@link #MAX_DEPTH}. The children of a component are pushed
     * in reverse so they pop in document order, which keeps the output readable without making anything depend on it.
     */
    private static Walk walkComponents(JsonNode document) {
        List<JsonNode> found = new ArrayList<>();
        Deque<Entry> pending = new ArrayDeque<>();
        pushChildren(document.get("components"), 1, pending);
        boolean depthLimitReached = false;
        while (!pending.isEmpty()) {
            Entry entry = pending.pop();
            found.add(entry.component());
            if (entry.depth() >= MAX_DEPTH) {
                depthLimitReached = true;
                continue;
            }
            pushChildren(entry.component().get("components"), entry.depth() + 1, pending);
        }
        return new Walk(found, depthLimitReached);
    }

    private static void pushChildren(JsonNode components, int depth, Deque<Entry> pending) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (int index = components.size() - 1; index >= 0; index--) {
            JsonNode child = components.get(index);
            if (child.isObject()) {
                pending.push(new Entry(child, depth));
            }
        }
    }

    private record Entry(JsonNode component, int depth) {
    }
}
