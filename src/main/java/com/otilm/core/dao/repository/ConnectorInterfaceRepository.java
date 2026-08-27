package com.otilm.core.dao.repository;

import com.otilm.api.model.client.connector.v2.ConnectorInterface;
import com.otilm.core.dao.entity.ConnectorInterfaceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectorInterfaceRepository extends JpaRepository<ConnectorInterfaceEntity, UUID> {

    /**
     * Reads one interface row directly rather than through the connector's LAZY interfaces collection, so a caller that
     * must not hold a transaction — every path that goes on to call the connector — can resolve it.
     */
    Optional<ConnectorInterfaceEntity> findByConnectorUuidAndInterfaceCodeAndVersion(UUID connectorUuid,
            ConnectorInterface interfaceCode, String version);
}
