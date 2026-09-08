package com.otilm.core.service.registration;

import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.certificate.CertificateEvent;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.repository.CertificateRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Resolves a protocol enrolment that names its pre-registration by certificate UUID (CMP senderKID, ACME EAB kid) to a
 * certificate of the profile's RA profile that the protocol's eligibility rule admits, and verifies the message MAC
 * against the registration challenge through the shared {@link RegistrationChallengeGate}, so a wrong MAC is counted
 * and eventually locks out exactly one authorization. The MAC key is the UTF-8 bytes of the challenge text for every
 * protocol.
 */
@Service
public class RegistrationResolver {

    private CertificateRepository certificateRepository;
    private RegistrationChallengeGate registrationChallengeGate;

    @Autowired
    public void setCertificateRepository(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    @Autowired
    public void setRegistrationChallengeGate(RegistrationChallengeGate registrationChallengeGate) {
        this.registrationChallengeGate = registrationChallengeGate;
    }

    /** The MAC key bytes of a registration challenge, as CMP and ACME clients derive them. */
    public static byte[] macKey(String challenge) {
        return challenge.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param registrationUuid the referenced certificate, or null when the reference did not parse
     * @param eligible the protocol's rule for which certificate of the RA profile admits the operation (state,
     * archival)
     */
    public Certificate resolveEligible(RaProfile raProfile, UUID registrationUuid, Predicate<Certificate> eligible)
            throws RegistrationRejectedException {
        if (registrationUuid == null) {
            throw new RegistrationRejectedException("the request does not carry a certificate registration reference");
        }
        Certificate certificate = certificateRepository.findByUuid(registrationUuid).orElse(null);
        if (certificate == null || !raProfile.getUuid().equals(certificate.getRaProfileUuid())
                || !eligible.test(certificate)) {
            throw new RegistrationRejectedException(
                    "the reference does not name an eligible certificate of this RA profile");
        }
        return certificate;
    }

    /**
     * Verifies the message MAC through the gate and returns the challenge plaintext that verified it, for presenting as
     * {@code authorizationSecret} and keying a protected response.
     *
     * @param macMatches given a candidate key's bytes, whether the message MAC verifies under it
     */
    public String verifyMac(Certificate certificate, CertificateEvent event, Predicate<byte[]> macMatches)
            throws RegistrationRejectedException {
        // The gate resolves the plaintext internally and hands it to the predicate; capture the verifying one.
        String[] captured = new String[1];
        boolean verified;
        try {
            verified = registrationChallengeGate.verify(certificate.getUuid(), event, plaintext -> {
                boolean ok = macMatches.test(macKey(plaintext));
                if (ok) {
                    captured[0] = plaintext;
                }
                return ok;
            });
        } catch (ValidationException denial) {
            // Locked, expired window, or wrong MAC (counted). Detail stays in the gate's event history.
            throw new RegistrationRejectedException("registration challenge denied: " + denial.getMessage());
        } catch (UnusableRegistrationChallengeException unusable) {
            // The verifier could not use the challenge as key material; the gate rolled back without counting.
            throw new RegistrationRejectedException("registration challenge unusable: " + unusable.getMessage());
        }
        if (!verified) {
            throw new RegistrationRejectedException("the certificate has no active registration authorization");
        }
        return captured[0];
    }
}
