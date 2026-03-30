package com.czertainly.core.listener;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.MessageHandlingException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.approval.ApprovalStatusEnum;
import com.czertainly.api.model.connector.secrets.content.KeyStoreSecretContent;
import com.czertainly.api.model.connector.secrets.content.KeyStoreType;
import com.czertainly.api.model.connector.secrets.content.SecretContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.secret.SecretRequestDto;
import com.czertainly.api.model.core.secret.SecretUpdateObjectsDto;
import com.czertainly.api.model.core.secret.SecretUpdateRequestDto;
import com.czertainly.core.messaging.listeners.ActionListener;
import com.czertainly.core.messaging.model.ActionMessage;
import com.czertainly.core.messaging.model.SecretActionData;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.service.SecretService;
import com.czertainly.core.util.AuthHelper;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.SecretEncodingVersion;
import com.czertainly.core.util.SecretsUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

class ActionListenerTest extends BaseSpringBootTest {

    @Autowired
    private ActionListener listener;

    @MockitoBean
    private AuthHelper authHelper;

    @MockitoBean
    private SecretService secretService;

    @Test
    void testSecretActionListener() throws JsonProcessingException, ConnectorException, NotFoundException, AttributeException {
        Mockito.doNothing().when(authHelper).authenticateAsUser(Mockito.any());
        ActionMessage actionMessage = new ActionMessage();
        actionMessage.setResource(Resource.SECRET);
        actionMessage.setResourceUuid(UUID.randomUUID());
        actionMessage.setUserUuid(UUID.randomUUID());
        actionMessage.setApprovalUuid(UUID.randomUUID());
        actionMessage.setApprovalStatus(ApprovalStatusEnum.APPROVED);
        actionMessage.setResourceAction(ResourceAction.CREATE);
        KeyStoreSecretContent content = new KeyStoreSecretContent();
        content.setContent("content");
        content.setPassword("");
        content.setKeyStoreType(KeyStoreType.PKCS12);
        String encryptedContent = SecretsUtil.encryptAndEncodeSecretString(new ObjectMapper().writeValueAsString(content), SecretEncodingVersion.V1);
        SecretActionData actionData = SecretActionData.builder()
                .encryptedContent(encryptedContent)
                .name("name")
                .attributes(List.of())
                .updatedSourceVaultProfileUuid(UUID.randomUUID())
                .build();
        actionMessage.setData(actionData);
        SecretRequestDto secretRequestDto = new SecretRequestDto();
        secretRequestDto.setName(actionData.name());
        secretRequestDto.setSecret(content);
        secretRequestDto.setAttributes(List.of());
        Mockito.doNothing().when(secretService).createSecretAction(actionMessage.getResourceUuid(), secretRequestDto, true);
        Assertions.assertDoesNotThrow(() -> listener.processMessage(actionMessage));

        actionMessage.setResourceAction(ResourceAction.UPDATE);
        SecretUpdateRequestDto secretUpdateRequestDto = new SecretUpdateRequestDto();
        secretUpdateRequestDto.setSecret(content);
        secretUpdateRequestDto.setAttributes(List.of());
        Mockito.doNothing().when(secretService).updateSecretAction(actionMessage.getResourceUuid(), secretUpdateRequestDto, true);
        Assertions.assertDoesNotThrow(() -> listener.processMessage(actionMessage));

        actionMessage.setResourceAction(ResourceAction.DELETE);
        Mockito.doNothing().when(secretService).deleteSecretAction(actionMessage.getResourceUuid(), true);
        Assertions.assertDoesNotThrow(() -> listener.processMessage(actionMessage));

        actionMessage.setResourceAction(ResourceAction.UPDATE_SOURCE_VAULT_PROFILE);
        SecretUpdateObjectsDto secretUpdateObjectsDto = new SecretUpdateObjectsDto();
        secretUpdateObjectsDto.setSourceVaultProfileUuid(actionData.updatedSourceVaultProfileUuid());
        secretUpdateObjectsDto.setSecretAttributes(List.of());
        Mockito.doNothing().when(secretService).updateSourceVaultProfile(secretUpdateObjectsDto, actionMessage.getResourceUuid(), true);
        Assertions.assertDoesNotThrow(() -> listener.processMessage(actionMessage));

        actionMessage.setResourceAction(ResourceAction.ANY);
        Assertions.assertDoesNotThrow(() -> listener.processMessage(actionMessage));

        actionMessage.setApprovalStatus(ApprovalStatusEnum.REJECTED);
        Assertions.assertDoesNotThrow(() -> listener.processMessage(actionMessage));

        actionMessage.setResourceAction(ResourceAction.CREATE);
        Mockito.doNothing().when(secretService).handleSecretCreationRejected(actionMessage.getResourceUuid());
        Assertions.assertDoesNotThrow(() -> listener.processMessage(actionMessage));
    }
}
