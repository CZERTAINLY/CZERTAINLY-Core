package com.czertainly.core.util;

import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.enums.PlatformEnum;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldType;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.comparator.SearchFieldDataComparator;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import com.czertainly.core.model.SearchFieldObject;

import java.util.ArrayList;
import java.util.List;

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
        fieldDataDto.setConditions(fieldNameEnum.getFieldTypeEnum().getFieldType() == FilterFieldType.BOOLEAN && fieldNameEnum.getFieldProperty().getExpectedValue() != null ? List.of(FilterConditionOperator.EQUALS, FilterConditionOperator.NOT_EQUALS) : fieldNameEnum.getFieldTypeEnum().getConditions());
        fieldDataDto.setType(fieldNameEnum.getFieldTypeEnum().getFieldType());
        fieldDataDto.setValue(values);
        if (fieldNameEnum.getFieldProperty().getEnumClass() != null) {
            fieldDataDto.setPlatformEnum(PlatformEnum.findByClass(fieldNameEnum.getFieldProperty().getEnumClass()));
        }

        return fieldDataDto;
    }

    public static SearchFieldDataDto prepareSearchForJSON(final SearchFieldObject attributeSearchInfo, final boolean hasDupliciteInList) {
        final SearchFieldTypeEnum searchFieldTypeEnum = retrieveSearchFieldTypeEnumByContentType(attributeSearchInfo.getAttributeContentType(), attributeSearchInfo.isList());
        final SearchFieldDataDto fieldDataDto = new SearchFieldDataDto();
        fieldDataDto.setFieldIdentifier(attributeSearchInfo.getAttributeName() + "|" + attributeSearchInfo.getAttributeContentType().name());
        fieldDataDto.setFieldLabel(hasDupliciteInList ? String.format(SEARCH_LABEL_TEMPLATE, attributeSearchInfo.getLabel(), attributeSearchInfo.getAttributeContentType().getCode()) : attributeSearchInfo.getLabel());
        fieldDataDto.setMultiValue(attributeSearchInfo.isMultiSelect());
        fieldDataDto.setConditions(searchFieldTypeEnum.getConditions());
        fieldDataDto.setType(searchFieldTypeEnum.getFieldType());
        fieldDataDto.setValue(attributeSearchInfo.getContentItems());
        fieldDataDto.setAttributeContentType(attributeSearchInfo.getAttributeContentType());
        return fieldDataDto;
    }

    private static SearchFieldTypeEnum retrieveSearchFieldTypeEnumByContentType(AttributeContentType attributeContentType, boolean isList) {
        if (isList) {
            return SearchFieldTypeEnum.LIST;
        }

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
        final List<SearchFieldDataDto> searchFieldDataDtoList = searchFieldObjectList.stream().map(attribute -> prepareSearchForJSON(attribute, duplicatesOfNames.contains(attribute.getAttributeName()))).sorted(new SearchFieldDataComparator()).toList();
        return searchFieldDataDtoList;
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
