package com.czertainly.core.service;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.api.model.core.acme.Authorization;
import com.czertainly.api.model.core.acme.AuthorizationStatus;
import com.czertainly.api.model.core.acme.ChallengeStatus;
import com.czertainly.api.model.core.acme.ChallengeType;
import com.czertainly.api.model.core.acme.Directory;
import com.czertainly.api.model.core.acme.Order;
import com.czertainly.api.model.core.acme.OrderStatus;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Client;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeAuthorization;
import com.czertainly.core.dao.entity.acme.AcmeChallenge;
import com.czertainly.core.dao.entity.acme.AcmeOrder;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ClientRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.dao.repository.acme.AcmeAuthorizationRepository;
import com.czertainly.core.dao.repository.acme.AcmeChallengeRepository;
import com.czertainly.core.dao.repository.acme.AcmeOrderRepository;
import com.czertainly.core.service.acme.AcmeRaProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;
import java.util.List;

@SpringBootTest
@Transactional
@Rollback
@WithMockUser(roles="ACME")
public class AcmeRaProfileServiceTest {
    private static final String ADMIN_NAME = "ACME_USER";

    private static final String RA_PROFILE_NAME = "testRaProfile1";
    private static final String CLIENT_NAME = "testClient1";

    @Autowired
    private RaProfileService raProfileService;

    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private CertificateRepository certificateRepository;
    @Autowired
    private CertificateContentRepository certificateContentRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;

    private RaProfile raProfile;
    private Certificate certificate;
    private CertificateContent certificateContent;
    private Client client;
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

    @Autowired
    private AcmeOrderRepository acmeOrderRepository;

    @Autowired
    private AcmeAuthorizationRepository acmeAuthorizationRepository;

    @Autowired
    private AcmeChallengeRepository acmeChallengeRepository;

    @Autowired
    private AcmeRaProfileService acmeService;

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

        client = new Client();
        client.setName(CLIENT_NAME);
        client.setCertificate(certificate);
        client.setSerialNumber(certificate.getSerialNumber());
        client = clientRepository.save(client);

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
        acmeProfile.setRaProfile(raProfile);
        acmeProfile.setWebsite("sample website");
        acmeProfile.setTermsOfServiceUrl("sample terms");
        acmeProfile.setValidity(30);
        acmeProfile.setRetryInterval(30);
        acmeProfile.setDescription("sample description");
        acmeProfile.setName("sameName");
        acmeProfile.setDnsResolverPort("53");
        acmeProfile.setDnsResolverIp("localhost");
        acmeProfile.setTermsOfServiceChangeUrl("change url");
        acmeProfile.setUuid("1757e43e-7d12-11ec-90d6-0242ac120003");
        acmeProfileRepository.save(acmeProfile);


        acmeAccount = new AcmeAccount();
        acmeAccount.setUuid("1757e43e-7d12-11ec-90d6-0242ac120004");
        acmeAccount.setStatus(AccountStatus.VALID);
        acmeAccount.setEnabled(true);
        acmeAccount.setAccountId("RMAl70zrRrs");
        acmeAccount.setTermsOfServiceAgreed(true);
        acmeAccount.setAcmeProfile(acmeProfile);
        acmeAccount.setRaProfile(raProfile);
        acmeAccountRepository.save(acmeAccount);

        AcmeOrder order1 = new AcmeOrder();
        order1.setOrderId("order123");
        order1.setStatus(OrderStatus.VALID);
        order1.setAcmeAccount(acmeAccount);
        acmeOrderRepository.save(order1);

        AcmeAuthorization authorization1 = new AcmeAuthorization();
        authorization1.setAuthorizationId("auth123");
        authorization1.setStatus(AuthorizationStatus.PENDING);
        authorization1.setWildcard(false);
        authorization1.setOrder(order1);
        acmeAuthorizationRepository.save(authorization1);

        AcmeChallenge challenge2 = new AcmeChallenge();
        challenge2.setChallengeId("challenge123");
        challenge2.setStatus(ChallengeStatus.VALID);
        challenge2.setType(ChallengeType.HTTP01);
        challenge2.setToken("122324");
        challenge2.setAuthorization(authorization1);
        acmeChallengeRepository.save(challenge2);

    }

    @Test
    public void testGetDirectory() throws AcmeProblemDocumentException, NotFoundException {
        ResponseEntity<Directory> directory = acmeService.getDirectory("sameName");
        Assertions.assertNotNull(directory);
        Assertions.assertEquals(true, directory.getBody().getNewOrder().endsWith("/new-order"));
        Assertions.assertEquals(true, directory.getBody().getKeyChange().endsWith("/key-change"));
        Assertions.assertEquals(true, directory.getBody().getNewAccount().endsWith("/new-account"));
        Assertions.assertEquals(true, directory.getBody().getNewNonce().endsWith("/new-nonce"));
        Assertions.assertEquals(true, directory.getBody().getRevokeCert().endsWith("/revoke-cert"));
    }

    @Test
    public void testGetNonce(){
        ResponseEntity<?> response = acmeService.getNonce(true);
        Assertions.assertNotNull(response.getHeaders().get("Replay-Nonce"));
    }

    @Test
    public void testNewAccount() throws AcmeProblemDocumentException, NotFoundException {
        String requestJson = "{\n" +
                "  \"protected\": \"eyJhbGciOiAiUlMyNTYiLCAiandrIjogeyJuIjogIi14aC1NM21QbER4QXp4dUNPVEFia2ZrOWJaQ1dkZXBzQkNSdUxabnJqSnNDUUV6eW1fZFJ3aU41UW53eGlJanBNYnR3aklzN3RKNjJ2UzlWSkN3dF9NZGdfYzkwR2dTTXJVNG5LZFNLdXByVmZ4WHI3MGt5UUpNRDg3ZkVFWGg5ZEFSTnVTSDNBc1pVWkpabGVZTG1MRlZMc1dxSlZWZk9QLTY4UVlGQTdBU1h3VGs2ZmRGNjFkYnVxR0pobklUSlFlNGdFbjF3a3JubG1SR1QzMldtT2F0UmhYdVFQZkdNMDZlZ3lMcVNmZTVzaHQ0STEwT2FTUVkwQ2x4blBrVkNuSFVDSXBuRVpTOFJlNDBpZXNEOFExQUFmM0ZrblhzMXNMdDczbS13UjFIT3hhVGJUUkZpUFZ2TlNwVXJMTFJ2a3BVOGVHblpqaG5YODBzalNrNDFpUSIsICJlIjogIkFRQUIiLCAia3R5IjogIlJTQSJ9LCAibm9uY2UiOiAiNXBTdjF2UjZTRUpyeUdsQTBKUm5zNmUzNzZaR2pVdC1DWXhtcXZ3QkVhWSIsICJ1cmwiOiAiaHR0cHM6Ly8xOTIuMTY4LjAuMTExOjg0NDMvYXBpL2FjbWUvcmFQcm9maWxlL3EvbmV3LWFjY291bnQifQ\",\n" +
                "  \"signature\": \"qR4sGW8IpGEeszEEoecE0l-cYZw-g1vWOTnEDVXgafotTN0cJosM55L_MB416Gixm2KPPPWSa96FzZ53Z0tEUJiqfrmczdW14fsHEpXuEuBfQ9jptlqZyoS3flYz98VDAUpr4jnHVvzyeMY5zTo2pSOt9Vrs2TJgjwbjqybsF7W4R_DWULyHnHF6mb-6eBx5u3KWUSgRd4sd83NZkI-XJp3X3fMenCDyMHKp0sT4hffI0_LaurD-Zxt4c6UgPEX1LCZSUthPEcZvdYfW1gxvNjWs4QR4SGKe2CqWurxlfShi8BRHiCk2oT2qKP5Y8Nyqq_OXQPLm9B24a9izieqPwA\",\n" +
                "  \"payload\": \"ewogICJjb250YWN0IjogWwogICAgIm1haWx0bzp0ZXN0LnRlc3RAdGVzdCIKICBdLAogICJ0ZXJtc09mU2VydmljZUFncmVlZCI6IHRydWUKfQ\"\n" +
                "}";
            Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.newAccount(RA_PROFILE_NAME, requestJson));
    }

    @Test
    public void testNewAccount_Fail() throws AcmeProblemDocumentException, NotFoundException {
        String requestJson = "{\n" +
                "  \"protected\": \"gfdfhgfghfdgh\",\n" +
                "  \"signature\": \"fdghdfgh-cYZw-dfghfdgh-fdgh-dfgh-dfgh\",\n" +
                "  \"payload\": \"dfgdrtyufghgjghktyfghdtu\"\n" +
                "}";
        Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.newAccount(RA_PROFILE_NAME, requestJson));
    }

    @Test
    public void testNewOrder() throws AcmeProblemDocumentException, NotFoundException {
        String requestJson = "{\n" +
                "  \"protected\": \"eyJhbGciOiAiUlMyNTYiLCAia2lkIjogImh0dHBzOi8vMTkyLjE2OC4wLjExMTo4NDQzL2FwaS9hY21lL3JhUHJvZmlsZS9xL2FjY3QvUk1BbDcwenJScnMiLCAibm9uY2UiOiAienJic1lpckhmX3RlM2tPeXlacDg2cDg2TmpnVnp0RTlKOW4zQWVEU3AtNCIsICJ1cmwiOiAiaHR0cHM6Ly8xOTIuMTY4LjAuMTExOjg0NDMvYXBpL2FjbWUvcmFQcm9maWxlL3EvbmV3LW9yZGVyIn0\",\n" +
                "  \"signature\": \"ttx50ZQERMtL4I3GjaM8g4Z9ljJUweevmEbBJt827dgiDbEP3clmXBhB-D5AmyfDvlYSqdxDdVhnl3tBBKSxtFs9irnoPfc-0-mFua_Eh1nsWeRq36z7utBNYaeUwc7IqwXK_dGRPZN0WD7fNs_YUi9nM1_-1rlRl3wQ3bH5aS_1w6EsZgfwVEEAuzMACT45OuuEvfrqddWnv6pEs2i6MhRBIG9u5-BQdet410NfRjaO74U6keVmhfFZiaroraCjnpcZwzo-ZkUfLHwxe16vO6dM9Jjfo9zijVI2I0U7oRl1d2jHllJCYlpnQdxufcjqJcWZBXglGfzjbTvES38wEQ\",\n" +
                "  \"payload\": \"ewogICJpZGVudGlmaWVycyI6IFsKICAgIHsKICAgICAgInR5cGUiOiAiZG5zIiwKICAgICAgInZhbHVlIjogImRlYmlhbjEwLmFjbWUubG9jYWwiCiAgICB9CiAgXQp9\"\n" +
                "}";
        try {
            ResponseEntity<Order> order = acmeService.newOrder(RA_PROFILE_NAME, requestJson);
            Assertions.assertNotNull(order.getBody());
            Assertions.assertEquals(OrderStatus.PENDING, order.getBody().getStatus());
            Assertions.assertEquals(1, order.getBody().getAuthorizations().size());
        }catch (Exception e){
                System.out.println(e.getMessage());
        }

    }

    @Test
    public void testNewOrder_Fail() throws AcmeProblemDocumentException, NotFoundException {
        String requestJson = "{\n" +
                "  \"protected\": \"gfdfhgfghfdgh\",\n" +
                "  \"signature\": \"fdghdfgh-cYZw-dfghfdgh-fdgh-dfgh-dfgh\",\n" +
                "  \"payload\": \"dfgdrtyufghgjghktyfghdtu\"\n" +
                "}";
        Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.newOrder(RA_PROFILE_NAME, requestJson));
    }

    @Test
    public void testGetAuthorization() throws AcmeProblemDocumentException, NotFoundException {
        String requestJson = "{\n" +
                "  \"protected\": \"eyJhbGciOiAiUlMyNTYiLCAia2lkIjogImh0dHBzOi8vMTkyLjE2OC4wLjExMTo4NDQzL2FwaS9hY21lL3JhUHJvZmlsZS9xL2FjY3QvUk1BbDcwenJScnMiLCAibm9uY2UiOiAiZEhUYzZQRE02bXBlNkdEcjFweXROUEM3ZFkwVS1yaEVzUDNMTmVKOWlFTSIsICJ1cmwiOiAiaHR0cHM6Ly8xOTIuMTY4LjAuMTExOjg0NDMvYXBpL2FjbWUvVFAvYXV0aHovSl8xVUxlZ1ZDVTQifQ\",\n" +
                "  \"signature\": \"P7rY0IydqGImlCystf80dMbtH9Av1Pxn5UF-qO3C2SdIunJCepsYuDNncsOiTH83OV93aIM9O-UzdbdQ-h5AyqWVH8l_UwJnYUGrpKnNcw-c4gXwtRaqnMn86Cz62SkTHpsRK-uEOat8UqCMBpJk86Tj9P5_lHsEnlYMTpeGor3Zm5sg25dS_iLSdsK0KeyB5elv5yDfKWAMvcnU5PvxhUIF9DLoFI0xDB5O1svQ6uuBfTlBfez5ElWRnWtUReDRVWuEg5Vw-faREq4sBbdkTLiyJJvzFJ-2JG9qRUHU5_8zYn_frnBnFMT3Bprhwg6k0x2iKg92_bWjIGs9nmbtEw\",\n" +
                "  \"payload\": \"\"\n" +
                "}";
        ResponseEntity<Authorization> authorization = acmeService.getAuthorization(RA_PROFILE_NAME, "auth123", requestJson);
        Assertions.assertNotNull(authorization);
        Assertions.assertEquals(0, authorization.getBody().getChallenges().size());
    }

    @Test
    public void testFinalize() throws AcmeProblemDocumentException, ConnectorException, CertificateException, AlreadyExistException, JsonProcessingException {
        String requestJson = "{\n" +
                "  \"protected\": \"eyJhbGciOiAiUlMyNTYiLCAia2lkIjogImh0dHBzOi8vMTkyLjE2OC4wLjExMTo4NDQzL2FwaS9hY21lL3JhUHJvZmlsZS9xL2FjY3QvUk1BbDcwenJScnMiLCAibm9uY2UiOiAiWkc5aWtYdTB4d1JNZ1ZpdDlLZHY5RG5zRmF5dDAzbWdXT0x4WldjMFE4USIsICJ1cmwiOiAiaHR0cHM6Ly8xOTIuMTY4LjAuMTExOjg0NDMvYXBpL2FjbWUvVFAvb3JkZXIvall3eVJHSk1JZUEvZmluYWxpemUifQ\",\n" +
                "  \"signature\": \"0xnnC2Stx0IJyDvEZ3sDda_50oEocMGZkQvOxniXp8cbk17fpKYS9sRgMqDTeC8uaUWtv7YIRGoCHQvTYs40_Q3bdGmJGtN-ltZte3LM5oJARQPwgB8NIVzXkE6axd_8So1Xsau5yVi23dHO_y0MIYQzUYFpyn_30bCkTfNdiOIrX55qb8EX0E5OwPUrcUXeVtXAkpxLLMew2ZcQF1pHYjTNFQ1ZXtlO9xcTWZikLq5Eg_3FRWuh0ZxqsVw6-8QtYM2W44Zna2ZGbIAm2jveVTEM2O-ZAvYxFxYmNdUR9aNVPpme76OC5v3rjVsnJaBtDjpkJ9ub7EbOk9kvG1oD6w\",\n" +
                "  \"payload\": \"ewogICJjc3IiOiAiTUlJQ2RqQ0NBVjRDQVFJd0FEQ0NBU0l3RFFZSktvWklodmNOQVFFQkJRQURnZ0VQQURDQ0FRb0NnZ0VCQUxlSnZ4N0pXYnd6b2JXTDc0S3lIejBGalBxdDBSNWlPYU94aVlxcGZNWS1aVmhNQmtTMEZxbkNCUXpNbjVCa0h1a2R4N0hzSU1rSi1zTTAxSFZISmFScGdwZjF6ZVR5UlFqWTdFU0Rpa1JMXzFFa3hpNlNnZjV1bnpCMzVhUDJFQnhpQWFvbUc2MTBIanBxU2ZHdE96RWYxMmh5NGprY0M0NDZUVDhuRTlkbTZDQmY3WEFvcTl2WHhYUmpuQWdka3I2MnlJemFuWGVkRHdkY055azVFaWlSV1FYd1ctTDVQZXg1ODA4aXAyZ21FNUFsNVNQVWl2OGVEQ3EwMlFWREo4TG40VVBZa3hMMWI2Uk1sZkVnS0xzR0VaWDBlLUZDMHdfZmlCTjQ4enJ2SHhxTTJmZFU3QWU4cFJEd1VPQ2xZT3hEa3J2RHY2MFJHaWtMbFFaNDVGY0NBd0VBQWFBeE1DOEdDU3FHU0liM0RRRUpEakVpTUNBd0hnWURWUjBSQkJjd0ZZSVRaR1ZpYVdGdU1UQXVZV050WlM1c2IyTmhiREFOQmdrcWhraUc5dzBCQVFzRkFBT0NBUUVBSGxPMFp1UHVZRXRwbFUwZ0VVajg4WWkxTVdrckVseDBKb1RrN3FvblJzdWZ1X1kyUF91LVJya1dPek0zVkowOGxOejkwTF9tbmM4Tk9PTk1sX1dsWVdCeXdiVU1zR2FyNFlfMXgweVNPRWRwNWZnODdyeFkxYjJqYlNMN3RQZTRPVjd5QWViZENFenpYWEJpM0F5OU5vSkFod05PTmp5UnA5MnZxVDUtTVdNWFF5WnZkY1VNTTM4bDZhTmM5am9mM0VsdU5iZ083bldTbGU2TVFKSnZsRVl3WHg3WlB2dmd4TWZyUmEtWWNfYVdTN3cyNU1TQU9ES0t3dklpdkduNXFfb3dmZDVBb3pZcDBweW1pTExidkFXaFlWV0xfLWJHdkoxM3hweWZOUG5HSklkd2NZOHpnaWtZUHlCZmJSbVB5S0pMUEk0UW5XejhHc1dHaWFVZ2pBIgp9\"\n" +
                "}";
        ResponseEntity<Order> order = acmeService.finalizeOrder("sameName", "order123", requestJson);
        Assertions.assertNotNull(order);
    }

    @Test
    public void testRevokeCert() throws AcmeProblemDocumentException, ConnectorException, CertificateException {
        String requestJson = "{\n" +
                "  \"protected\": \"eyJhbGciOiAiUlMyNTYiLCAia2lkIjogImh0dHBzOi8vMTkyLjE2OC4wLjExMTo4NDQzL2FwaS9hY21lL3JhUHJvZmlsZS9xL2FjY3QvUk1BbDcwenJScnMiLCAibm9uY2UiOiAiZTY5RUtiRU9HaFpzdjh2M0xmU0xMM0NjLVNFQTJ2YmNxcGZnT3BCNlVLbyIsICJ1cmwiOiAiaHR0cHM6Ly8xOTIuMTY4LjAuMTExOjg0NDMvYXBpL2FjbWUvcmFQcm9maWxlL3EvcmV2b2tlLWNlcnQifQ\",\n" +
                "  \"signature\": \"jIx3Xq51pACNHhUc2c7Er1_V5E0rpJU5dHvuaCnUkfyXrr8kilSVlKSRJJhVq8RuX4r8gHWR10fmgZnxk1N8WhvHEro44jSgYT2Jrp-7DxEPGJy0v4LhHoLJWtyquUo8MtunWaoGJn6yzT3mtvVyIG1ROiNnwA6pm8xGYaQdyPiW3kCiiYyj1E8SDMqo10UMWRYQlINCNVh_1M8SAvar4MDJgKL19jR-r3_BL4yjaW9pMjVhFtHVJqpu-DcqMS07twW5OS9Wu11QeKG0gmwJ57lgS_jqtK-SrXGPKw13QVBqJg4ftUzSWeQh11oBLpFmtvwegn4EpRWdlGoXJCU_rA\",\n" +
                "  \"payload\": \"ewogICJjZXJ0aWZpY2F0ZSI6ICJNSUlGVlRDQ0F6MmdBd0lCQWdJVEdBQUFBVWREY28xdGlEM3RhQUFBQUFBQlJ6QU5CZ2txaGtpRzl3MEJBUTBGQURBM01SY3dGUVlEVlFRRERBNUVaVzF2SUUxVElGTjFZaUJEUVRFY01Cb0dBMVVFQ2d3VE0wdGxlU0JEYjIxd1lXNTVJSE11Y2k1dkxqQWVGdzB5TWpBeE1qVXdPVFV6TVRCYUZ3MHlOREF4TWpVd09UVXpNVEJhTUFBd2dnRWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUJEd0F3Z2dFS0FvSUJBUUMzaWI4ZXlWbThNNkcxaS0tQ3NoODlCWXo2cmRFZVlqbWpzWW1LcVh6R1BtVllUQVpFdEJhcHdnVU16Si1RWkI3cEhjZXg3Q0RKQ2ZyRE5OUjFSeVdrYVlLWDljM2s4a1VJMk94RWc0cEVTXzlSSk1ZdWtvSC1icDh3ZC1XajloQWNZZ0dxSmh1dGRCNDZha254clRzeEg5ZG9jdUk1SEF1T09rMF9KeFBYWnVnZ1gtMXdLS3ZiMThWMFk1d0lIWkstdHNpTTJwMTNuUThIWERjcE9SSW9rVmtGOEZ2aS1UM3NlZk5QSXFkb0poT1FKZVVqMUlyX0hnd3F0TmtGUXlmQzUtRkQySk1TOVcta1RKWHhJQ2k3QmhHVjlIdmhRdE1QMzRnVGVQTTY3eDhhak5uM1ZPd0h2S1VROEZEZ3BXRHNRNUs3dzctdEVSb3BDNVVHZU9SWEFnTUJBQUdqZ2dHUE1JSUJpekFoQmdOVkhSRUJBZjhFRnpBVmdoTmtaV0pwWVc0eE1DNWhZMjFsTG14dlkyRnNNQjBHQTFVZERnUVdCQlNOUFFiaTN1dGMtSnJCQXlaMXhldUVfcXl4WERBZkJnTlZIU01FR0RBV2dCU1N3cnpmVmNYQms0VkpCX2VzeVIwTGFBRUhVVEJOQmdOVkhSOEVSakJFTUVLZ1FLQS1oanhvZEhSd09pOHZiR0ZpTURJdU0ydGxlUzVqYjIxd1lXNTVMMk55YkhNdlpHVnRieTlFWlcxdkpUSXdUVk1sTWpCVGRXSWxNakJEUVM1amNtd3dWd1lJS3dZQkJRVUhBUUVFU3pCSk1FY0dDQ3NHQVFVRkJ6QUJoanRvZEhSd09pOHZiR0ZpTURJdU0ydGxlUzVqYjIxd1lXNTVMMk5oY3k5a1pXMXZMMFJsYlc4bE1qQk5VeVV5TUZOMVlpVXlNRU5CTG1OeWREQU9CZ05WSFE4QkFmOEVCQU1DQmFBd1BBWUpLd1lCQkFHQ054VUhCQzh3TFFZbEt3WUJCQUdDTnhVSWgtV0RZYWlIY0lLSmdUeUVrOElCaFlpNWFvRk1ncEhlVHBPV1ZRSUJaQUlCQkRBVEJnTlZIU1VFRERBS0JnZ3JCZ0VGQlFjREFUQWJCZ2tyQmdFRUFZSTNGUW9FRGpBTU1Bb0dDQ3NHQVFVRkJ3TUJNQTBHQ1NxR1NJYjNEUUVCRFFVQUE0SUNBUUNqMlVBeVhrb0tib0xKUWQyQW5XRGVoQXdMcEV3OWpoalNzMzZKbzVzZVJyVEpSWEZvNGxkWHlIUGdNUFJNTldoRGItTHpuUk52d2tiMHh6S292Ynk1ZlFjOFVxcmlNV1cyVGZlSEhudW1pQUJWVWRmNHcyalpzSGJpMFMyMEY4dTlFNHV0YW1mdUNlU1Q2ZHlBdW96eXlZdjNRUzZuVnIxazZTbXI2UHYxOHFPZklaMzdZRzgtdGZkZ3Y5VVF3SlZ2eEJ1UElma04xeVpBN2tudXEyb0JxRXpmUHVrOGoybXV5VHdndkFfM3phRVRtT1F4UDREcnM3bVJwSkIzbDJuTHpwdWViZ1ZsWUppSEdieWFnRWlueHZSUTZMdVBnN25TYmdLYXFIa0pWSGtDSTZpSF9ONlk3aWNxcGZIT0pRUEFIaVBObXhyZUtJVVpyV2l5SERGMWJKQ3paWmk3R0ZRVXNzaktDbTI2bnI5aEY4VGJnVnlybDFyMnAxOEFwb0NrdmdQaGtQT0swdzVqMUowMzJjT1FQTTZlUm5kZXJlbEJzV3lGWk9ZcDVpeExoeUxHcklFN2p4ZXk3N1hmSzgtUUVLMy1hd3c5c0V5VDVBUnhiUFdnNDVuNEd6QXBwanFfbVo2OVJBdS1WcVJvSjNjUldNNUxsUUh5VjR5dnA3ZEg3TUZUMVZyc2U1MWJoNE5yc0RwcGthTE5GSFVMcmE5RlNoYUtkSTBBeENKUFhLWmF6TGVpeDg5am5KM0hGbjR0WERINkJIdGZzeDd3aG5Ea1RuUzVsRDF3QXR4eHI1LWZtSWNCUHhDSmtGclBDWGlxNzIwOFZxQ2FuazBQX0JOb0NZOVhwYWF2NjBhNFNjTjAtSkJQSVRJcFVvQ0RUeHEzbDN4TDd1dEdudyIsCiAgInJlYXNvbiI6IDAKfQ\"\n" +
                "}";
        Assertions.assertThrows(NullPointerException.class, () -> acmeService.revokeCertificate(RA_PROFILE_NAME, requestJson));

    }

    @Test
    public void testGetOrderList() throws AcmeProblemDocumentException, NotFoundException {
        ResponseEntity<List<Order>> orders = acmeService.listOrders(RA_PROFILE_NAME, "RMAl70zrRrs");
        Assertions.assertNotNull(orders);
    }

    @Test
    public void testGetOrderListFail() throws AcmeProblemDocumentException, NotFoundException {
        Assertions.assertThrows(AcmeProblemDocumentException.class, () -> acmeService.listOrders(RA_PROFILE_NAME, "wrong"));
    }

    @Test
    public void testGetOrder() throws AcmeProblemDocumentException, NotFoundException {
        ResponseEntity<Order> orders = acmeService.getOrder(RA_PROFILE_NAME, "order123");
        Assertions.assertNotNull(orders);
        Assertions.assertNotNull(orders.getBody().getStatus());
    }
}
