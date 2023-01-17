package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AttributeApiClient;
import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.cryptography.key.KeyRequestType;
import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.GroupAttribute;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CallbackServiceImpl implements CallbackService {

    private static final Logger logger = LoggerFactory.getLogger(CallbackServiceImpl.class);

    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private AttributeApiClient attributeApiClient;
    @Autowired
    private CoreCallbackService coreCallbackService;
    @Autowired
    private CredentialService credentialService;
    @Autowired
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    @Autowired
    private AuthorityInstanceApiClient authorityInstanceApiClient;
    @Autowired
    private AttributeService attributeService;
    @Autowired
    private CryptographicKeyService cryptographicKeyService;
    @Autowired
    private TokenInstanceService tokenInstanceService;
    @Autowired
    private TokenProfileService tokenProfileService;


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.CALLBACK)
    public Object callback(String uuid, FunctionGroupCode functionGroup, String kind, RequestAttributeCallback callback) throws ConnectorException, ValidationException {
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(uuid));
        List<BaseAttribute> definitions;
        definitions = attributeApiClient.listAttributeDefinitions(connector.mapToDto(), functionGroup, kind);
        AttributeCallback attributeCallback = getAttributeByName(callback.getName(), definitions);
        AttributeDefinitionUtils.validateCallback(attributeCallback, callback);

        if (attributeCallback.getCallbackContext().equals("core/getCredentials")) {
            return coreCallbackService.coreGetCredentials(callback);
        }

        // Load complete credential data for mapping of type credential
        credentialService.loadFullCredentialData(attributeCallback, callback);

        Object response = attributeApiClient.attributeCallback(connector.mapToDto(), attributeCallback, callback);
        if (isGroupAttribute(callback.getName(), definitions)) {
            processGroupAttributes(connector.getUuid(), response);
        }
        return response;
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.CALLBACK)
    public Object resourceCallback(Resource resource, String resourceUuid, RequestAttributeCallback callback) throws ConnectorException, ValidationException {
        List<BaseAttribute> definitions = null;
        Connector connector = null;
        switch (resource) {
            case RA_PROFILE:
                AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(
                                UUID.fromString(resourceUuid))
                        .orElseThrow(
                                () -> new NotFoundException(
                                        AuthorityInstanceReference.class,
                                        resourceUuid
                                )
                        );
                connector = authorityInstance.getConnector();
                definitions = authorityInstanceApiClient.listRAProfileAttributes(
                        connector.mapToDto(),
                        authorityInstance.getAuthorityInstanceUuid()
                );
                break;

            case CRYPTOGRAPHIC_KEY:
                connector =
                        tokenProfileService.getTokenProfileEntity(
                                SecuredUUID.fromString(
                                        resourceUuid
                                )
                        ).getTokenInstanceReference().getConnector();
                definitions = cryptographicKeyService.listCreateKeyAttributes(
                        null,
                        SecuredParentUUID.fromString(
                                resourceUuid
                        ),
                        KeyRequestType.KEY_PAIR
                );
                break;

            default:
                throw new ValidationException(
                        ValidationError.create(
                                "Callback for the requested resource is not supported"
                        )
                );
        }

        AttributeCallback attributeCallback = getAttributeByName(callback.getName(), definitions);
        AttributeDefinitionUtils.validateCallback(attributeCallback, callback);

        if (attributeCallback.getCallbackContext().equals("core/getCredentials")) {
            return coreCallbackService.coreGetCredentials(callback);
        }
        // Load complete credential data for mapping of type credential
        credentialService.loadFullCredentialData(attributeCallback, callback);

        Object response = attributeApiClient.attributeCallback(connector.mapToDto(), attributeCallback, callback);

        if (isGroupAttribute(callback.getName(), definitions)) {
            processGroupAttributes(connector.getUuid(), response);
        }
        return response;
    }


    private AttributeCallback getAttributeByName(String name, List<BaseAttribute> attributes) throws NotFoundException {
        for (BaseAttribute attributeDefinition : attributes) {
            if (attributeDefinition.getName().equals(name)) {
                switch (attributeDefinition.getType()) {
                    case DATA:
                        return ((DataAttribute) attributeDefinition).getAttributeCallback();
                    case GROUP:
                        return ((GroupAttribute) attributeDefinition).getAttributeCallback();
                }
            }
        }
        throw new NotFoundException(BaseAttribute.class, name);
    }

    /**
     * Function to check the response for callback and store the data in the database.
     */
    private void processGroupAttributes(UUID connectorUuid, Object callbackResponse) {
        // When the callback is retrieved from the connector, and of the type of the attribute triggering the
        // callback is GROUP, then the response is expected to be list of other attributes. In that case,
        // We are not able to merge the attributes since these attributes will not be available as part of the list attribute
        // response.
        // This method will take the attribute definition and store it in the database. In the other methods where there
        // group attributes, we can retrieve them and merge them with the code.
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            List<BaseAttribute> responseAttributes = mapper.convertValue(callbackResponse, mapper.getTypeFactory().constructCollectionType(List.class, BaseAttribute.class));
            for (BaseAttribute attribute : responseAttributes) {
                logger.debug("Creating reference attribute: {}", attribute);
                attributeService.createAttributeDefinition(connectorUuid, attribute);
            }
        } catch (Exception e) {
            logger.debug("Failed to create the reference attributes. Exception is {}", e.getMessage());
        }
    }

    /**
     * Function to check if the attribute is of type group
     *
     * @param name       Name of the attribute
     * @param attributes List of the attribute definitions
     * @return If the attribute is group or not
     */
    private boolean isGroupAttribute(String name, List<BaseAttribute> attributes) {
        for (BaseAttribute attributeDefinition : attributes) {
            if (attributeDefinition.getName().equals(name)) {
                if (attributeDefinition.getType().equals(AttributeType.GROUP)) {
                    return true;
                }
            }
        }
        return false;
    }
}
