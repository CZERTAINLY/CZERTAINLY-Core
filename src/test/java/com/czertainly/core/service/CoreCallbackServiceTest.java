package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.v2.content.ObjectAttributeContentV2;
import com.czertainly.core.dao.entity.Credential;
import com.czertainly.core.dao.repository.CredentialRepository;
import com.czertainly.core.service.impl.CoreCallbackServiceImpl;
import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
@Transactional
@Rollback
public class CoreCallbackServiceTest extends BaseSpringBootTest {

    @Autowired
    private CoreCallbackService coreCallbackService;

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    public void testCoreGetCredentials() throws NotFoundException {
        Credential credential = new Credential();
        credential.setKind("certificate");
        credential.setEnabled(true);
        credential = credentialRepository.save(credential);

        RequestAttributeCallback callback = new RequestAttributeCallback();
        callback.setPathVariable(Map.ofEntries(Map.entry(CoreCallbackServiceImpl.CREDENTIAL_KIND_PATH_VARIABLE, credential.getKind())));

        List<ObjectAttributeContentV2> credentials = coreCallbackService.coreGetCredentials(callback);
        Assertions.assertNotNull(credentials);
        Assertions.assertFalse(credentials.isEmpty());
        Assertions.assertEquals(credential.getUuid().toString(), ((NameAndUuidDto) credentials.get(0).getData()).getUuid());
    }


    @Test
    public void testCoreGetCredentialsUnknown() throws NotFoundException {
        Credential credential = new Credential();
        credential.setKind("certificate");
        credential.setEnabled(true);
        credentialRepository.save(credential);

        RequestAttributeCallback callback = new RequestAttributeCallback();
        callback.setPathVariable(Map.ofEntries(Map.entry(CoreCallbackServiceImpl.CREDENTIAL_KIND_PATH_VARIABLE, "unknown")));

        Assertions.assertTrue(coreCallbackService.coreGetCredentials(callback).isEmpty());
    }

    @Test
    public void testCoreGetCredentials_validationFail() {
        Assertions.assertThrows(ValidationException.class, () -> coreCallbackService.coreGetCredentials(new RequestAttributeCallback()));
    }

    @Test
    public void testCoreGetCredentials_notFound() {
        RequestAttributeCallback callback = new RequestAttributeCallback();
        callback.setPathVariable(Map.ofEntries(Map.entry(CoreCallbackServiceImpl.CREDENTIAL_KIND_PATH_VARIABLE, "")));

        Assertions.assertTrue(coreCallbackService.coreGetCredentials(callback).isEmpty());
    }
}
