package com.czertainly.core.service.impl;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.core.audit.*;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.search.SearchFieldDataDto;
import com.czertainly.core.dao.entity.AuditLog;
import com.czertainly.core.dao.entity.AuditLog_;
import com.czertainly.core.dao.repository.AuditLogRepository;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.logging.AuditLogExportDto;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.AuditLogService;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.czertainly.core.util.RequestValidatorHelper;
import com.czertainly.core.util.SearchHelper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AuditLogServiceImpl implements AuditLogService {

    private static final String LOGGER_NAME = "audit-log";
    private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.findAndRegisterModules();
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Value("${export.auditLog.fileName.prefix:audit-logs}")
    private String fileNamePrefix;

    @Value("${auditLog.enabled:false}")
    private boolean auditLogEnabled;

    private AuditLogRepository auditLogRepository;
    private ExportProcessor exportProcessor;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public void setAuditLogRepository(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Autowired
    public void setExportProcessor(ExportProcessor exportProcessor) {
        this.exportProcessor = exportProcessor;
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUDIT_LOG, action = ResourceAction.LIST)
    public AuditLogResponseDto listAuditLogs(final SearchRequestDto request) {
        RequestValidatorHelper.revalidateSearchRequestDto(request);
        final Pageable p = PageRequest.of(request.getPageNumber() - 1, request.getItemsPerPage());

        final TriFunction<Root<AuditLog>, CriteriaBuilder, CriteriaQuery, jakarta.persistence.criteria.Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, request.getFilters());
        final List<AuditLogDto> auditLogs = auditLogRepository.findUsingSecurityFilter(SecurityFilter.create(), List.of(), additionalWhereClause, p, (root, cb) -> cb.desc(root.get(AuditLog_.loggedAt)))
                .stream()
                .map(AuditLog::mapToDto).toList();
        final Long totalItems = auditLogRepository.countUsingSecurityFilter(SecurityFilter.create(), additionalWhereClause);

        AuditLogResponseDto response = new AuditLogResponseDto();
        response.setItems(auditLogs);
        response.setItemsPerPage(request.getItemsPerPage());
        response.setPageNumber(request.getPageNumber());
        response.setTotalItems(totalItems);
        response.setTotalPages((int) Math.ceil((double) totalItems / request.getItemsPerPage()));

        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUDIT_LOG, action = ResourceAction.EXPORT)
    public ExportResultDto exportAuditLogs(final List<SearchFilterRequestDto> filters) {
        final TriFunction<Root<AuditLog>, CriteriaBuilder, CriteriaQuery, jakarta.persistence.criteria.Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, filters);

        final List<AuditLog> auditLogsEntities = auditLogRepository.findUsingSecurityFilter(SecurityFilter.create(), List.of(), additionalWhereClause, Pageable.ofSize(Integer.MAX_VALUE), (root, cb) -> cb.desc(root.get(AuditLog_.id)));
        final List<AuditLogExportDto> auditLogs = auditLogsEntities
                .stream()
                .map(a -> {
                    AuditLogExportDto.AuditLogExportDtoBuilder builder = AuditLogExportDto.builder();
                    builder.id(a.getId());
                    builder.version(a.getVersion());
                    builder.loggedAt(a.getLoggedAt());
                    builder.module(a.getModule());
                    builder.resource(a.getResource());
                    builder.resourceUuids(a.getLogRecord().resource().uuids());
                    builder.resourceNames(a.getLogRecord().resource().names());
                    builder.affiliatedResource(a.getAffiliatedResource());
                    if (a.getLogRecord().affiliatedResource() != null) {
                        builder.affiliatedResourceUuids(a.getLogRecord().affiliatedResource().uuids());
                        builder.affiliatedResourceNames(a.getLogRecord().affiliatedResource().names());
                    }
                    builder.actorType(a.getActorType());
                    builder.actorAuthMethod(a.getActorAuthMethod());
                    builder.actorUuid(a.getActorUuid());
                    builder.actorName(a.getActorName());
                    if (a.getLogRecord().source() != null) {
                        builder.ipAddress(a.getLogRecord().source().ipAddress());
                        builder.userAgent(a.getLogRecord().source().userAgent());
                    }
                    builder.operation(a.getOperation());
                    builder.operationResult(a.getOperationResult());
                    builder.message(a.getMessage());

                    try {
                        builder.operationData(MAPPER.writeValueAsString(a.getLogRecord().operationData()));
                    } catch (JsonProcessingException e) {
                        builder.operationData("ERROR_SERIALIZATION");
                    }

                    try {
                        builder.additionalData(MAPPER.writeValueAsString(a.getLogRecord().additionalData()));
                    } catch (JsonProcessingException e) {
                        builder.additionalData("ERROR_SERIALIZATION");
                    }

                    return builder.build();
                }).toList();

        return exportProcessor.generateExport(fileNamePrefix, auditLogs);
    }

    @Override
    @ExternalAuthorization(resource = Resource.AUDIT_LOG, action = ResourceAction.DELETE)
    public void purgeAuditLogs(final List<SearchFilterRequestDto> filters) {
        final TriFunction<Root<AuditLog>, CriteriaBuilder, CriteriaQuery, jakarta.persistence.criteria.Predicate> additionalWhereClause = (root, cb, cr) -> FilterPredicatesBuilder.getFiltersPredicate(cb, cr, root, filters);
        final List<AuditLog> auditLogs = auditLogRepository.findUsingSecurityFilter(SecurityFilter.create(), List.of(), additionalWhereClause);
        auditLogRepository.deleteAll(auditLogs);
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformationByGroup() {
        final List<SearchFieldDataByGroupDto> searchFieldDataByGroupDtos = new ArrayList<>();

        List<SearchFieldDataDto> fields = new ArrayList<>();
        List<FilterField> filterFields = FilterField.getEnumsForResource(Resource.AUDIT_LOG);
        for (FilterField filterField : filterFields) {
            fields.add(SearchHelper.prepareSearch(filterField));
        }

        searchFieldDataByGroupDtos.add(new SearchFieldDataByGroupDto(fields, FilterFieldSource.PROPERTY));
        return searchFieldDataByGroupDtos;
    }

}
