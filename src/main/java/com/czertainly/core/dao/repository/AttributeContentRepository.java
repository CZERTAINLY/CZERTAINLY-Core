package com.czertainly.core.dao.repository;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeContent;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.model.SearchFieldObject;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Transactional
public interface AttributeContentRepository extends JpaRepository<AttributeContent, String> {

    @Query("SELECT ac " +
            "FROM AttributeContent ac" +
            "   JOIN AttributeContentItem aci on aci.attributeContentUuid = ac.uuid " +
            "WHERE ac.attributeDefinition = ?2 and aci.json in ?1 ")
    List<AttributeContent> findByBaseAttributeContentAndAttributeDefinition(List<BaseAttributeContent> baseAttributeContent, AttributeDefinition definition);

    @Query("SELECT DISTINCT new com.czertainly.core.model.SearchFieldObject(ad.attributeName, ad.contentType, ad.type)" +
            "from AttributeContent2Object aco " +
            "         join AttributeContent ac on ac.uuid = aco.attributeContentUuid " +
            "         join AttributeDefinition ad on ac.attributeDefinitionUuid = ad.uuid\n" +
            "where ad.type = ?2 and aco.objectType = ?1")
    List<SearchFieldObject> findDistinctAttributeContentNamesByAttrTypeAndObjType(final Resource resourceType, final AttributeType attributeType);

}
