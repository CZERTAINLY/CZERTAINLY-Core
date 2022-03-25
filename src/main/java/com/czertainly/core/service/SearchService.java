package com.czertainly.core.service;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.search.*;

import java.util.List;

public interface SearchService {
    SearchFieldDataDto getSearchField(SearchableFields field, String label, Boolean multiValue, List<Object> values,
                                      SearchableFieldType fieldType, List<SearchCondition> conditions);
    Object completeSearchQueryExecutor(List<SearchFilterRequestDto> filters, String entity, List<SearchFieldDataDto> originalJson);
    DynamicSearchInternalResponse dynamicSearchQueryExecutor(SearchRequestDto searchRequestDto, String entity, List<SearchFieldDataDto> originalJson);

}
