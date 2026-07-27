package com.otilm.core.oid;

import com.otilm.api.model.core.oid.OidCategory;
import com.otilm.api.model.core.oid.SystemOid;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
public class OidHandler {

    private OidHandler() {
    }

    /** Dotted-decimal OID form (e.g. {@code 2.5.4.3}); anything else is treated as a short RDN code. */
    private static final Pattern OID_PATTERN = Pattern.compile("^[0-2](\\.(0|[1-9]\\d{0,38})){1,127}$");

    /** {@code true} if {@code value} is a well-formed dotted-decimal OID (never {@code null}-safe true). */
    public static boolean isOid(String value) {
        return value != null && OID_PATTERN.matcher(value).matches();
    }

    private static final Map<OidCategory, Map<String, OidRecord>> oidCache = new ConcurrentHashMap<>();

    /**
     * Case-insensitive RDN code/altCode → OID lookup. Rebuilt on every RDN cache mutation and
     * republished as an immutable snapshot, so readers on hot paths (DN parsing) never iterate
     * a map another thread may be mutating.
     */
    private static final AtomicReference<Map<String, String>> rdnCodeToOid =
            new AtomicReference<>(Collections.emptyMap());

    /**
     * Serializes writers so that the read-copy-publish of a per-category map and the rebuild of the
     * derived {@link #rdnCodeToOid} index happen as one unit. A private monitor is used rather than
     * the {@code OidHandler.class} object so foreign code cannot contend on the same lock.
     */
    private static final Object WRITE_LOCK = new Object();

    /** The published per-category map, or {@code null} when the category is not loaded. Immutable — see {@link #cacheOidCategory}. */
    public static Map<String, OidRecord> getOidCache(OidCategory oidCategory) {
        return oidCache.get(oidCategory);
    }

    public static void cacheOidCategory(OidCategory category, Map<String, OidRecord> oidRecordMap) {
        synchronized (WRITE_LOCK) {
            // Publish an immutable defensive copy: per-category maps are iterated lock-free by
            // readers (getCodeToOidMap, style snapshots), so a caller mutating the passed map
            // after publish would desync oidCache from the derived rdnCodeToOid index. Copying
            // here makes the copy-on-write contract structural rather than convention-only.
            oidCache.put(category, Collections.unmodifiableMap(new HashMap<>(oidRecordMap)));
            refreshRdnCodeLookup(category);
        }
    }

    public static void cacheOid(OidCategory category, String oid, OidRecord oidRecord) {
        synchronized (WRITE_LOCK) {
            // Copy-on-write: build the next map fresh, then publish it immutable so readers never
            // observe a map another thread might mutate. getOrDefault keeps the first write to an
            // as-yet-uncached category from throwing.
            Map<String, OidRecord> next = new HashMap<>(oidCache.getOrDefault(category, Map.of()));
            next.put(oid, oidRecord);
            oidCache.put(category, Collections.unmodifiableMap(next));
            refreshRdnCodeLookup(category);
        }
    }

    /** OID for an RDN code or alternative code, matched case-insensitively; {@code null} when unknown. */
    public static String getOidForRdnCode(String code) {
        return code == null ? null : rdnCodeToOid.get().get(code);
    }

    /**
     * The published, immutable, case-insensitive RDN code/altCode → OID snapshot. Capture it once
     * and reuse it for a whole DN so every RDN of one subject resolves against the same registry
     * state, rather than re-reading the reference per attribute.
     */
    public static Map<String, String> getRdnCodeToOidMap() {
        return rdnCodeToOid.get();
    }

    private static void refreshRdnCodeLookup(OidCategory category) {
        if (category != OidCategory.RDN_ATTRIBUTE_TYPE) {
            return;
        }
        Map<String, String> lookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        lookup.putAll(getCodeToOidMap());
        rdnCodeToOid.set(Collections.unmodifiableMap(lookup));
    }

    /**
     * RDN code/altCode → OID, matched case-insensitively so the authoring-time gate and request-time
     * resolution agree. Codes and alt codes share one flat namespace, so two OIDs can claim the same
     * token; see {@link #claimToken} for how that is resolved.
     */
    public static Map<String, String> getCodeToOidMap() {
        Map<String, String> reverseMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, OidRecord> rdnCache = oidCache.get(OidCategory.RDN_ATTRIBUTE_TYPE);
        if (rdnCache == null) {
            return reverseMap;
        }
        // Sorted so a contested token resolves the same way on every rebuild, rather than by the
        // iteration order of the backing HashMap.
        for (String oid : new TreeSet<>(rdnCache.keySet())) {
            OidRecord oidRecord = rdnCache.get(oid);
            if (oidRecord.code() != null) {
                claimToken(reverseMap, oidRecord.code(), oid);
            }
            if (oidRecord.altCodes() != null) {
                for (String altCode : oidRecord.altCodes()) {
                    if (altCode != null) {
                        claimToken(reverseMap, altCode, oid);
                    }
                }
            }
        }
        return reverseMap;
    }

    /**
     * Assigns one code token to an OID, resolving a contest in favour of the operator-registered
     * entry. A custom OID entry was already resolving that token before its OID became a system OID,
     * so an upgrade must not silently repoint it; the built-in entry stays reachable by its dotted
     * OID. Contests between two entries of the same kind keep the lowest OID, purely for determinism.
     */
    private static void claimToken(Map<String, String> reverseMap, String token, String oid) {
        String incumbent = reverseMap.get(token);
        if (incumbent == null || incumbent.equals(oid)) {
            reverseMap.put(token, oid);
            return;
        }
        boolean incumbentIsSystem = SystemOid.fromOID(incumbent) != null;
        boolean candidateIsSystem = SystemOid.fromOID(oid) != null;
        String winner = incumbentIsSystem && !candidateIsSystem ? oid : incumbent;
        reverseMap.put(token, winner);
        log.warn("RDN code '{}' is claimed by OID {} and OID {}; resolving it to {}. "
                        + "Rename the custom OID entry's code or alt code to remove the ambiguity.",
                token, incumbent, oid, winner);
    }


    public static void removeCachedOid(OidCategory category, String oid) {
        synchronized (WRITE_LOCK) {
            Map<String, OidRecord> current = oidCache.get(category);
            // Removing from a never-loaded category is a no-op: don't materialize an empty entry,
            // so getOidCache(category) stays null for callers that read null as "not loaded yet".
            if (current == null) {
                return;
            }
            Map<String, OidRecord> next = new HashMap<>(current);
            next.remove(oid);
            oidCache.put(category, Collections.unmodifiableMap(next));
            refreshRdnCodeLookup(category);
        }
    }
}
