package com.czertainly.core.service.scep;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import com.czertainly.api.model.connector.cryptography.enums.CryptographicAlgorithm;
import com.czertainly.api.model.connector.cryptography.enums.KeyFormat;
import com.czertainly.api.model.connector.cryptography.enums.KeyType;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.dao.repository.*;
import com.czertainly.core.provider.CzertainlyProvider;
import com.czertainly.core.provider.key.CzertainlyPrivateKey;
import com.czertainly.core.service.CryptographicKeyService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.shaded.org.bouncycastle.cms.CMSException;

import java.io.IOException;
import java.util.*;

@SpringBootTest
public class CryptographicProviderTest {
    @Autowired
    CryptographicOperationsApiClient cryptographicOperationsApiClient;

    @Autowired
    private CryptographicKeyService cryptographicKeyService;
    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;
    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;
    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private TokenProfileRepository tokenProfileRepository;
    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    private TokenInstanceReference tokenInstanceReference;
    private CryptographicKeyItem content;
    private CryptographicKeyItem content1;
    private TokenProfile tokenProfile;
    private Connector connector;
    private CryptographicKey key;
    private WireMockServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = new WireMockServer(3665);
        mockServer.start();

        WireMock.configureFor("localhost", mockServer.port());

        connector = new Connector();
        connector.setUrl("http://localhost:3665");
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setTokenInstanceUuid("1l");
        tokenInstanceReference.setConnector(connector);
        tokenInstanceReferenceRepository.save(tokenInstanceReference);

        tokenProfile = new TokenProfile();
        tokenProfile.setName("profile1");
        tokenProfile.setTokenInstanceReference(tokenInstanceReference);
        tokenProfile.setDescription("sample description");
        tokenProfile.setEnabled(true);
        tokenProfile.setTokenInstanceName("testInstance");
        tokenProfileRepository.save(tokenProfile);

        key = new CryptographicKey();
        key.setName("testKey");
        key.setTokenProfile(tokenProfile);
        key.setTokenInstanceReference(tokenInstanceReference);
        key.setDescription("initial description");
        cryptographicKeyRepository.save(key);

        content = new CryptographicKeyItem();
        content.setLength(1024);
        content.setCryptographicKey(key);
        content.setCryptographicKeyUuid(key.getUuid());
        content.setType(KeyType.PRIVATE_KEY);
        content.setKeyData("some/encrypted/data");
        content.setFormat(KeyFormat.PRKI);
        content.setState(KeyState.ACTIVE);
        content.setEnabled(true);
        content.setCryptographicAlgorithm(CryptographicAlgorithm.RSA);
        cryptographicKeyItemRepository.save(content);

        content1 = new CryptographicKeyItem();
        content1.setLength(1024);
        content1.setCryptographicKey(key);
        content1.setCryptographicKeyUuid(key.getUuid());
        content1.setType(KeyType.PUBLIC_KEY);
        content1.setKeyData("some/encrypted/data");
        content1.setFormat(KeyFormat.SPKI);
        content1.setState(KeyState.ACTIVE);
        content1.setEnabled(true);
        content1.setCryptographicAlgorithm(CryptographicAlgorithm.RSA);
        cryptographicKeyItemRepository.save(content1);

        content.setKeyReferenceUuid(content.getUuid());
        content1.setKeyReferenceUuid(content1.getUuid());
        cryptographicKeyItemRepository.save(content);
        cryptographicKeyItemRepository.save(content1);

        Set<CryptographicKeyItem> items = new HashSet<>();
        items.add(content1);
        items.add(content);
        key.setItems(items);
        cryptographicKeyRepository.save(key);
    }

    @AfterEach
    public void tearDown() {
        mockServer.stop();
    }
    @Test
    public void TestCmsDecrypt() throws IOException, CMSException, org.bouncycastle.cms.CMSException {
        String encapsulatedString = "MIAGCSqGSIb3DQEHA6CAMIACAQAxgcgwgcUCAQAwLjAWMRQwEgYDVQQDDAtuZXdweXRoc3ViMQIUORJlivM+pu04Au0ztZaINDDKIPUwDQYJKoZIhvcNAQEBBQAEgYAIqQOwHgive2n0HZwg+sG98zJM3jpv9p4J5fK2rnZVP4T1KJdj+GnZLziEXudUnDpgwbdKUxijN9krnJpoIm89xmrNezVJe7qZvzcvrpekT1ggpCCqZXBAf+mTVfcB0swlJAHrb3kPSCqPgf5+OMKrH/u16ybB29SbFrY2s3TLCTCABgkqhkiG9w0BBwEwFAYIKoZIhvcNAwcECFsJazuq/HhmoIAEggKg3QLMtXE9/Y/GD9S9eHPIWprG0ej9dFU3+VSGDY5hfAgIGZbq4waToZ8ugnkOybkHE5mwlxbCLRRqAKQvBEbx1xq0oynA+bXdv4EJSTY71PHE04InXtJHKu14MCfsUmbuAVcfyP5SlveeBZ5OJZsvsuB2ayvtqNkOLdwrGIRDstBhCrhGAM3bp0I/2wK/VgJEksBCPezgMGpnwo/17sOyVbNg5BGW2uoi8FoctQpdzzVWjFUqdUBgNT4KhWdbWZO6+UAxBueQWjc/vmK1ht7h66FlEgkWvCcOFDV5T36LkRmC4w7Wmt2TEVlpYce6qQTDJlriaSUhpYL89z2W8TWi+08jOweHKOI3Pz6AswexD2XHEM6IN95xklOLBD7L+v9254eKNeRP7TpTbWB/VsVn6Fu8BPdq0HoU9bt9sgnCTiha6lXbBpWENJyYdyavaMcXL15TS0EsxIRy5zj4e73w+qwQJtBXo/kspgkFZcFfA1raFTM3uFStkGshhjrLecAi0pfookoCqnUiFt8ReNu1Jhl7yIQzldZL/oiFfohXM5v2xGgnwNDm5bOJPhDzbLC/zx6kEcpuIeXMFvBArUSTftJU9ktyZi8pRT9+dlI5oLIr6aXTCS5Tx+BVkWBcjHNaSqeOgk4FP1FitohDLt/1wCeV1sLraN8rE8HIm+fDH+9A+u0OUho5j6XJ+mFVamBJaxCohMvq+a30Bq6ngvPfzBUZQwr6Q4wNDAzOFncRAzl7ITjDCaUDuuTg4rr4lg7IFSHs9MkLvtJIZ5v8im0rgs/kaoIUIFaKH8uVC5er953ERKqU3uKoJUS19yJ9bZ0V3JKT0IMU1PT4IlzygFqy4w/LrV/hMJiEVJhQ08kC2ncs3Lx9FA8GOti3a5wL62U3AAAAAAAAAAAAAA==";
        byte[] cmsDataStream = Base64.getDecoder().decode(encapsulatedString);

        CzertainlyPrivateKey privateKey = new CzertainlyPrivateKey(tokenInstanceReference.getTokenInstanceUuid(), key.getUuid().toString(), connector.mapToDto());
        CzertainlyProvider czertainlyProvider = CzertainlyProvider.getInstance("Test", true, cryptographicOperationsApiClient);

        CMSEnvelopedData envelopedData = new CMSEnvelopedData(cmsDataStream);
        RecipientInformationStore recipientInfos = envelopedData.getRecipientInfos();
        Collection<RecipientInformation> recipients = recipientInfos.getRecipients();
        Iterator<RecipientInformation> recipientInformationIterator = recipients.iterator();

        if (recipientInformationIterator.hasNext()) {
            RecipientInformation recipient = recipientInformationIterator.next();
            org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient jceKeyTransEnvelopedRecipient = new org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient(privateKey);
            jceKeyTransEnvelopedRecipient.setProvider(czertainlyProvider);
            jceKeyTransEnvelopedRecipient.setContentProvider(org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME);
            jceKeyTransEnvelopedRecipient.setMustProduceEncodableUnwrappedKey(true);
            byte[] decryptedBytes = recipient.getContent(jceKeyTransEnvelopedRecipient);
        }
    }
}
