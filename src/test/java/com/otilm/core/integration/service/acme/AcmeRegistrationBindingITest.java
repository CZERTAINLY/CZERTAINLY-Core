package com.otilm.core.integration.service.acme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.otilm.api.exception.AcmeProblemDocumentException;
import com.otilm.api.model.client.connector.v2.ConnectorVersion;
import com.otilm.api.model.core.acme.Account;
import com.otilm.api.model.core.acme.AccountStatus;
import com.otilm.api.model.core.acme.Directory;
import com.otilm.api.model.core.acme.ExternalAccountBinding;
import com.otilm.api.model.core.acme.NewAccountRequest;
import com.otilm.api.model.core.acme.Problem;
import com.otilm.api.model.core.certificate.CertificateState;
import com.otilm.api.model.core.connector.ConnectorStatus;
import com.otilm.api.model.core.protocol.ProtocolChallengeSource;
import com.otilm.core.dao.entity.AuthorityInstanceReference;
import com.otilm.core.dao.entity.Certificate;
import com.otilm.core.dao.entity.CertificateRegistrationAuthorization;
import com.otilm.core.dao.entity.Connector;
import com.otilm.core.dao.entity.RaProfile;
import com.otilm.core.dao.entity.RegistrationState;
import com.otilm.core.dao.entity.acme.AcmeAccount;
import com.otilm.core.dao.entity.acme.AcmeProfile;
import com.otilm.core.dao.repository.AcmeProfileRepository;
import com.otilm.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.otilm.core.dao.repository.CertificateRegistrationAuthorizationRepository;
import com.otilm.core.dao.repository.CertificateRepository;
import com.otilm.core.dao.repository.ConnectorRepository;
import com.otilm.core.dao.repository.RaProfileRepository;
import com.otilm.core.dao.repository.acme.AcmeAccountRepository;
import com.otilm.core.dao.repository.acme.AcmeNonceRepository;
import com.otilm.core.service.acme.AcmeExternalService;
import com.otilm.core.service.acme.AcmeTestUtil;
import com.otilm.core.service.acme.registration.AcmeEabCredential;
import com.otilm.core.service.acme.registration.AcmeRegistrationBinder;
import com.otilm.core.service.acme.registration.EabTestUtil;
import com.otilm.core.service.registration.RegistrationChallengeStore;
import com.otilm.core.service.registration.RegistrationResolver;
import com.otilm.core.util.BaseSpringBootTest;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * newAccount against an ACME profile in certificate-registration mode: the External Account Binding names a
 * pre-registration and is MAC-verified with its challenge through the shared gate, and the account is bound to that
 * registration. Not transactional: the gate commits its attempt counter in its own transaction.
 */
class AcmeRegistrationBindingITest extends BaseSpringBootTest {

    private static final String PROFILE_NAME = "acmeRegistrationProfile";
    private static final URI NEW_ACCOUNT = URI.create("http://localhost/api/acme/" + PROFILE_NAME + "/new-account");

    @Autowired
    private AcmeExternalService acmeService;
    @Autowired
    private AcmeProfileRepository acmeProfileRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateRegistrationAuthorizationRepository authorizationRepository;
    @Autowired
    private RegistrationChallengeStore registrationChallengeStore;
    @Autowired
    private AcmeAccountRepository acmeAccountRepository;
    @Autowired
    private AcmeNonceRepository acmeNonceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RaProfile raProfile;
    private AcmeProfile acmeProfile;
    private String challenge;

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        Connector connector = new Connector();
        connector.setName("acmeRegistrationConnector");
        connector.setUrl("http://localhost:1");
        connector.setVersion(ConnectorVersion.V1);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        AuthorityInstanceReference authority = new AuthorityInstanceReference();
        authority.setName("acmeRegistrationAuthority");
        authority.setAuthorityInstanceUuid("1l");
        authority.setConnector(connector);
        authority.setConnectorUuid(connector.getUuid());
        authority = authorityInstanceReferenceRepository.save(authority);

        raProfile = new RaProfile();
        raProfile.setEnabled(true);
        raProfile.setName("acmeRegistrationRaProfile");
        raProfile.setAuthorityInstanceReference(authority);
        raProfile.setAuthorityInstanceReferenceUuid(authority.getUuid());
        raProfile = raProfileRepository.save(raProfile);

        acmeProfile = new AcmeProfile();
        acmeProfile.setName(PROFILE_NAME);
        acmeProfile.setEnabled(true);
        acmeProfile.setDisableNewOrders(false);
        acmeProfile.setRequireContact(false);
        acmeProfile.setRequireTermsOfService(false);
        acmeProfile.setRetryInterval(30);
        acmeProfile.setValidity(30);
        acmeProfile.setRaProfile(raProfile);
        acmeProfile.setChallengeSource(ProtocolChallengeSource.CERTIFICATE_REGISTRATION);
        acmeProfile = acmeProfileRepository.save(acmeProfile);
        raProfile.setAcmeProfile(acmeProfile);
        raProfileRepository.save(raProfile);

        challenge = AcmeEabCredential.generateChallenge();
    }

    private Certificate seedRegistration() {
        Certificate certificate = new Certificate();
        certificate.setUuid(UUID.randomUUID());
        certificate.setSubjectDn("CN=device-1");
        certificate.setState(CertificateState.REGISTERED);
        certificate.setRaProfile(raProfile);
        certificate.setRaProfileUuid(raProfile.getUuid());
        certificate = certificateRepository.saveAndFlush(certificate);

        CertificateRegistrationAuthorization authorization = new CertificateRegistrationAuthorization();
        authorization.setCertificateUuid(certificate.getUuid());
        authorization.setState(RegistrationState.ACTIVE);
        authorization.setFailedAttempts(0);
        authorization.setExpiresAt(OffsetDateTime.now().plusDays(7));
        registrationChallengeStore.store(authorization, challenge);
        authorizationRepository.saveAndFlush(authorization);
        return certificate;
    }

    private static KeyPair accountKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static RSAKey accountJwk(KeyPair keyPair) {
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).build();
    }

    private String newAccountJws(KeyPair keyPair, ExternalAccountBinding binding) throws Exception {
        return newAccountJws(keyPair, binding, List.of("mailto:device@example.com"), true);
    }

    private String newAccountJws(KeyPair keyPair, ExternalAccountBinding binding, List<String> contact,
            boolean termsOfServiceAgreed) throws Exception {
        NewAccountRequest payload = new NewAccountRequest();
        payload.setContact(contact);
        payload.setTermsOfServiceAgreed(termsOfServiceAgreed);
        payload.setExternalAccountBinding(binding);
        return AcmeTestUtil
                .createJwsRequest(objectMapper, keyPair, acmeNonceRepository, payload, NEW_ACCOUNT.toString(), null,
                        PROFILE_NAME);
    }

    private ExternalAccountBinding bindingFor(Certificate registration, KeyPair keyPair, String macKeyText)
            throws Exception {
        return EabTestUtil
                .build(registration.getUuid(), NEW_ACCOUNT.toString(), accountJwk(keyPair),
                        RegistrationResolver.macKey(macKeyText));
    }

    private AcmeAccount boundAccountRow(Certificate registration) {
        AcmeAccount account = new AcmeAccount();
        account.setAcmeProfile(acmeProfile);
        account.setRaProfile(raProfile);
        account.setEnabled(true);
        account.setStatus(AccountStatus.VALID);
        account.setTermsOfServiceAgreed(true);
        account.setAccountId(UUID.randomUUID().toString());
        account.setPublicKey("public-key-" + UUID.randomUUID());
        account.setRegistrationCertificateUuid(registration.getUuid());
        return account;
    }

    private int failedAttempts(Certificate registration) {
        return authorizationRepository.findByCertificateUuid(registration.getUuid()).orElseThrow().getFailedAttempts();
    }

    @Test
    void directoryAdvertisesExternalAccountRequiredInRegistrationMode() throws Exception {
        ResponseEntity<Directory> directory = acmeService
                .getDirectory(PROFILE_NAME, URI.create("http://localhost/api/acme/" + PROFILE_NAME + "/directory"),
                        false);

        assertTrue(directory.getBody().getMeta().getExternalAccountRequired());
    }

    @Test
    void validBindingCreatesAnAccountBoundToTheRegistration() throws Exception {
        Certificate registration = seedRegistration();
        KeyPair keyPair = accountKeyPair();

        ResponseEntity<Account> response = acmeService
                .newAccount(PROFILE_NAME, newAccountJws(keyPair, bindingFor(registration, keyPair, challenge)),
                        NEW_ACCOUNT, false);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        List<AcmeAccount> accounts = acmeAccountRepository.findAll();
        assertEquals(1, accounts.size());
        assertEquals(registration.getUuid(), accounts.getFirst().getRegistrationCertificateUuid());
        assertEquals(0, failedAttempts(registration));
    }

    @Test
    void missingBindingAnswersExternalAccountRequired() throws Exception {
        seedRegistration();
        KeyPair keyPair = accountKeyPair();
        String request = newAccountJws(keyPair, null);

        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(PROFILE_NAME, request, NEW_ACCOUNT, false));

        assertEquals(Problem.EXTERNAL_ACCOUNT_REQUIRED.getType(), ex.getProblemDocument().getType());
        assertTrue(acmeAccountRepository.findAll().isEmpty());
    }

    @Test
    void wrongKeyIsRejectedGenericallyAndCountedAgainstTheRegistration() throws Exception {
        Certificate registration = seedRegistration();
        KeyPair keyPair = accountKeyPair();
        String request = newAccountJws(keyPair,
                bindingFor(registration, keyPair, AcmeEabCredential.generateChallenge()));

        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(PROFILE_NAME, request, NEW_ACCOUNT, false));

        assertEquals(AcmeRegistrationBinder.REGISTRATION_REJECTION, ex.getProblemDocument().getDetail());
        assertEquals(1, failedAttempts(registration),
                "a wrong EAB key spends one attempt, committed despite the rejection");
        assertTrue(acmeAccountRepository.findAll().isEmpty());
    }

    @Test
    void unknownRegistrationIsIndistinguishableFromAWrongKey() throws Exception {
        Certificate registration = seedRegistration();
        KeyPair keyPair = accountKeyPair();
        Certificate unknown = new Certificate();
        unknown.setUuid(UUID.randomUUID());
        String request = newAccountJws(keyPair, bindingFor(unknown, keyPair, challenge));

        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(PROFILE_NAME, request, NEW_ACCOUNT, false));

        assertEquals(AcmeRegistrationBinder.REGISTRATION_REJECTION, ex.getProblemDocument().getDetail());
        assertEquals(0, failedAttempts(registration), "an unknown kid reaches no registration and spends nothing");
    }

    @Test
    void secondAccountOnTheSameRegistrationIsRejectedGenerically() throws Exception {
        Certificate registration = seedRegistration();
        KeyPair first = accountKeyPair();
        acmeService
                .newAccount(PROFILE_NAME, newAccountJws(first, bindingFor(registration, first, challenge)), NEW_ACCOUNT,
                        false);
        KeyPair second = accountKeyPair();
        String request = newAccountJws(second, bindingFor(registration, second, challenge));

        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(PROFILE_NAME, request, NEW_ACCOUNT, false));

        assertEquals(AcmeRegistrationBinder.REGISTRATION_REJECTION, ex.getProblemDocument().getDetail());
        assertEquals(1, acmeAccountRepository.findAll().size());
        assertEquals(0, failedAttempts(registration), "a replay against a bound registration reaches no gate");
    }

    @Test
    void oneAccountPerRegistrationIsEnforcedByTheSchema() throws Exception {
        Certificate registration = seedRegistration();
        acmeAccountRepository.saveAndFlush(boundAccountRow(registration));
        AcmeAccount duplicate = boundAccountRow(registration);

        assertThrows(DataIntegrityViolationException.class, () -> acmeAccountRepository.saveAndFlush(duplicate));
    }

    @Test
    void termsOfServiceRequirementAdmitsOnlyTheClientThatAgreed() throws Exception {
        acmeProfile.setRequireTermsOfService(true);
        acmeProfile.setTermsOfServiceUrl("https://acme.example/terms");
        acmeProfileRepository.save(acmeProfile);
        Certificate registration = seedRegistration();
        KeyPair declined = accountKeyPair();
        String declinedRequest = newAccountJws(declined, bindingFor(registration, declined, challenge),
                List.of("mailto:device@example.com"), false);

        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(PROFILE_NAME, declinedRequest, NEW_ACCOUNT, false));
        assertEquals(Problem.USER_ACTION_REQUIRED.getType(), ex.getProblemDocument().getType());

        KeyPair agreed = accountKeyPair();
        ResponseEntity<Account> response = acmeService
                .newAccount(PROFILE_NAME, newAccountJws(agreed, bindingFor(registration, agreed, challenge),
                        List.of("mailto:device@example.com"), true), NEW_ACCOUNT, false);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void termsOfServiceRequirementWithoutATermsUrlDoesNotBlockClientsThatSendNoAgreement() throws Exception {
        acmeProfile.setRequireTermsOfService(true);
        acmeProfileRepository.save(acmeProfile);
        Certificate registration = seedRegistration();
        KeyPair keyPair = accountKeyPair();

        ResponseEntity<Account> response = acmeService
                .newAccount(PROFILE_NAME, newAccountJws(keyPair, bindingFor(registration, keyPair, challenge),
                        List.of("mailto:device@example.com"), false), NEW_ACCOUNT, false);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void omittedContactIsStoredAsAnEmptyListWhenContactIsOptional() throws Exception {
        Certificate registration = seedRegistration();
        KeyPair keyPair = accountKeyPair();

        ResponseEntity<Account> response = acmeService
                .newAccount(PROFILE_NAME,
                        newAccountJws(keyPair, bindingFor(registration, keyPair, challenge), null, true), NEW_ACCOUNT,
                        false);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(List.of(), response.getBody().getContact());
        assertEquals(List.of(), acmeAccountRepository.findAll().getFirst().mapToDto().getContact());
    }

    @Test
    void missingContactOnARequireContactProfileAnswersInvalidContact() throws Exception {
        acmeProfile.setRequireContact(true);
        acmeProfileRepository.save(acmeProfile);
        Certificate registration = seedRegistration();
        KeyPair keyPair = accountKeyPair();
        String request = newAccountJws(keyPair, bindingFor(registration, keyPair, challenge), null, true);

        AcmeProblemDocumentException ex = assertThrows(AcmeProblemDocumentException.class,
                () -> acmeService.newAccount(PROFILE_NAME, request, NEW_ACCOUNT, false));

        assertEquals(Problem.INVALID_CONTACT.getType(), ex.getProblemDocument().getType());
    }

    @Test
    void protocolDefaultProfileCreatesUnboundAccountsWithoutBinding() throws Exception {
        acmeProfile.setChallengeSource(ProtocolChallengeSource.PROTOCOL_DEFAULT);
        acmeProfileRepository.save(acmeProfile);
        KeyPair keyPair = accountKeyPair();

        ResponseEntity<Account> response = acmeService
                .newAccount(PROFILE_NAME, newAccountJws(keyPair, null), NEW_ACCOUNT, false);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNull(acmeAccountRepository.findAll().getFirst().getRegistrationCertificateUuid());
        assertFalse(acmeService
                .getDirectory(PROFILE_NAME, URI.create("http://localhost/api/acme/" + PROFILE_NAME + "/directory"),
                        false)
                .getBody()
                .getMeta()
                .getExternalAccountRequired());
    }
}
