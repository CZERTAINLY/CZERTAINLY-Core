package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface AttributeContentRepository extends JpaRepository<AttributeContent, String> {

    Optional<AttributeContent> findByAttributeContentAndAttributeDefinition(String serializedContent, AttributeDefinition definition);
}
