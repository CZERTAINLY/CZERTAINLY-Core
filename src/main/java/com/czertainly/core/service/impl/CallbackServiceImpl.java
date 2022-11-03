package com.czertainly.core.service.impl;

import com.czertainly.api.clients.AttributeApiClient;
import com.czertainly.api.clients.AuthorityInstanceApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.callback.AttributeCallback;

import com.czertainly.api.model.common.attribute.v2.callback.RequestAttributeCallback;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.connector.FunctionGroupCode;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.AuthorityInstanceReference;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.repository.AuthorityInstanceReferenceRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CallbackService;
import com.czertainly.core.service.ConnectorService;
import com.czertainly.core.service.CoreCallbackService;
import com.czertainly.core.service.CredentialService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CallbackServiceImpl implements CallbackService {

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


    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.CALLBACK)
    public Object callback(String uuid, FunctionGroupCode functionGroup, String kind, RequestAttributeCallback callback) throws ConnectorException, ValidationException {
        Connector connector = connectorService.getConnectorEntity(SecuredUUID.fromString(uuid));
        List<BaseAttribute> definitions;
        definitions = attributeApiClient.listAttributeDefinitions(connector.mapToDto(), functionGroup, kind);
        AttributeCallback attributeCallback = getAttributeByName(callback.getName(), definitions).getAttributeCallback();
        AttributeDefinitionUtils.validateCallback(attributeCallback, callback);

        if (attributeCallback.getCallbackContext().equals("core/getCredentials")) {
            return coreCallbackService.coreGetCredentials(callback);
        }

        // Load complete credential data for mapping of type credential
        credentialService.loadFullCredentialData(attributeCallback, callback);

        return attributeApiClient.attributeCallback(connector.mapToDto(), attributeCallback, callback);
    }

    @Override
    @AuditLogged(originator = ObjectType.FE, affected = ObjectType.ATTRIBUTES, operation = OperationType.CALLBACK)
    public Object raProfileCallback(String authorityUuid, RequestAttributeCallback callback) throws ConnectorException, ValidationException {
        List<BaseAttribute> definitions;
        AuthorityInstanceReference authorityInstance = authorityInstanceReferenceRepository.findByUuid(UUID.fromString(authorityUuid))
                .orElseThrow(() -> new NotFoundException(AuthorityInstanceReference.class, authorityUuid));
        definitions = authorityInstanceApiClient.listRAProfileAttributes(authorityInstance.getConnector().mapToDto(), authorityInstance.getAuthorityInstanceUuid());
        AttributeCallback attributeCallback = getAttributeByName(callback.getName(), definitions).getAttributeCallback();
        AttributeDefinitionUtils.validateCallback(attributeCallback, callback);

        if (attributeCallback.getCallbackContext().equals("core/getCredentials")) {
            return coreCallbackService.coreGetCredentials(callback);
        }

        // Load complete credential data for mapping of type credential
        credentialService.loadFullCredentialData(attributeCallback, callback);

        return attributeApiClient.attributeCallback(authorityInstance.getConnector().mapToDto(), attributeCallback, callback);
    }

    private DataAttribute getAttributeByName(String name, List<BaseAttribute> attributes) throws NotFoundException {
        for (BaseAttribute attributeDefinition : attributes) {
            if (attributeDefinition.getName().equals(name)) {
                return (DataAttribute) attributeDefinition;
            }
        }
        throw new NotFoundException(BaseAttribute.class, name);
    }
}
