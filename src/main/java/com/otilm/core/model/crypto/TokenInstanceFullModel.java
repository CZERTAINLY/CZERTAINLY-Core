package com.otilm.core.model.crypto;

import com.otilm.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.otilm.core.dao.entity.Connector;
import com.otilm.core.model.connector.ImmutableConnectorInterface;
import java.util.Set;

public interface TokenInstanceFullModel extends TokenInstanceBasicModel {
    Connector connector();

    ImmutableConnectorInterface connectorInterface();

    Set<ImmutableTokenProfileFullModel> tokenProfiles();

    TokenInstanceFullModel withNewStatus(TokenInstanceStatus newStatus);
}
