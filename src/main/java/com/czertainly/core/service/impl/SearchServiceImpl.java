package com.czertainly.core.service.impl;

import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.search.DynamicSearchInternalResponse;
import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.api.model.core.search.SearchableFieldType;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.repository.GroupRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.SearchService;
import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SearchServiceImpl implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private RaProfileRepository raProfileRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Override
    public SearchFieldDataDto getSearchField(SearchableFields field, String label, Boolean multiValue, List<Object> values, SearchableFieldType fieldType, List<SearchCondition> conditions) {
        SearchFieldDataDto dto = new SearchFieldDataDto();
        dto.setFieldIdentifier(field.getCode());
        dto.setLabel(label);
        dto.setMultiValue(multiValue);
        dto.setValue(values);
        dto.setType(fieldType);
        dto.setConditions(conditions);
        return dto;
    }

    @Override
    public Object completeSearchQueryExecutor(List<SearchFilterRequestDto> filters, String entity, List<SearchFieldDataDto> originalJson) {

        String sqlQuery = "select c from " + entity + " c";
        logger.debug("Executing query: {}", sqlQuery);
        if (!filters.isEmpty()) {
            sqlQuery = getQueryDynamicBasedOnFilter(filters, entity, originalJson, "", false, false, "");
        }
        return customQueryExecutor(sqlQuery);
    }

    @Override
    public String getCompleteSearchQuery(List<SearchFilterRequestDto> filters, String entity, String joinQuery, List<SearchFieldDataDto> originalJson, Boolean conditionOnly, Boolean nativeCode) {

        String sqlQuery = !conditionOnly ? "select c from " + entity + " c" : "";
        logger.debug("Executing query: {}", sqlQuery);
        if (!filters.isEmpty()) {
            sqlQuery = getQueryDynamicBasedOnFilter(filters, entity, originalJson, joinQuery, conditionOnly, nativeCode, "");
        }
        return sqlQuery;
    }


    @Override
    public Object customQueryExecutor(String sqlQuery) {
        logger.debug("Executing query: {}", sqlQuery);
        Query query = entityManager.createQuery(sqlQuery);
        Object result = query.getResultList();
        return result;
    }

    @Override
    public Object nativeQueryExecutor(String sqlQuery) {
        logger.debug("Executing query: {}", sqlQuery);
        Query query = entityManager.createNativeQuery(sqlQuery);
        Object result = null;
        try {
            result = query.getResultList();
        } catch (PersistenceException e) {
            logger.warn("Result is empty: {}", e.getMessage());
        }
        return result;
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public Object asyncNativeQueryExecutor(String sqlQuery) {
        logger.debug("Executing query: {}", sqlQuery);
        Query query = entityManager.createNativeQuery(sqlQuery);
        Object result = null;
        try {
            result = query.getResultList();
        } catch (PersistenceException e) {
            logger.warn("Result is empty: {}", e.getMessage());
        }
        return result;
    }


    @Override
    public DynamicSearchInternalResponse dynamicSearchQueryExecutor(SearchRequestDto searchRequestDto, String entity, List<SearchFieldDataDto> originalJson, String additionalWhereClause) {
        logger.debug("Search request: {}", searchRequestDto.toString());
        Map<String, Integer> page = getPageable(searchRequestDto);
        DynamicSearchInternalResponse dynamicSearchInternalResponse = new DynamicSearchInternalResponse();
        if (searchRequestDto.getItemsPerPage() == null) {
            searchRequestDto.setItemsPerPage(CertificateServiceImpl.DEFAULT_PAGE_SIZE);
        }
        if (searchRequestDto.getPageNumber() == null) {
            searchRequestDto.setPageNumber(1);
        }
        String sqlQuery = getQueryDynamicBasedOnFilter(searchRequestDto.getFilters(), entity, originalJson, "", false, false, additionalWhereClause) + " GROUP BY created, uuid ORDER BY created DESC";
        Query query = entityManager.createQuery(sqlQuery);
        query.setFirstResult(page.get("start"));
        query.setMaxResults(searchRequestDto.getItemsPerPage());
        List<?> result = query.getResultList();

        if (result.isEmpty()) {
            dynamicSearchInternalResponse.setTotalPages(1);
            dynamicSearchInternalResponse.setTotalItems(0L);
            dynamicSearchInternalResponse.setResult(new ArrayList<>());
        } else {
            Query countQuery = entityManager.createQuery(sqlQuery.replace("select c from", "select COUNT(c) from").split(" GROUP BY ")[0]);
            Long totalItems = (Long) countQuery.getSingleResult();
            dynamicSearchInternalResponse.setTotalPages((int) Math.ceil((double) totalItems / searchRequestDto.getItemsPerPage()));
            dynamicSearchInternalResponse.setTotalItems(totalItems);
            dynamicSearchInternalResponse.setResult(result);
        }
        if (dynamicSearchInternalResponse.getTotalPages().equals(0)) {
            dynamicSearchInternalResponse.setTotalPages(1);
        }
        if (dynamicSearchInternalResponse.getTotalPages().equals(0)) {
            dynamicSearchInternalResponse.setTotalPages(1);
        }
        return dynamicSearchInternalResponse;
    }

    @Override
    public String getQueryDynamicBasedOnFilter(List<SearchFilterRequestDto> conditions, String entity, List<SearchFieldDataDto> originalJson, String joinQuery, Boolean conditionOnly, Boolean nativeCode, String additionalWhereClause) throws ValidationException {
        String query;
        if (joinQuery.isEmpty()) {
            query = (!conditionOnly ? "select c from " + entity + " c " : "") + " WHERE " + additionalWhereClause;
        } else {
            query = (!conditionOnly ? "select c from " + entity + " c " : " ") + joinQuery;
            if (!conditions.isEmpty()) {
                query += " AND ";
            }
        }
        List<String> queryParts = new ArrayList<>();
        List<SearchFieldDataDto> iterableJson = new LinkedList<>();
        for (SearchFilterRequestDto requestField : conditions) {
            for (SearchFieldDataDto field : originalJson) {
                if (requestField.getFieldIdentifier().equals(field.getFieldIdentifier())) {
                    SearchFieldDataDto fieldDup = new SearchFieldDataDto();
                    fieldDup.setFieldIdentifier(field.getFieldIdentifier());
                    fieldDup.setType(field.getType());
                    fieldDup.setLabel(field.getLabel());
                    fieldDup.setType(field.getType());
                    fieldDup.setMultiValue(field.isMultiValue());
                    fieldDup.setValue(requestField.getValue());
                    fieldDup.setConditions(List.of(requestField.getCondition()));
                    iterableJson.add(fieldDup);
                }
            }
        }
         for (SearchFieldDataDto filter : iterableJson) {
            String qp = "";
            String ntvCode = "";
            if (List.of(SearchableFields.OCSP_VALIDATION, SearchableFields.CRL_VALIDATION, SearchableFields.SIGNATURE_VALIDATION).contains(filter.getField())) {
                qp += " c.certificateValidationResult ";
            } else {
                if (nativeCode) {
                    ntvCode = filter.getField().getNativeCode();
                } else {
                    ntvCode = filter.getField().getCode();
                }
                qp += " c." + ntvCode  + " ";
            }
            if (filter.isMultiValue() && !(filter.getValue() instanceof String)) {
                List<String> whereObjects = new ArrayList<>();
                if (filter.getField().equals(SearchableFields.RA_PROFILE_NAME)) {
                    whereObjects.addAll(raProfileRepository.findAll().stream().filter(c -> ((List<Object>) filter.getValue()).contains(c.getName())).map(RaProfile::getUuid).map(c -> "'" + c + "'").collect(Collectors.toList()));
                } else if (filter.getField().equals(SearchableFields.GROUP_NAME)) {
                    whereObjects.addAll(groupRepository.findAll().stream().filter(c -> ((List<Object>) filter.getValue()).contains(c.getName())).map(Group::getUuid).map(c -> "'" + c + "'").collect(Collectors.toList()));
                } else {
                    whereObjects.addAll(((List<Object>) filter.getValue()).stream().map(i -> "'" + i.toString() + "'").collect(Collectors.toList()));
                }

                if (whereObjects.isEmpty()) {
                    throw new ValidationException(ValidationError.create("No valid object found for search in " + filter.getLabel()));
                }

                if (filter.getConditions().get(0).equals(SearchCondition.EQUALS)) {
                    qp += " IN (" + String.join(",", whereObjects) + " )";
                    if(filter.getField().equals(SearchableFields.COMPLIANCE_STATUS) && ((List<String>) filter.getValue()).contains("NA")){
                        qp += " or " + ntvCode + " IS NULL ";
                    }
                }
                if (filter.getConditions().get(0).equals(SearchCondition.NOT_EQUALS)) {
                    qp += " NOT IN (" + String.join(",", whereObjects) + " )";
                    if(filter.getField().equals(SearchableFields.COMPLIANCE_STATUS) && !((List<String>) filter.getValue()).contains("NA")){
                        qp += " or " + ntvCode + " IS NOT NULL ";
                    }
                }

            } else {
                if (filter.getField().equals(SearchableFields.SIGNATURE_VALIDATION)) {
                    if (filter.getConditions().get(0).equals(SearchCondition.SUCCESS)) {
                        qp += " LIKE '%\"Signature Verification\":{\"status\":\"success\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.FAILED)) {
                        qp += " LIKE '%\"Signature Verification\":{\"status\":\"failed\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.UNKNOWN)) {
                        qp += " LIKE '%\"Signature Verification\":{\"status\":\"not_checked\"%'";
                    }
                } else if (filter.getField().equals(SearchableFields.OCSP_VALIDATION)) {
                    if (filter.getConditions().get(0).equals(SearchCondition.SUCCESS)) {
                        qp += "LIKE '%\"OCSP Verification\":{\"status\":\"success\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.FAILED)) {
                        qp += "LIKE '%\"OCSP Verification\":{\"status\":\"failed\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.UNKNOWN)) {
                        qp += "LIKE '%\"OCSP Verification\":{\"status\":\"unknown\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.EMPTY)) {
                        qp += "LIKE '%\"OCSP Verification\":{\"status\":\"warning\"%'";
                    }
                } else if (filter.getField().equals(SearchableFields.CRL_VALIDATION)) {
                    if (filter.getConditions().get(0).equals(SearchCondition.SUCCESS)) {
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"success\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.FAILED)) {
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"failed\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.UNKNOWN)) {
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"unknown\"%'";
                    } else if (filter.getConditions().get(0).equals(SearchCondition.EMPTY)) {
                        qp += "LIKE '%\"CRL Verification\":{\"status\":\"warning\"%'";
                    }
                } else if (filter.getConditions().get(0).equals(SearchCondition.CONTAINS) || filter.getConditions().get(0).equals(SearchCondition.NOT_CONTAINS)) {
                    qp += filter.getConditions().get(0).getCode() + " '%" + filter.getValue().toString() + "%'";
                    try{
                        if(filter.getConditions().get(0).equals(SearchCondition.NOT_CONTAINS)) {
                            qp += " or " + ntvCode + " IS NULL ";
                        }
                    }catch (Exception e){
                        logger.warn("Unable to add empty query");
                    }
                } else if (filter.getConditions().get(0).equals(SearchCondition.STARTS_WITH)) {
                    qp += filter.getConditions().get(0).getCode() + " '" + filter.getValue().toString() + "%'";
                } else if (filter.getConditions().get(0).equals(SearchCondition.ENDS_WITH)) {
                    qp += filter.getConditions().get(0).getCode() + " '%" + filter.getValue().toString() + "'";
                } else if (filter.getConditions().get(0).equals(SearchCondition.EMPTY) || filter.getConditions().get(0).equals(SearchCondition.NOT_EMPTY)) {
                    qp += filter.getConditions().get(0).getCode();
                } else {
                    if (filter.getField().equals(SearchableFields.RA_PROFILE_NAME)) {
                        String raProfileUuid = raProfileRepository.findByName(filter.getValue().toString()).orElseThrow(() -> new ValidationException(ValidationError.create(filter.getValue().toString() + " not found"))).getUuid().toString();
                        qp += filter.getConditions().get(0).getCode() + " '" + raProfileUuid + "'";
                    } else if (filter.getField().equals(SearchableFields.GROUP_NAME)) {
                        String groupUuid = groupRepository.findByName(filter.getValue().toString()).orElseThrow(() -> new ValidationException(ValidationError.create(filter.getValue().toString() + " not found"))).getUuid().toString();
                        qp += filter.getConditions().get(0).getCode() + " '" + groupUuid + "'";
                    } else {
                        qp += filter.getConditions().get(0).getCode() + " '" + filter.getValue().toString() + "'";
                    }
                }
            }
            if (!qp.isEmpty()) {
                queryParts.add("(" + qp + ")");
            }
        }
        query += String.join(" AND ", queryParts);
        logger.debug("Executable query: {}", query);
        return query;
    }

    private Map<String, Integer> getPageable(SearchRequestDto request) throws ValidationException {
        if (request.getItemsPerPage() == null) {
            request.setItemsPerPage(CertificateServiceImpl.DEFAULT_PAGE_SIZE);
        }
        if (request.getItemsPerPage() > CertificateServiceImpl.MAX_PAGE_SIZE) {
            throw new ValidationException(ValidationError.create("Maximum items per page is " + CertificateServiceImpl.MAX_PAGE_SIZE));
        }

        Integer pageStart = 0;
        Integer pageEnd = request.getItemsPerPage();

        if (request.getPageNumber() != null) {
            pageStart = ((request.getPageNumber() - 1) * request.getItemsPerPage());
            pageEnd = request.getPageNumber() * request.getItemsPerPage();
        }
        logger.debug("Pagination information - Start: {}, End : {}", pageStart, pageEnd);
        return Map.ofEntries(Map.entry("start", pageStart), Map.entry("end", pageEnd));
    }

    @Override
    public String createCriteriaBuilderString(SecurityFilter filter, Boolean addFinisher) {
        String whereCondition = "";
        if (filter.getResourceFilter().areOnlySpecificObjectsAllowed()) {
            String data = filter.getResourceFilter().getAllowedObjects().stream().map(UUID::toString).collect(Collectors.joining("','", "'", "'"));
            whereCondition += "c.uuid" + " IN ( " + data + " )";
        } else {
            if (!filter.getResourceFilter().getForbiddenObjects().isEmpty()) {
                String data = filter.getResourceFilter().getForbiddenObjects().stream().map(UUID::toString).collect(Collectors.joining("','", "'", "'"));
                whereCondition += "c.uuid" + " NOT IN ( " + data + " )";
            }
        }

        if(filter.getParentResourceFilter() != null) {
            if(filter.getParentRefProperty() == null) throw new ValidationException("Unknown parent ref property to filter by parent resource " + filter.getParentResourceFilter().getResource());

            if (filter.getParentResourceFilter().areOnlySpecificObjectsAllowed()) {
                String data = filter.getParentResourceFilter().getAllowedObjects().stream().map(UUID::toString).collect(Collectors.joining("','", "'", "'"));
                whereCondition += "c." + filter.getParentRefProperty() + " IN ( " + data + " )";
            } else {
                if (!filter.getParentResourceFilter().getForbiddenObjects().isEmpty()) {
                    String data = filter.getParentResourceFilter().getForbiddenObjects().stream().map(UUID::toString).collect(Collectors.joining("','", "'", "'"));
                    whereCondition += "c." + filter.getParentRefProperty() + "NOT IN ( " + data + " )";
                }
            }
        }
        if(!whereCondition.equals("") && addFinisher){
            whereCondition = whereCondition + " AND";
        }
        return whereCondition;

    }
}
