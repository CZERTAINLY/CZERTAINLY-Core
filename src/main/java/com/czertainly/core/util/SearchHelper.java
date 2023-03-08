package com.czertainly.core.util;

import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.enums.SearchFieldNameEnum;

public class SearchHelper {

    public static SearchFieldDataDto prepareSearch(final SearchFieldNameEnum fieldNameEnum) {
        return prepareSearch(fieldNameEnum, null);
    }

    public static SearchFieldDataDto prepareSearch(final SearchFieldNameEnum fieldNameEnum, final Object values) {
        final SearchFieldDataDto fieldDataDto = new SearchFieldDataDto();
        fieldDataDto.setFieldIdentifier(fieldNameEnum.getFieldProperty().getCode());
        fieldDataDto.setFieldLabel(fieldNameEnum.getFieldLabel());
        fieldDataDto.setMultiValue(fieldNameEnum.getFieldTypeEnum().isMultiValue());
        fieldDataDto.setConditions(fieldNameEnum.getFieldTypeEnum().getContitions());
        fieldDataDto.setType(fieldNameEnum.getFieldTypeEnum().getFieldType());
        fieldDataDto.setValue(values);
        return fieldDataDto;
    }

}
