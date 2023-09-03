package com.czertainly.core.service;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.search.DynamicSearchInternalResponse;
import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.search.SearchableFieldType;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;  

public interface SearchService {
    Object completeSearchQueryExecutor(List<SearchFilterRequestDto> filters, String entity, List<SearchFieldDataDto> originalJson);
    DynamicSearchInternalResponse dynamicSearchQueryExecutor(SearchRequestDto searchRequestDto, String entity, List<SearchFieldDataDto> originalJson, String additionalWhereClause);

    Object customQueryExecutor(String sqlQuery);

    String getCompleteSearchQuery(List<SearchFilterRequestDto> filters, String entity, String joinQuery, List<SearchFieldDataDto> originalJson, Boolean conditionOnly, Boolean nativeCode);

    String getQueryDynamicBasedOnFilter(List<SearchFilterRequestDto> conditions, String entity, List<SearchFieldDataDto> originalJson, String joinQuery, Boolean conditionOnly, Boolean nativeCode, String additionalWhereClause) throws ValidationException;

    String createCriteriaBuilderString(SecurityFilter filter, Boolean addFinisher);
}
