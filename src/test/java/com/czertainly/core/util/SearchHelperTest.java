package com.czertainly.core.util;

import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.model.SearchFieldObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class SearchHelperTest {

    @Test
    void testPrepareSearchForJSON() {
        SearchFieldObject attributeSearchInfo = new SearchFieldObject(AttributeContentType.TIME);
        SearchFieldDataDto searchFieldDataDto = SearchHelper.prepareSearchForJSON(attributeSearchInfo, false);
        Assertions.assertFalse(searchFieldDataDto.getConditions().isEmpty());
        Assertions.assertFalse(searchFieldDataDto.getConditions().containsAll(List.of(FilterConditionOperator.IN_NEXT, FilterConditionOperator.IN_PAST)));
    }
}
