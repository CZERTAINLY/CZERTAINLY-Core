package com.czertainly.core.util;

import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.model.SearchFieldObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SearchHelperTest {

    @Test
    void testPrepareSearchForJSON() {
        SearchFieldObject attributeSearchInfo = new SearchFieldObject(AttributeContentType.TIME);
        SearchFieldDataDto searchFieldDataDto = SearchHelper.prepareSearchForJSON(attributeSearchInfo, false);
        Assertions.assertFalse(searchFieldDataDto.getConditions().isEmpty());
        Assertions.assertFalse(searchFieldDataDto.getConditions().contains(FilterConditionOperator.IN_NEXT), "Condition should not contain IN_NEXT operator");
        Assertions.assertFalse(searchFieldDataDto.getConditions().contains(FilterConditionOperator.IN_PAST), "Condition should not contain IN_PAST operator");
    }
}
