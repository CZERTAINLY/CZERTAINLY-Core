package com.czertainly.core.util;

import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import com.czertainly.core.model.SearchFieldObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchHelper {

    private static final String SEARCH_LABEL_TEMPLATE = "%s (%s)";

    public static SearchFieldDataDto prepareSearch(final SearchFieldNameEnum fieldNameEnum) {
        return prepareSearch(fieldNameEnum, null);
    }

    public static SearchFieldDataDto prepareSearch(final SearchFieldNameEnum fieldNameEnum, final Object values) {
        final SearchFieldDataDto fieldDataDto = new SearchFieldDataDto();
        fieldDataDto.setFieldIdentifier(fieldNameEnum.getFieldProperty().name());
        fieldDataDto.setFieldLabel(fieldNameEnum.getFieldLabel());
        fieldDataDto.setMultiValue(fieldNameEnum.getFieldTypeEnum().isMultiValue());
        fieldDataDto.setConditions(fieldNameEnum.getFieldTypeEnum().getContitions());
        fieldDataDto.setType(fieldNameEnum.getFieldTypeEnum().getFieldType());
        fieldDataDto.setValue(values);
        return fieldDataDto;
    }

    public static SearchFieldDataDto prepareSearchForJSON(final String fieldName, final AttributeContentType attributeContentType, final boolean hasDupliciteInList) {
        final SearchFieldTypeEnum searchFieldTypeEnum = retrieveSearchFieldTypeEnumByContentType(attributeContentType);
        final SearchFieldDataDto fieldDataDto = new SearchFieldDataDto();
        fieldDataDto.setFieldIdentifier(fieldName);
        fieldDataDto.setFieldLabel(hasDupliciteInList ? String.format(SEARCH_LABEL_TEMPLATE, fieldName, attributeContentType.getCode()) : fieldName);
        fieldDataDto.setMultiValue(searchFieldTypeEnum.isMultiValue());
        fieldDataDto.setConditions(searchFieldTypeEnum.getContitions());
        fieldDataDto.setType(searchFieldTypeEnum.getFieldType());
        fieldDataDto.setValue(null);
        return fieldDataDto;
    }

    private static SearchFieldTypeEnum retrieveSearchFieldTypeEnumByContentType(AttributeContentType attributeContentType) {
        SearchFieldTypeEnum searchFieldTypeEnum = null;
        switch (attributeContentType) {
            case TEXT, STRING -> searchFieldTypeEnum = SearchFieldTypeEnum.STRING;
            case DATE, DATETIME, TIME -> searchFieldTypeEnum = SearchFieldTypeEnum.DATE;
            case INTEGER, FLOAT -> searchFieldTypeEnum = SearchFieldTypeEnum.NUMBER;
            case BOOLEAN -> searchFieldTypeEnum = SearchFieldTypeEnum.BOOLEAN;
            default -> searchFieldTypeEnum = SearchFieldTypeEnum.OTHERS_AS_STRING;
        }
        return searchFieldTypeEnum;
    }

    public static List<SearchFieldDataDto> prepareSearchForJSON(final List<SearchFieldObject> searchFieldObjectList) {
        final List<String> duplicatesOfNames = filterDuplicity(searchFieldObjectList);
        return searchFieldObjectList.stream().map(attribute -> prepareSearchForJSON(attribute.getAttributeName(), attribute.getAttributeContentType(), duplicatesOfNames.contains(attribute.getAttributeName()))).collect(Collectors.toList());
    }

    private static List<String> filterDuplicity(final List<SearchFieldObject> searchFieldObjectList) {
        final List<String> uniqueNames = new ArrayList<>();
        final List<String> duplicatesOfNames = new ArrayList<>();
        searchFieldObjectList.forEach(attr -> {
            if (uniqueNames.contains(attr.getAttributeName())) {
                duplicatesOfNames.add(attr.getAttributeName());
            } else {
                uniqueNames.add(attr.getAttributeName());
            }
        });
        return duplicatesOfNames;
    }


}
