package com.otilm.core.service.registration;

import com.otilm.core.util.CertificateUtil;
import com.otilm.core.util.PlatformX500NameStyle;
import org.bouncycastle.asn1.x500.X500Name;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure matching kernel binding a protocol enrolment to a pre-registered certificate. Subjects compare by their
 * {@link PlatformX500NameStyle#NORMALIZED} rendering, which neutralizes RDN order, attribute-name case and
 * spacing but deliberately preserves attribute-value case — the enrolment must present the identity exactly
 * as registered. SAN sets compare as maps of sorted value lists with empty entries dropped, so an absent
 * SAN column, an empty map and all-empty buckets are the same identity.
 *
 * <p>The kernel never checks the challenge — the caller verifies it against the single matched
 * registration, keeping failed-attempt accounting attributable to exactly one authorization.
 */
public final class RegistrationIdentityMatcher {

    /** A pre-registered candidate: the certificate row's UUID, subject DN and serialized SAN column. */
    public record Candidate(UUID certificateUuid, String subjectDn, String serializedSans) {
    }

    public enum Outcome {
        /** Exactly one registration matches subject and SANs; its UUID is in the result. */
        MATCHED,
        /** No registration matches the subject. */
        NO_MATCH,
        /** Several registrations match the subject and the SAN tiebreak does not single one out. */
        AMBIGUOUS,
        /**
         * Exactly one registration matches the subject but its SANs differ from the CSR's; the result
         * carries its UUID so the caller can record the failure against it.
         */
        SAN_MISMATCH
    }

    /** Result of a match: the outcome, and the involved certificate UUID for MATCHED and SAN_MISMATCH. */
    public record MatchResult(Outcome outcome, UUID certificateUuid) {

        private static MatchResult matched(UUID certificateUuid) {
            return new MatchResult(Outcome.MATCHED, certificateUuid);
        }

        private static MatchResult noMatch() {
            return new MatchResult(Outcome.NO_MATCH, null);
        }

        private static MatchResult ambiguous() {
            return new MatchResult(Outcome.AMBIGUOUS, null);
        }

        private static MatchResult sanMismatch(UUID certificateUuid) {
            return new MatchResult(Outcome.SAN_MISMATCH, certificateUuid);
        }
    }

    private RegistrationIdentityMatcher() {
    }

    /**
     * Matches the CSR identity against the candidates: candidates are filtered by normalized subject; a
     * unique subject match must then carry SANs equal to the CSR's; several subject matches narrow to the
     * single candidate with SAN equality. A candidate whose stored DN does not parse is skipped — one
     * malformed row must not block matching for the others.
     */
    public static MatchResult match(X500Name csrSubject, Map<String, List<String>> csrSans, List<Candidate> candidates) {
        String normalizedCsrSubject = normalize(csrSubject);
        Map<String, List<String>> normalizedCsrSans = normalizeSans(csrSans);

        List<Candidate> subjectMatches = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String normalizedCandidateSubject;
            try {
                normalizedCandidateSubject = normalize(new X500Name(PlatformX500NameStyle.NORMALIZED, candidate.subjectDn()));
            } catch (RuntimeException e) {
                continue;
            }
            if (normalizedCsrSubject.equals(normalizedCandidateSubject)) {
                subjectMatches.add(candidate);
            }
        }

        if (subjectMatches.isEmpty()) {
            return MatchResult.noMatch();
        }
        if (subjectMatches.size() == 1) {
            Candidate single = subjectMatches.getFirst();
            return sansEqual(normalizedCsrSans, single)
                    ? MatchResult.matched(single.certificateUuid())
                    : MatchResult.sanMismatch(single.certificateUuid());
        }
        List<Candidate> sanMatches = subjectMatches.stream()
                .filter(candidate -> sansEqual(normalizedCsrSans, candidate))
                .toList();
        return sanMatches.size() == 1
                ? MatchResult.matched(sanMatches.getFirst().certificateUuid())
                : MatchResult.ambiguous();
    }

    private static String normalize(X500Name subject) {
        return X500Name.getInstance(PlatformX500NameStyle.NORMALIZED, subject).toString();
    }

    private static boolean sansEqual(Map<String, List<String>> normalizedCsrSans, Candidate candidate) {
        return normalizedCsrSans.equals(normalizeSans(CertificateUtil.deserializeSans(candidate.serializedSans())));
    }

    /** Drops empty buckets and sorts each value list, so bucket presence and value order carry no meaning. */
    private static Map<String, List<String>> normalizeSans(Map<String, List<String>> sans) {
        Map<String, List<String>> normalized = new TreeMap<>();
        if (sans == null) {
            return normalized;
        }
        sans.forEach((type, values) -> {
            if (values != null && !values.isEmpty()) {
                normalized.put(type, values.stream().sorted().toList());
            }
        });
        return normalized;
    }
}
