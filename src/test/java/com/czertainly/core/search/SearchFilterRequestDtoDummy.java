package com.czertainly.core.search;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.search.SearchCondition;

import java.io.Serializable;

public class SearchFilterRequestDtoDummy extends SearchFilterRequestDto {

    private String groupName;
    private String fieldIdentifier;
    private SearchCondition condition;
    private Serializable value;

    public SearchFilterRequestDtoDummy(String groupName, String fieldIdentifier, SearchCondition condition, Serializable value) {
        this.groupName = groupName;
        this.fieldIdentifier = fieldIdentifier;
        this.condition = condition;
        this.value = value;
    }

    @Override
    public String getGroupName() {
        return groupName;
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
