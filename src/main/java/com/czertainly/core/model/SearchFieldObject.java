package com.czertainly.core.model;

import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import lombok.Data;

import java.util.List;

@Data
public class SearchFieldObject {

    private String attributeName;

    private AttributeContentType attributeContentType;

    private AttributeType attributeType;

    private String label;

    private boolean list;

    private boolean multiSelect;

    private List<String> contentItems;


    public SearchFieldObject(AttributeContentType attributeContentType) {
        this.attributeContentType = attributeContentType;
    }

}
