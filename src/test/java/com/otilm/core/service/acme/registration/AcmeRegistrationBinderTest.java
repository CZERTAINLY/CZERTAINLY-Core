package com.otilm.core.service.acme.registration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.otilm.api.exception.AcmeProblemDocumentException;
import com.otilm.api.exception.ValidationException;
import com.otilm.api.model.core.acme.ExternalAccountBinding;
import com.otilm.api.model.core.acme.NewAccountRequest;
import com.otilm.api.model.core.acme.Problem;
import com.otilm.api.model.core.certificate.CertificateEvent;
import com.otilm.api.model.core.certificate.CertificateState;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.repository.CertificateRepository;
import com.otilm.core.dao.repository.acme.AcmeAccountRepository;
import com.otilm.core.service.registration.RegistrationChallengeGate;
import com.otilm.core.service.registration.RegistrationResolver;
import com.otilm.core.service.registration.UnusableRegistrationChallengeException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AcmeRegistrationBinderTest {

    private static final UUID CERT_UUID = UUID.randomUUID();
    private static final UUID RA_PROFILE_UUID = UUID.randomUUID();
    private static final URI NEW_ACCOUNT = URI.create("https://acme.example/api/acme/profile/new-account");

    private AcmeRegistrationBinder binder;
    private CertificateRepository certificateRepository;
    private AcmeAccountRepository acmeAccountRepository;
    private RegistrationChallengeGate gate;
    private RaProfile raProfile;
    private ECKey accountKey;
    private String challenge;

    @BeforeEach
    void setUp() throws JOSEException {
        certificateRepository = mock(CertificateRepository.class);
        acmeAccountRepository = mock(AcmeAccountRepository.class);
        gate = mock(RegistrationChallengeGate.class);
        RegistrationResolver registrationResolver = new RegistrationResolver();
        registrationResolver.setCertificateRepository(certificateRepository);
        registrationResolver.setRegistrationChallengeGate(gate);
        binder = new AcmeRegistrationBinder();
        binder.setRegistrationResolver(registrationResolver);
        binder.setAcmeAccountRepository(acmeAccountRepository);
        raProfile = new RaProfile();
        raProfile.setUuid(RA_PROFILE_UUID);
        accountKey = new ECKeyGenerator(Curve.P_256).generate();
        challenge = AcmeEabCredential.generateChallenge();
    }

    private Certificate registeredCertificate() {
        Certificate certificate = new Certificate();
        certificate.setUuid(CERT_UUID);
        certificate.setRaProfileUuid(RA_PROFILE_UUID);
        certificate.setState(CertificateState.REGISTERED);
        return certificate;
    }

    private void gateResolvesTheChallenge() {
        when(gate.verify(eq(CERT_UUID), eq(CertificateEvent.ISSUE), any())).thenAnswer(invocation -> {
            Predicate<String> secretMatches = invocation.getArgument(2);
            return secretMatches.test(challenge);
        });
    }

    private static NewAccountRequest requestWith(ExternalAccountBinding binding) {
        NewAccountRequest request = new NewAccountRequest();
        request.setExternalAccountBinding(binding);
        return request;
    }

    private ExternalAccountBinding bindingWith(UUID kid, String url, ECKey key, String macKeyText)
            throws JOSEException {
        return EabTestUtil.build(kid, url, key, RegistrationResolver.macKey(macKeyText));
    }

    private ExternalAccountBinding validBinding() throws JOSEException {
        return bindingWith(CERT_UUID, NEW_ACCOUNT.toString(), accountKey, challenge);
    }

    private void assertGenericRejection(NewAccountRequest request) {
        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> binder.resolveBinding(raProfile, request, accountKey.toPublicJWK(), NEW_ACCOUNT));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getHttpStatusCode());
        assertEquals(Problem.UNAUTHORIZED.getType(), ex.getProblemDocument().getType());
        assertEquals(AcmeRegistrationBinder.REGISTRATION_REJECTION, ex.getProblemDocument().getDetail());
    }

    @Test
    void validBindingResolvesTheRegisteredCertificate() throws Exception {
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(registeredCertificate()));
        gateResolvesTheChallenge();

        Certificate resolved = binder
                .resolveBinding(raProfile, requestWith(validBinding()), accountKey.toPublicJWK(), NEW_ACCOUNT);

        assertEquals(CERT_UUID, resolved.getUuid());
    }

    @Test
    void missingBindingAnswersExternalAccountRequired() {
        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> binder.resolveBinding(raProfile, new NewAccountRequest(), accountKey.toPublicJWK(), NEW_ACCOUNT));

        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getHttpStatusCode());
        assertEquals(Problem.EXTERNAL_ACCOUNT_REQUIRED.getType(), ex.getProblemDocument().getType());
        verifyNoInteractions(certificateRepository, gate);
    }

    @Test
    void malformedBindingIsRejectedGenericallyBeforeAnyLookup() throws Exception {
        ExternalAccountBinding binding = validBinding();
        binding.setPayload("***");

        assertGenericRejection(requestWith(binding));
        verifyNoInteractions(certificateRepository, gate);
    }

    @Test
    void bindingForAnotherUrlIsRejectedGenericallyBeforeAnyLookup() throws Exception {
        assertGenericRejection(requestWith(bindingWith(CERT_UUID, NEW_ACCOUNT + "/other", accountKey, challenge)));
        verifyNoInteractions(certificateRepository, gate);
    }

    @Test
    void bindingOfAnotherAccountKeyIsRejectedGenericallyBeforeAnyLookup() throws Exception {
        ECKey otherKey = new ECKeyGenerator(Curve.P_256).generate();

        assertGenericRejection(requestWith(bindingWith(CERT_UUID, NEW_ACCOUNT.toString(), otherKey, challenge)));
        verifyNoInteractions(certificateRepository, gate);
    }

    @Test
    void unknownKidIsRejectedGenericallyWithoutReachingTheGate() throws Exception {
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.empty());

        assertGenericRejection(requestWith(validBinding()));
        verifyNoInteractions(gate);
    }

    @Test
    void nonUuidKidIsRejectedGenericallyWithoutAnyLookup() throws Exception {
        assertGenericRejection(requestWith(EabTestUtil
                .build(JWSAlgorithm.HS256, "not-a-uuid", NEW_ACCOUNT.toString(), accountKey,
                        RegistrationResolver.macKey(challenge))));
        verifyNoInteractions(certificateRepository, gate);
    }

    @Test
    void certificateOfAnotherRaProfileIsRejectedGenerically() throws Exception {
        Certificate other = registeredCertificate();
        other.setRaProfileUuid(UUID.randomUUID());
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(other));

        assertGenericRejection(requestWith(validBinding()));
        verifyNoInteractions(gate);
    }

    @Test
    void archivedRegisteredCertificateIsRejectedGenerically() throws Exception {
        Certificate archived = registeredCertificate();
        archived.setArchived(true);
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(archived));

        assertGenericRejection(requestWith(validBinding()));
        verifyNoInteractions(gate);
    }

    @Test
    void challengeTooShortForHs256SurfacesAsTheGenericRejection() throws Exception {
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(registeredCertificate()));
        // The verifier abandons the gate evaluation with the typed exception; the resolver maps it to a rejection.
        when(gate.verify(eq(CERT_UUID), eq(CertificateEvent.ISSUE), any())).thenAnswer(invocation -> {
            Predicate<String> secretMatches = invocation.getArgument(2);
            UnusableRegistrationChallengeException raised = assertThrows(UnusableRegistrationChallengeException.class,
                    () -> secretMatches.test("device-7-secret"));
            throw raised;
        });

        assertGenericRejection(requestWith(validBinding()));
    }

    @Test
    void certificateNotInRegisteredStateIsRejectedGenerically() throws Exception {
        Certificate issued = registeredCertificate();
        issued.setState(CertificateState.ISSUED);
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(issued));

        assertGenericRejection(requestWith(validBinding()));
        verifyNoInteractions(gate);
    }

    @Test
    void alreadyBoundRegistrationIsRejectedGenericallyBeforeTheGate() throws Exception {
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(registeredCertificate()));
        when(acmeAccountRepository.existsByRegistrationCertificateUuid(CERT_UUID)).thenReturn(true);

        assertGenericRejection(requestWith(validBinding()));
        // A correct key replayed against a bound registration must not reach the gate, which would reset its counter.
        verifyNoInteractions(gate);
    }

    @Test
    void wrongMacIsCountedByTheGateAndRejectedGenerically() throws Exception {
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(registeredCertificate()));
        gateResolvesTheChallenge();
        ExternalAccountBinding wrongKey = bindingWith(CERT_UUID, NEW_ACCOUNT.toString(), accountKey,
                AcmeEabCredential.generateChallenge());

        assertGenericRejection(requestWith(wrongKey));
    }

    @Test
    void gateDenialIsRejectedGenerically() throws Exception {
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(registeredCertificate()));
        when(gate.verify(eq(CERT_UUID), eq(CertificateEvent.ISSUE), any()))
                .thenThrow(new ValidationException("locked"));

        assertGenericRejection(requestWith(validBinding()));
    }

    @Test
    void certificateWithoutActiveAuthorizationIsRejectedGenerically() throws Exception {
        when(certificateRepository.findByUuid(CERT_UUID)).thenReturn(Optional.of(registeredCertificate()));
        when(gate.verify(eq(CERT_UUID), eq(CertificateEvent.ISSUE), any())).thenReturn(false);

        assertGenericRejection(requestWith(validBinding()));
    }
}
