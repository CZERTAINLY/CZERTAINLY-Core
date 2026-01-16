package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AttributeApiClient;
import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.clients.EntityInstanceApiClient;
import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.cryptography.key.KeyRequestType;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.DataAttribute;
import com.czertainly.api.model.common.attribute.common.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.common.callback.AttributeCallbackMapping;
import com.czertainly.api.model.common.attribute.common.callback.RequestAttributeCallback;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.core.auth.AttributeResource;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.AttributeVersionHelper;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.EntityInstanceReference;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.dao.repository.EntityInstanceReferenceRepository;
import com.czertainly.core.logging.LoggingHelper;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.*;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Transactional
public class CallbackServiceImpl implements CallbackService {

    private static final Logger logger = LoggerFactory.getLogger(CallbackServiceImpl.class);

    private ConnectorService connectorService;
    private AttributeApiClient attributeApiClient;
    private CoreCallbackService coreCallbackService;
    private CredentialService credentialService;
    private AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository;
    private AuthorityInstanceApiClient authorityInstanceApiClient;
    private EntityInstanceReferenceRepository entityInstanceReferenceRepository;
    private EntityInstanceApiClient entityInstanceApiClient;
    private CryptographicKeyService cryptographicKeyService;
    private TokenProfileService tokenProfileService;
    private AttributeEngine attributeEngine;
    private ResourceService resourceService;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Autowired
    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @Autowired
    public void setAttributeApiClient(AttributeApiClient attributeApiClient) {
        this.attributeApiClient = attributeApiClient;
    }

    @Autowired
    public void setCoreCallbackService(CoreCallbackService coreCallbackService) {
        this.coreCallbackService = coreCallbackService;
    }

    @Autowired
    public void setCredentialService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Autowired
    public void setAuthorityInstanceReferenceRepository(AuthorityInstanceReferenceRepository authorityInstanceReferenceRepository) {
        this.authorityInstanceReferenceRepository = authorityInstanceReferenceRepository;
    }

    @Autowired
    public void setAuthorityInstanceApiClient(AuthorityInstanceApiClient authorityInstanceApiClient) {
        this.authorityInstanceApiClient = authorityInstanceApiClient;
    }

    @Autowired
    public void setEntityInstanceReferenceRepository(EntityInstanceReferenceRepository entityInstanceReferenceRepository) {
        this.entityInstanceReferenceRepository = entityInstanceReferenceRepository;
    }

    @Autowired
    public void setEntityInstanceApiClient(EntityInstanceApiClient entityInstanceApiClient) {
        this.entityInstanceApiClient = entityInstanceApiClient;
    }

    @Autowired
    public void setCryptographicKeyService(CryptographicKeyService cryptographicKeyService) {
        this.cryptographicKeyService = cryptographicKeyService;
    }

    @Autowired
    public void setTokenProfileService(TokenProfileService tokenProfileService) {
        this.tokenProfileService = tokenProfileService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    public Object callback(String uuid, FunctionGroupCode functionGroup, String kind, RequestAttributeCallback callback) throws ConnectorException, ValidationException, NotFoundException, AttributeException {
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(uuid));
        List<BaseAttribute> definitions = attributeApiClient.listAttributeDefinitions(connector.mapToDto(), functionGroup, kind);
        return getCallbackObject(callback, definitions, connector);
    }

    private Object getCallbackObject(RequestAttributeCallback callback, List<BaseAttribute> definitions, Connector connector) throws NotFoundException, ConnectorException, AttributeException {
        BaseAttribute attribute = getAttributeByName(callback.getName(), definitions, connector.getUuid());
        AttributeCallback attributeCallback = getAttributeCallback(attribute);
        AttributeDefinitionUtils.validateCallback(attributeCallback, callback);

        if (Objects.equals(attributeCallback.getCallbackContext(), "core/getCredentials")) {
            return coreCallbackService.coreGetCredentials(callback);
        }

        AttributeResource attributeResource = getAttributeResource(attribute);

        if (attribute instanceof DataAttribute dataAttribute && dataAttribute.getContentType() == AttributeContentType.RESOURCE)  {
            return coreCallbackService.coreGetResources(callback, attributeResource);
        }

        // Load complete credential data for mapping of type credential
        credentialService.loadFullCredentialData(attributeCallback, callback);
        if (attribute.getType() == AttributeType.DATA && callback.getBody() != null) {
            Map<String, AttributeResource> toResource = new HashMap<>();
            for (String to : callback.getBody().keySet()) {
                AttributeCallbackMapping callbackMapping = attributeCallback.getMappings().stream().filter(attributeCallbackMapping -> attributeCallbackMapping.getTo().equals(to)).findFirst().orElse(null);
                if (callbackMapping != null) {
                    String fromAttributeName = callbackMapping.getFrom().split("\\.", 2)[0];
                    DataAttribute fromAttribute =  (DataAttribute) getAttributeByName(fromAttributeName, definitions, connector.getUuid());
                    toResource.put(to, fromAttribute.getProperties().getResource());
                }
            }
            resourceService.loadResourceObjectContentData(attributeCallback, callback, toResource);
        }

        Object response = attributeApiClient.attributeCallback(connector.mapToDto(), attributeCallback, callback);
        if (attribute.getType().equals(AttributeType.GROUP)) {
            processGroupAttributes(connector.getUuid(), response);
        }
        return response;
    }

    private AttributeResource getAttributeResource(BaseAttribute attribute) {
        return attribute instanceof DataAttribute dataAttribute ? dataAttribute.getProperties().getResource() : null;
    }

    @Override
    public Object resourceCallback(Resource resource, String resourceUuid, RequestAttributeCallback callback) throws ConnectorException, ValidationException, NotFoundException, AttributeException {
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

            case LOCATION:
                EntityInstanceReference entityInstance = entityInstanceReferenceRepository.findByUuid(UUID.fromString(resourceUuid))
                        .orElseThrow(
                        () -> new NotFoundException(
                                EntityInstanceReference.class,
                                resourceUuid
                        )
                );
                connector = entityInstance.getConnector();
                definitions = entityInstanceApiClient.listLocationAttributes(connector.mapToDto(), entityInstance.getEntityInstanceUuid());
                break;

            default:
                throw new ValidationException(
                        ValidationError.create(
                                "Callback for the requested resource is not supported"
                        )
                );
        }

        LoggingHelper.putLogResourceInfo(Resource.CONNECTOR, true, connector.getUuid().toString(), connector.getName());
        return getCallbackObject(callback, definitions, connector);
    }

    private AttributeCallback getAttributeCallback(BaseAttribute attribute) {
        AttributeType type = attribute.getType();
        if (Objects.requireNonNull(type) == AttributeType.DATA) {
            return ((DataAttribute) attribute).getAttributeCallback();
        } else if (type == AttributeType.GROUP) {
            return AttributeVersionHelper.getGroupAttributeCallback(attribute);
        }
        throw new IllegalArgumentException("Attribute %s is not of type DATA or GROUP, cannot get callback for this attribute".formatted(attribute.getName()));
    }

    private BaseAttribute getAttributeByName(String name, List<BaseAttribute> attributes, UUID connectorUuid) throws NotFoundException {
        for (BaseAttribute attributeDefinition : attributes) {
            if (attributeDefinition.getName().equals(name)) {
                return attributeDefinition;
            }
        }

        // if not present in definitions from connector, search in reference attributes in DB
        DataAttribute referencedAttribute = attributeEngine.getDataAttributeDefinition(connectorUuid, name);
        if (referencedAttribute != null) {
            return referencedAttribute;
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
            List<BaseAttribute> callbackAttributes = mapper.convertValue(callbackResponse, mapper.getTypeFactory().constructCollectionType(List.class, BaseAttribute.class));
            attributeEngine.updateDataAttributeDefinitions(connectorUuid, null, callbackAttributes);
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
            if (attributeDefinition.getName().equals(name) && attributeDefinition.getType().equals(AttributeType.GROUP)) {
                    return true;
                }
        }
        return false;
    }
}
