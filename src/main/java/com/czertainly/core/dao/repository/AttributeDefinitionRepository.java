package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.core.dao.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttributeDefinitionRepository extends SecurityFilterRepository<AttributeDefinition, String> {
    Optional<AttributeDefinition> findByUuid(UUID uuid);
    Optional<AttributeDefinition> findByUuidAndType(UUID uuid, AttributeType type);
    Optional<AttributeDefinition> findByUuidAndTypeAndGlobalTrue(UUID uuid, AttributeType type);
    Optional<AttributeDefinition> findByAttributeUuid(UUID uuid);
    List<AttributeDefinition> findByTypeAndConnectorUuid(AttributeType type, UUID connectorUuid);
    List<AttributeDefinition> findByTypeAndConnectorUuidAndAttributeUuidInAndNameIn(AttributeType type, UUID connectorUuid, List<UUID> uuids, List<String> names);
    List<AttributeDefinition> findByTypeAndContentType(AttributeType type, AttributeContentType contentType);
    List<AttributeDefinition> findByTypeAndGlobal(AttributeType type, boolean global);
    List<AttributeDefinition> findByConnectorUuidAndTypeAndGlobal(UUID connectorUuid, AttributeType type, boolean global);
    Optional<AttributeDefinition> findByTypeAndNameAndGlobal(AttributeType attributeType, String attributeName, boolean global);
    Optional<AttributeDefinition> findByConnectorUuidAndAttributeUuid(UUID connectorUuid, UUID attributeUuid);
    Optional<AttributeDefinition> findByTypeAndConnectorUuidAndName(AttributeType type, UUID connectorUuid, String attributeName);
    Optional<AttributeDefinition> findByTypeAndConnectorUuidAndAttributeUuidAndName(AttributeType attributeType, UUID connectorUuid, UUID attributeUuid, String attributeName);
    Optional<AttributeDefinition> findByTypeAndName(AttributeType type, String attributeName);
    List<AttributeDefinition> findByType(AttributeType type);

    Boolean existsByTypeAndName(AttributeType type, String attributeName);
    Boolean existsByTypeAndNameAndGlobalTrue(AttributeType type, String attributeName);

    @Modifying
    @Query("UPDATE AttributeDefinition ad SET ad.connectorUuid = NULL WHERE ad.type = ?1 AND ad.connectorUuid = ?2")
    void removeConnectorByTypeAndConnectorUuid(AttributeType attributeType, UUID connectorUuid);

    long deleteByTypeAndConnectorUuid(AttributeType attributeType, UUID connectorUuid);

}
