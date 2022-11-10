package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.MetadataContent;
import com.czertainly.core.dao.entity.MetadataDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface MetadataContentRepository extends JpaRepository<MetadataContent, String> {

    Optional<MetadataContent> findByAttributeContentAndMetadataDefinition(String serializedContent, MetadataDefinition definition);
}
