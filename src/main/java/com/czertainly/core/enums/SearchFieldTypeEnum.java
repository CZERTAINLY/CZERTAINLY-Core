package com.czertainly.core.enums;

import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchableFieldType;

import java.util.List;

public enum SearchFieldTypeEnum {

    STRING(SearchableFieldType.STRING,
            List.of(SearchCondition.CONTAINS, SearchCondition.NOT_CONTAINS, SearchCondition.EQUALS, SearchCondition.NOT_EQUALS, SearchCondition.EMPTY, SearchCondition.NOT_EMPTY, SearchCondition.STARTS_WITH, SearchCondition.ENDS_WITH)
            , false),
    DATE(SearchableFieldType.DATE,
            List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS, SearchCondition.GREATER, SearchCondition.LESSER)
            , false),
    NUMBER(SearchableFieldType.NUMBER,
            List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS, SearchCondition.GREATER, SearchCondition.LESSER)
            , false),
    LIST(SearchableFieldType.LIST,
            List.of(SearchCondition.EQUALS, SearchCondition.NOT_EQUALS, SearchCondition.EMPTY, SearchCondition.NOT_EMPTY)
            , true);


    private SearchableFieldType fieldType;

    private List<SearchCondition> contitions;

    private boolean multiValue;

    SearchFieldTypeEnum(final SearchableFieldType fieldType, final List<SearchCondition> contitions, final boolean multiValue) {
        this.fieldType = fieldType;
        this.contitions = contitions;
        this.multiValue = multiValue;
    }

    public SearchableFieldType getFieldType() {
        return fieldType;
    }

    public List<SearchCondition> getContitions() {
        return contitions;
    }

    public boolean isMultiValue() {
        return multiValue;
    }
}
