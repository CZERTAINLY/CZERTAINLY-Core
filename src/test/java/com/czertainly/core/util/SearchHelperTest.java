package com.czertainly.core.util;

import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.model.SearchFieldObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class SearchHelperTest extends BaseSpringBootTest {

    @Test
    void testPrepareSearchForJSON() {
        SearchFieldObject attributeSearchInfo = new SearchFieldObject(AttributeContentType.TIME);
        SearchFieldDataDto searchFieldDataDto = SearchHelper.prepareSearchForJSON(attributeSearchInfo, false);
        Assertions.assertFalse(searchFieldDataDto.getConditions().isEmpty());
        Assertions.assertFalse(searchFieldDataDto.getConditions().contains(FilterConditionOperator.IN_NEXT), "Condition should not contain IN_NEXT operator");
        Assertions.assertFalse(searchFieldDataDto.getConditions().contains(FilterConditionOperator.IN_PAST), "Condition should not contain IN_PAST operator");
    }

    @Test
    void testPrepareSearchCount() {
        Set<FilterField> shouldHaveCountOperator = Set.of(FilterField.GROUP_NAME, FilterField.SUCCEEDING_CERTIFICATES, FilterField.PRECEDING_CERTIFICATES, FilterField.CERT_LOCATION_NAME, FilterField.CK_GROUP);
        Set<FilterField> withCountOperator = new HashSet<>();
        for (FilterField filterField : FilterField.values()) {
            SearchFieldDataDto searchFieldDataDto = SearchHelper.prepareSearch(filterField, List.of("sampleValue"));
            if (searchFieldDataDto.getConditions().containsAll(Set.of(FilterConditionOperator.COUNT_EQUAL, FilterConditionOperator.COUNT_NOT_EQUAL, FilterConditionOperator.COUNT_GREATER_THAN, FilterConditionOperator.COUNT_LESS_THAN)))
                withCountOperator.add(filterField);
            }
        Assertions.assertEquals(shouldHaveCountOperator, withCountOperator);
    }

    @Test
    void testPrepareSearchJsonArray() {
        Set<FilterField> jsonArrays = Set.of(FilterField.AUDIT_LOG_RESOURCE_NAME, FilterField.AUDIT_LOG_RESOURCE_UUID);
        for (FilterField filterField : jsonArrays) {
            SearchFieldDataDto searchFieldDataDto = SearchHelper.prepareSearch(filterField);
            Assertions.assertEquals(Set.of(FilterConditionOperator.EQUALS, FilterConditionOperator.NOT_EQUALS, FilterConditionOperator.NOT_EMPTY, FilterConditionOperator.EMPTY),
                    new HashSet<>(searchFieldDataDto.getConditions()));
        }
    }
}
