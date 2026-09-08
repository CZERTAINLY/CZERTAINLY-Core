package com.otilm.core.service.cmp.registration;

import com.otilm.api.interfaces.core.cmp.error.CmpBaseException;
import com.otilm.api.interfaces.core.cmp.error.CmpProcessingException;
import com.otilm.api.model.core.certificate.CertificateEvent;
import com.otilm.api.model.core.certificate.CertificateState;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.CertificateRelationId;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.entity.cmp.CmpTransaction;
import com.otilm.core.dao.repository.CertificateRelationRepository;
import com.otilm.core.service.registration.RegistrationRejectedException;
import com.otilm.core.service.registration.RegistrationResolver;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Predicate;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Binds a MAC-protected CMP request to its pre-registered certificate: the {@code senderKID} names the pre-registration
 * by UUID, and the registration challenge is the MAC key. Resolution and MAC verification run through the shared
 * {@link RegistrationResolver}; this class supplies the CMP state rules, the transaction binding of follow-ups, and the
 * single generic wire rejection.
 */
@Service
public class CmpRegistrationResolver {

    private static final Logger logger = LoggerFactory.getLogger(CmpRegistrationResolver.class);

    /** The single wire message for every registration-mode rejection, so a prober cannot enumerate registrations. */
    public static final String REGISTRATION_REJECTION = "The request does not match an active certificate registration.";

    private RegistrationResolver registrationResolver;
    private CertificateRelationRepository certificateRelationRepository;

    @Autowired
    public void setRegistrationResolver(RegistrationResolver registrationResolver) {
        this.registrationResolver = registrationResolver;
    }

    @Autowired
    public void setCertificateRelationRepository(CertificateRelationRepository certificateRelationRepository) {
        this.certificateRelationRepository = certificateRelationRepository;
    }

    /**
     * The matched pre-registration and its challenge plaintext (for {@code authorizationSecret} and response keying).
     */
    public record RegistrationMacResolution(Certificate certificate, String challenge) {
    }

    /**
     * Resolves the senderKID to an unarchived certificate of {@code raProfile} whose state admits the operation (a
     * REGISTERED placeholder for issuance, an issued certificate for rekey), verifies the message MAC via
     * {@code macMatches} through the challenge gate, and returns the match.
     *
     * @param macMatches given a candidate challenge key's bytes, whether the message MAC verifies under it
     * @throws CmpProcessingException the single generic rejection ({@link PKIFailureInfo#badMessageCheck}) on any
     * failure
     */
    public RegistrationMacResolution resolveAndVerify(RaProfile raProfile, ASN1OctetString senderKID,
            CertificateEvent event, Predicate<byte[]> macMatches, ASN1OctetString tid) throws CmpBaseException {
        return resolve(raProfile, senderKID, event,
                certificate -> !certificate.isArchived() && stateMatchesOperation(certificate.getState(), event),
                macMatches, tid);
    }

    /**
     * As {@link #resolveAndVerify} but for a CMP follow-up (pollReq / certConf) of a registration exchange: by the time
     * the client polls, the placeholder has moved past {@code REGISTERED}, so the certificate state is not constrained
     * — only that the senderKID references a certificate of {@code raProfile} whose authorization still verifies the
     * MAC through the gate (a forged follow-up is still counted and locks out). Issuance leaves the authorization
     * ACTIVE, so the challenge remains available to key the response. Archival is not constrained either: an operator
     * archiving the issued certificate must not strand the device's confirmation.
     */
    public RegistrationMacResolution resolveAndVerifyFollowup(RaProfile raProfile, ASN1OctetString senderKID,
            Predicate<byte[]> macMatches, ASN1OctetString tid) throws CmpBaseException {
        return resolve(raProfile, senderKID, CertificateEvent.ISSUE, certificate -> true, macMatches, tid);
    }

    private RegistrationMacResolution resolve(RaProfile raProfile, ASN1OctetString senderKID, CertificateEvent event,
            Predicate<Certificate> eligible, Predicate<byte[]> macMatches, ASN1OctetString tid)
            throws CmpProcessingException {
        try {
            Certificate certificate = registrationResolver
                    .resolveEligible(raProfile, parseSenderKid(senderKID), eligible);
            String challenge = registrationResolver.verifyMac(certificate, event, macMatches);
            return new RegistrationMacResolution(certificate, challenge);
        } catch (RegistrationRejectedException e) {
            throw rejection(tid, e.getMessage());
        }
    }

    /**
     * Requires a registration-mode follow-up (pollReq / certConf) to act only on a transaction its senderKID's
     * registration opened: the transaction's certificate must be the matched registration itself (ir/cr) or its
     * recorded rekey/renewal successor (a kur transaction stores the successor, while the authorization that
     * authenticated the MAC lives on the predecessor). Without this bind, any active registration under the RA profile
     * could poll or confirm another registration's transaction; the mismatch surfaces as the single generic rejection.
     */
    public void requireTransactionBinding(Certificate matchedRegistration, CmpTransaction transaction,
            ASN1OctetString tid) throws CmpProcessingException {
        if (matchedRegistration == null) {
            throw bindingRejection(tid, "follow-up reached the handler without a matched registration");
        }
        UUID transactionCertificateUuid = transaction.getCertificateUuid();
        if (matchedRegistration.getUuid().equals(transactionCertificateUuid)) {
            return;
        }
        if (transactionCertificateUuid == null || !certificateRelationRepository
                .existsById(new CertificateRelationId(transactionCertificateUuid, matchedRegistration.getUuid()))) {
            throw bindingRejection(tid, "transaction is not bound to the authenticated registration");
        }
    }

    /** Same wire rejection as {@link #rejection}, typed so the error handling spares the named transaction. */
    private static CmpTransactionNotBoundException bindingRejection(ASN1OctetString tid, String reason) {
        logger.info("CMP registration enrolment rejected: {}", reason);
        return new CmpTransactionNotBoundException(tid, PKIFailureInfo.badMessageCheck, REGISTRATION_REJECTION);
    }

    /**
     * ir/cr completes a REGISTERED placeholder; kur rekeys the already-issued certificate whose registration
     * authorization survived issuance (a REGISTERED placeholder has no key to rekey).
     */
    private static boolean stateMatchesOperation(CertificateState state, CertificateEvent event) {
        return event == CertificateEvent.REKEY
                ? state != CertificateState.REGISTERED
                : state == CertificateState.REGISTERED;
    }

    private static UUID parseSenderKid(ASN1OctetString senderKID) {
        if (senderKID == null || senderKID.getOctets().length == 0) {
            return null;
        }
        try {
            return UUID.fromString(new String(senderKID.getOctets(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static CmpProcessingException rejection(ASN1OctetString tid, String reason) {
        logger.info("CMP registration enrolment rejected: {}", reason);
        return new CmpProcessingException(tid, PKIFailureInfo.badMessageCheck, REGISTRATION_REJECTION);
    }
}
