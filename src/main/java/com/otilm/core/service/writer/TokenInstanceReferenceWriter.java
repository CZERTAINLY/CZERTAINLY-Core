package com.otilm.core.service.writer;

import com.otilm.core.dao.entity.TokenInstanceReference;
import com.otilm.core.dao.repository.TokenInstanceReferenceRepository;
import com.otilm.core.model.crypto.TokenInstanceBasicModel;
import jakarta.persistence.EntityNotFoundException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenInstanceReferenceWriter {

    private final TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

    public TokenInstanceReferenceWriter(TokenInstanceReferenceRepository tokenInstanceReferenceRepository) {
        this.tokenInstanceReferenceRepository = tokenInstanceReferenceRepository;
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

    @Transactional
    public void delete(TokenInstanceBasicModel model) {
        Objects.requireNonNull(model, "Token instance model is required.");
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
