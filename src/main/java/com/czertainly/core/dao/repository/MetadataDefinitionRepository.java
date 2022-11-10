package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.MetadataDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.UUID;

@Repository
@Transactional
public interface MetadataDefinitionRepository extends JpaRepository<MetadataDefinition, String> {
    Boolean existsByConnectorUuidAndAttributeUuid(UUID connectorUuid, UUID attributeUuid);

    MetadataDefinition findByConnectorUuidAndAttributeUuid(UUID connectorUuid, UUID attributeUuid);
}
