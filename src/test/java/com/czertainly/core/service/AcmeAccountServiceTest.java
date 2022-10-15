package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.acme.AcmeAccountListResponseDto;
import com.czertainly.api.model.client.acme.AcmeAccountResponseDto;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.cert.CertificateException;
import java.util.List;

public class AcmeAccountServiceTest extends BaseSpringBootTest {

    private static final String ADMIN_NAME = "ACME_USER";

    private static final String RA_PROFILE_NAME = "testRaProfile1";
    private static final String CLIENT_NAME = "testClient1";

    @Autowired
    private com.czertainly.core.service.RaProfileService raProfileService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;

    private RaProfile raProfile;
    private Certificate certificate;
    private CertificateContent certificateContent;
    private AuthorityInstanceReference authorityInstanceReference;
    private Connector connector;

    @Autowired
    private AcmeAccountService acmeAccountService;

    @Autowired
    private AcmeAccountRepository acmeAccountRepository;

    @Autowired
    private AcmeProfileService acmeProfileService;

    @Autowired
    private AcmeProfileRepository acmeProfileRepository;

    private AcmeProfile acmeProfile;

    private AcmeAccount acmeAccount;

    @BeforeEach
    public void setUp() {

        certificateContent = new CertificateContent();
        certificateContent = certificateContentRepository.save(certificateContent);

        certificate = new Certificate();
        certificate.setCertificateContent(certificateContent);
        certificate.setSerialNumber("123456789");
        certificate = certificateRepository.save(certificate);

        connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector = connectorRepository.save(connector);

        authorityInstanceReference = new AuthorityInstanceReference();
        authorityInstanceReference.setAuthorityInstanceUuid("1l");
        authorityInstanceReference.setConnector(connector);
        authorityInstanceReference = authorityInstanceReferenceRepository.save(authorityInstanceReference);

        raProfile = new RaProfile();
        raProfile.setName(RA_PROFILE_NAME);
        raProfile.setAuthorityInstanceReference(authorityInstanceReference);
        raProfile = raProfileRepository.save(raProfile);

        acmeProfile = new AcmeProfile();
        acmeProfile.setWebsite("sample website");
        acmeProfile.setTermsOfServiceUrl("sample terms");
        acmeProfile.setValidity(30);
        acmeProfile.setRetryInterval(30);
        acmeProfile.setDescription("sample description");
        acmeProfile.setName("sameName");
        acmeProfile.setDnsResolverPort("53");
        acmeProfile.setDnsResolverIp("localhost");
        acmeProfile.setTermsOfServiceChangeUrl("change url");
        acmeProfileRepository.save(acmeProfile);


        acmeAccount = new AcmeAccount();
        acmeAccount.setStatus(AccountStatus.VALID);
        acmeAccount.setEnabled(true);
        acmeAccount.setAccountId("D65fAtrgfAD");
        acmeAccount.setTermsOfServiceAgreed(true);
        acmeAccount.setAcmeProfile(acmeProfile);
        acmeAccount.setRaProfile(raProfile);
        acmeAccountRepository.save(acmeAccount);
    }

    @Test
    public void testListAccounts() {
        List<AcmeAccountListResponseDto> accounts = acmeAccountService.listAcmeAccounts(SecurityFilter.create());
        Assertions.assertNotNull(accounts);
        Assertions.assertFalse(accounts.isEmpty());
        Assertions.assertEquals(1, accounts.size());
        Assertions.assertEquals(acmeAccount.getAccountId(), accounts.get(0).getAccountId());
    }

    @Test
    public void testGetAccountById() throws NotFoundException {
        AcmeAccountResponseDto dto = acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid());
        Assertions.assertNotNull(dto);
        Assertions.assertEquals(acmeAccount.getAccountId(), dto.getAccountId());
        Assertions.assertNotNull(acmeAccount.getUuid());
    }

    @Test
    public void testGetAccountById_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testRemoveAccount() throws NotFoundException {
        acmeAccountService.revokeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid());
        Assertions.assertEquals(AccountStatus.REVOKED, acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid()).getStatus());
    }

    @Test
    public void testRemoveAccount_notFound() {
        Assertions.assertThrows(
                NotFoundException.class,
                () -> acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002"))
        );
    }

    @Test
    public void testEnableAccount() throws NotFoundException, CertificateException {
        acmeAccountService.enableAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid());
        Assertions.assertEquals(true, acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid()).isEnabled());
    }

    @Test
    public void testEnableAccount_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeAccountService.enableAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testDisableAccount() throws NotFoundException {
        acmeAccountService.disableAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid());
        Assertions.assertEquals(false, acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid()).isEnabled());
    }

    @Test
    public void testDisableAccount_notFound() {
        Assertions.assertThrows(NotFoundException.class, () -> acmeAccountService.disableAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), SecuredUUID.fromString("abfbc322-29e1-11ed-a261-0242ac120002")));
    }

    @Test
    public void testBulkRemove() throws NotFoundException {
        acmeAccountService.bulkRevokeAccount(List.of(acmeAccount.getSecuredUuid()));
        Assertions.assertEquals(AccountStatus.REVOKED, acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid()).getStatus());
    }

    @Test
    public void testBulkEnable() throws NotFoundException {
        acmeAccountService.bulkEnableAccount(List.of(acmeAccount.getSecuredUuid()));
        Assertions.assertEquals(true, acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid()).isEnabled());
    }

    @Test
    public void testBulkDisable() throws NotFoundException {
        acmeAccountService.bulkDisableAccount(List.of(acmeAccount.getSecuredUuid()));
        Assertions.assertEquals(false, acmeAccountService.getAcmeAccount(acmeAccount.getAcmeProfile().getSecuredParentUuid(), acmeAccount.getSecuredUuid()).isEnabled());
    }
}
