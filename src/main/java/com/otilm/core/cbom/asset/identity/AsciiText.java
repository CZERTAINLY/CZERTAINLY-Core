package com.otilm.core.cbom.asset.identity;

import java.util.regex.Pattern;

/**
 * ASCII-only case folding, and the separator-dropping fold used for table lookup.
 *
 * <p>
 * The governing rule (specification R12): an operation applied to a keyed value must not be able to change across a
 * runtime or library version. Locale is only one such dependency -- an unpinned {@code toUpperCase()} turns
 * {@code Camellia} into {@code CAMELL<dotless I>A} under Turkish and loses the family -- but Unicode case-folding
 * tables are another, and they have changed between Unicode versions. A stored unique arbiter that shifts on a JDK
 * upgrade re-keys the estate silently.
 *
 * <p>
 * Two concrete hazards a Unicode fold carries that this one does not:
 *
 * <ul>
 * <li>{@code U+212A KELVIN SIGN} case-folds to ASCII {@code k}, so a non-ASCII character could <em>alias</em> onto a
 * registry token and claim a family it does not have.</li>
 * <li>{@code <sharp s>.toUpperCase()} is {@code SS} -- length-changing -- so an uppercase fold is strictly worse than a
 * lowercase one on non-ASCII input.</li>
 * </ul>
 *
 * <p>
 * Non-ASCII characters therefore pass through unchanged. They can never fold onto an ASCII token, and a value differing
 * only in non-ASCII case under-merges -- the safe direction, and stable forever. The resulting duplicate is not
 * accepted silently: it is reported by the case-fold twin detector, which may use the runtime's Unicode tables
 * precisely because its answer never enters a key.
 */
public final class AsciiText {

    private static final Pattern LOOKUP_SEPARATORS = Pattern.compile("[\\s_\\-/]+");

    private AsciiText() {
    }

    /** Lower-cases ASCII letters only, leaving every other code point untouched. */
    public static String fold(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder folded = null;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                if (folded == null) {
                    folded = new StringBuilder(text);
                }
                folded.setCharAt(index, (char) (character + 32));
            }
        }
        return folded == null ? text : folded.toString();
    }

    /**
     * Upper-cases ASCII letters only -- the same restriction as {@link #fold}, upward.
     *
     * <p>
     * Enum-shaped slots (mode, padding, cipher-suite names) are upper-cased before they enter a key, and some of them
     * carry producer-controlled text. {@code String.toUpperCase()} would apply the runtime's tables to that text.
     */
    public static String upper(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder uppered = null;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= 'a' && character <= 'z') {
                if (uppered == null) {
                    uppered = new StringBuilder(text);
                }
                uppered.setCharAt(index, (char) (character - 32));
            }
        }
        return uppered == null ? text : uppered.toString();
    }

    /**
     * Normalizes for table <em>lookup</em>: drops separators, then ASCII-folds case.
     *
     * <p>
     * Lookup-only. A value folded this way is matched against a table; it is the table's own spelling that enters a
     * key, never this. That is what lets {@code aes}, {@code AES} and {@code A-E-S} all resolve to the registry's
     * {@code AES} without any of those spellings being keyed.
     */
    public static String lookupKey(String text) {
        return text == null ? null : fold(LOOKUP_SEPARATORS.matcher(text).replaceAll(""));
    }

    public static boolean isAsciiPrintable(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < 32 || character > 126) {
                return false;
            }
        }
        return true;
    }
}
