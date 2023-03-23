package com.czertainly.core.model;

import com.czertainly.api.model.common.attribute.v2.AttributeType;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class SearchFieldObject<T> {

    private String attributeName;

    private AttributeContentType attributeContentType;

    private AttributeType attributeType;

    public SearchFieldObject(AttributeContentType attributeContentType) {
        this.attributeContentType = attributeContentType;
    }

    public SearchFieldObject(String attributeName, AttributeContentType attributeContentType, AttributeType attributeType) {
        this.attributeName = attributeName;
        this.attributeContentType = attributeContentType;
        this.attributeType = attributeType;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public AttributeContentType getAttributeContentType() {
        return attributeContentType;
    }

    public void setAttributeContentType(AttributeContentType attributeContentType) {
        this.attributeContentType = attributeContentType;
    }

    public AttributeType getAttributeType() {
        return attributeType;
    }

    public void setAttributeType(AttributeType attributeType) {
        this.attributeType = attributeType;
    }

    public boolean isDateTimeFormat() {
        return this.attributeContentType.equals(AttributeContentType.DATE)
                || this.attributeContentType.equals(AttributeContentType.TIME)
                || this.attributeContentType.equals(AttributeContentType.DATETIME);
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
