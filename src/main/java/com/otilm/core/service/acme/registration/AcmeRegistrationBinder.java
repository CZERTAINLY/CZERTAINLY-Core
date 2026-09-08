package com.otilm.core.service.acme.registration;

import com.nimbusds.jose.jwk.JWK;
import com.otilm.api.exception.AcmeProblemDocumentException;
import com.otilm.api.model.core.acme.NewAccountRequest;
import com.otilm.api.model.core.acme.Problem;
import com.otilm.api.model.core.certificate.CertificateEvent;
import com.otilm.api.model.core.certificate.CertificateState;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.repository.acme.AcmeAccountRepository;
import com.otilm.core.service.registration.RegistrationRejectedException;
import com.otilm.core.service.registration.RegistrationResolver;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Resolves the External Account Binding of a newAccount request on a registration-mode ACME profile to the certificate
 * pre-registration it names: the binding's {@code kid} is the pre-registered certificate's UUID and its MAC key is the
 * registration challenge. Resolution and MAC verification run through the shared {@link RegistrationResolver}; this
 * class adds the RFC 8555 section 7.3.4 checks (the binding is for this request URL and this account key) and the
 * one-account-per-registration rule.
 *
 * <p>
 * A missing binding answers with the RFC-defined {@code externalAccountRequired} problem, which the directory already
 * advertises. Every other failure (malformed binding, wrong URL or key, unknown or ineligible kid, registration already
 * bound, MAC mismatch, locked or expired registration) is the single generic rejection; the reason stays in the log
 * and, where the gate is reached, in the certificate's event history. The checks that need no secret run before the
 * gate, so a mismatched URL or key spends no attempt and a replay against an already-bound registration cannot reset
 * its failed-attempt counter.
 */
@Service
public class AcmeRegistrationBinder {

    private static final Logger logger = LoggerFactory.getLogger(AcmeRegistrationBinder.class);

    /**
     * The single wire detail for every registration-mode binding rejection, so a prober cannot enumerate registrations.
     */
    public static final String REGISTRATION_REJECTION = "The external account binding does not match an active certificate registration.";

    private RegistrationResolver registrationResolver;
    private AcmeAccountRepository acmeAccountRepository;

    @Autowired
    public void setRegistrationResolver(RegistrationResolver registrationResolver) {
        this.registrationResolver = registrationResolver;
    }

    @Autowired
    public void setAcmeAccountRepository(AcmeAccountRepository acmeAccountRepository) {
        this.acmeAccountRepository = acmeAccountRepository;
    }

    /**
     * Resolves and verifies the binding of a newAccount request and returns the pre-registered certificate the new
     * account is to be bound to.
     *
     * @param accountKey the JWK the newAccount JWS is signed with, which the binding payload must equal
     * @param requestUri the newAccount URL the binding's protected header must name
     * @throws AcmeProblemDocumentException {@code externalAccountRequired} when the binding is absent; the generic
     * rejection on any other failure
     */
    public Certificate resolveBinding(RaProfile raProfile, NewAccountRequest request, JWK accountKey, URI requestUri)
            throws AcmeProblemDocumentException {
        if (request.getExternalAccountBinding() == null) {
            logger.info("ACME registration-mode newAccount rejected: no external account binding");
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.EXTERNAL_ACCOUNT_REQUIRED);
        }
        ExternalAccountBindingJws binding = ExternalAccountBindingJws.parse(request.getExternalAccountBinding());
        if (binding == null) {
            throw rejection("external account binding is malformed");
        }
        if (!binding.isForUrl(requestUri.toString())) {
            throw rejection("external account binding is not for this newAccount URL");
        }
        if (!binding.bindsAccountKey(accountKey)) {
            throw rejection("external account binding payload is not the account key");
        }
        try {
            Certificate certificate = registrationResolver
                    .resolveEligible(raProfile, binding.registrationUuid(), AcmeRegistrationBinder::isBindable);
            if (acmeAccountRepository.existsByRegistrationCertificateUuid(certificate.getUuid())) {
                throw rejection("registration is already bound to an account");
            }
            registrationResolver.verifyMac(certificate, CertificateEvent.ISSUE, binding::verify);
            return certificate;
        } catch (RegistrationRejectedException e) {
            throw rejection(e.getMessage());
        }
    }

    /** An uncompleted, unarchived placeholder; a bound account then completes it over ACME. */
    private static boolean isBindable(Certificate certificate) {
        return certificate.getState() == CertificateState.REGISTERED && !certificate.isArchived();
    }

    private static AcmeProblemDocumentException rejection(String reason) {
        logger.info("ACME registration-mode newAccount rejected: {}", reason);
        return new AcmeProblemDocumentException(HttpStatus.UNAUTHORIZED, Problem.UNAUTHORIZED, REGISTRATION_REJECTION);
    }
}
