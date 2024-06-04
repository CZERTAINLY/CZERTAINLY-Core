package com.czertainly.core.model;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.CustomAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    public SearchFieldObject(String attributeName, AttributeContentType attributeContentType, AttributeType attributeType) {
        this.attributeName = attributeName;
        this.attributeContentType = attributeContentType;
        this.attributeType = attributeType;
    }

    public SearchFieldObject(String attributeName, AttributeContentType attributeContentType, AttributeType attributeType, String label, BaseAttribute<?> attributeDefinition) {
        this.attributeName = attributeName;
        this.attributeContentType = attributeContentType;
        this.attributeType = attributeType;
        this.label = label;

        if (attributeType == AttributeType.CUSTOM || attributeType == AttributeType.DATA) {
            if (attributeDefinition instanceof CustomAttribute customAttribute) {
                list = customAttribute.getProperties().isList();
                multiSelect = customAttribute.getProperties().isMultiSelect();
                if (list && customAttribute.getContent() != null) {
                    contentItems = customAttribute.getContent().stream().map(item -> item.getData().toString()).toList();
                }
            } else {
                DataAttribute dataAttribute = (DataAttribute) attributeDefinition;
                list = dataAttribute.getProperties().isList();
                multiSelect = dataAttribute.getProperties().isMultiSelect();
                if (list && dataAttribute.getContent() != null) {
                    contentItems = dataAttribute.getContent().stream().map(item -> item.getData().toString()).toList();
                }
            }
        }
    }

    public boolean isDateTimeFormat() {
        return this.attributeContentType.equals(AttributeContentType.DATE)
                || this.attributeContentType.equals(AttributeContentType.TIME)
                || this.attributeContentType.equals(AttributeContentType.DATETIME);
    }

    public boolean isBooleanFormat() {
        return this.attributeContentType.equals(AttributeContentType.BOOLEAN);
    }

    public Class getDateTimeFormatClass() {
        Class clazz = null;
        switch (this.attributeContentType) {
            case DATE -> clazz = LocalDate.class;
            case TIME -> clazz = LocalTime.class;
            case DATETIME -> clazz = LocalDateTime.class;
        }
        return clazz;
    }

    public LocalDateTime getLocalDateTimeFormat(final String dateTimeValue) {
        if (!this.attributeContentType.equals(AttributeContentType.DATETIME)) {
            return null;
        }
        return LocalDateTime.parse(dateTimeValue, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }

    public LocalDate getLocalDateFormat(final String dateTimeValue) {
        if (!this.attributeContentType.equals(AttributeContentType.DATE)) {
            return null;
        }
        return LocalDate.parse(dateTimeValue);
    }

    public LocalTime getLocalTimeFormat(final String dateTimeValue) {
        if (!this.attributeContentType.equals(AttributeContentType.TIME)) {
            return null;
        }
        return LocalTime.parse(dateTimeValue);
    }
}
