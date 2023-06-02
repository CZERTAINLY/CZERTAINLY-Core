package com.czertainly.core.search;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchGroup;

import java.io.Serializable;

public class SearchFilterRequestDtoDummy extends SearchFilterRequestDto {

    private SearchGroup searchGroup;
    private String fieldIdentifier;
    private SearchCondition condition;
    private Serializable value;

    public SearchFilterRequestDtoDummy(SearchGroup searchGroup, String fieldIdentifier, SearchCondition condition, Serializable value) {
        this.searchGroup = searchGroup;
        this.fieldIdentifier = fieldIdentifier;
        this.condition = condition;
        this.value = value;
    }

    @Override
    public SearchGroup getSearchGroup() {
        return searchGroup;
    }

    @Override
    public SearchCondition getCondition() {
        return condition;
    }

    @Override
    public String getFieldIdentifier() {
        return fieldIdentifier;
    }

    @Override
    public Serializable getValue() {
        return value;
    }

}
