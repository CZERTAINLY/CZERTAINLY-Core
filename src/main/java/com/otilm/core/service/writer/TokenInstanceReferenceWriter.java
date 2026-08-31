package com.otilm.core.service.writer;

import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.model.client.attribute.RequestAttribute;
import com.otilm.api.model.common.attribute.common.MetadataAttribute;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.core.attribute.engine.AttributeEngine;
import com.otilm.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.otilm.core.dao.entity.TokenInstanceReference;
import com.otilm.core.dao.repository.TokenInstanceReferenceRepository;
import com.otilm.core.model.crypto.TokenInstanceBasicModel;
import com.otilm.core.service.CommentInternalService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenInstanceReferenceWriter {

    private final TokenInstanceReferenceRepository tokenInstanceReferenceRepository;
    private final AttributeEngine attributeEngine;
    private final CommentInternalService commentService;

    public TokenInstanceReferenceWriter(TokenInstanceReferenceRepository tokenInstanceReferenceRepository,
            AttributeEngine attributeEngine, CommentInternalService commentService) {
        this.tokenInstanceReferenceRepository = tokenInstanceReferenceRepository;
        this.attributeEngine = attributeEngine;
        this.commentService = commentService;
    }

    @Transactional
    public TokenInstanceReference save(TokenInstanceBasicModel model) {
        return tokenInstanceReferenceRepository.save(toEntity(model));
    }

    @Transactional
    public TokenInstanceReference update(TokenInstanceBasicModel model) {
        Objects.requireNonNull(model, "Token instance model is required.");
        if (!tokenInstanceReferenceRepository.existsByUuid(model.uuid())) {
            throw new EntityNotFoundException("Token instance not found: " + model.uuid());
        }
        return tokenInstanceReferenceRepository.save(toEntity(model));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAttributes(UUID tokenUuid, UUID connectorUuid, List<RequestAttribute> customAttributes,
            List<RequestAttribute> dataAttributes, List<MetadataAttribute> metadataAttributes)
            throws NotFoundException, AttributeException {
        attributeEngine
                .updateMetadataAttributes(metadataAttributes,
                        ObjectAttributeContentInfo.builder(Resource.TOKEN, tokenUuid).connector(connectorUuid).build());
        attributeEngine.updateObjectCustomAttributesContent(Resource.TOKEN, tokenUuid, customAttributes);
        attributeEngine
                .updateObjectDataAttributesContent(
                        ObjectAttributeContentInfo.builder(Resource.TOKEN, tokenUuid).connector(connectorUuid).build(),
                        dataAttributes);
    }

    @Transactional
    public void delete(TokenInstanceBasicModel model) {
        Objects.requireNonNull(model, "Token instance model is required.");
        attributeEngine.deleteObjectAttributeContent(Resource.TOKEN, model.uuid());
        commentService.removeObjectComments(Resource.TOKEN, model.uuid());
        tokenInstanceReferenceRepository.deleteById(model.uuid());
    }

    private TokenInstanceReference toEntity(TokenInstanceBasicModel model) {
        Objects.requireNonNull(model, "Token instance model is required.");
        TokenInstanceReference tokenInstanceReference = new TokenInstanceReference();
        tokenInstanceReference.setUuid(model.uuid());
        tokenInstanceReference.setTokenInstanceUuid(model.tokenInstanceUuid());
        tokenInstanceReference.setName(model.name());
        tokenInstanceReference.setStatus(model.status());
        tokenInstanceReference.setKind(model.kind());
        tokenInstanceReference.setConnectorUuid(model.connectorUuid());
        tokenInstanceReference.setConnectorName(model.connectorName());
        tokenInstanceReference.setConnectorInterfaceUuid(model.connectorInterfaceUuid());
        return tokenInstanceReference;
    }
}
