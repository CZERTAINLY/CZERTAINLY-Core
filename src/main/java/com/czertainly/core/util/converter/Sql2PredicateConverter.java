package com.czertainly.core.util.converter;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.search.SearchCondition;
import com.czertainly.api.model.core.search.SearchGroup;
import com.czertainly.api.model.core.search.SearchableFieldType;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import com.czertainly.core.model.SearchFieldObject;
import jakarta.persistence.criteria.*;

import java.util.*;

public class Sql2PredicateConverter {

    private Sql2PredicateConverter() {
    }

    private static final String OCSP_VERIFICATION = "%\"OCSP Verification\":{\"status\":\"%STATUS%\"%";
    private static final String SIGNATURE_VERIFICATION = "%\"Signature Verification\":{\"status\":\"%STATUS%\"%";
    private static final String CRL_VERIFICATION = "%\"CRL Verification\":{\"status\":\"%STATUS%\"%";

    public static Predicate mapSearchFilter2Predicates(final List<SearchFilterRequestDto> dtos, final CriteriaBuilder criteriaBuilder, final Root root, final List<UUID> objectUUIDsToBeFiltered) {
        final List<Predicate> predicates = new ArrayList<>();
        boolean hasFilteredAttributes = false;
        for (final SearchFilterRequestDto dto : dtos) {
            if (dto.getSearchGroup() == SearchGroup.PROPERTY) {
                predicates.add(mapSearchFilter2Predicate(dto, criteriaBuilder, root));
            } else {
                hasFilteredAttributes = true;
            }
        }
        final Predicate propertyPredicates = criteriaBuilder.and(predicates.toArray(new Predicate[]{}));
        if (objectUUIDsToBeFiltered != null && !dtos.isEmpty() && hasFilteredAttributes) {
            Predicate uuidOrPredicate = root.get("uuid").in(objectUUIDsToBeFiltered);
            if (root.getJavaType().equals(CryptographicKeyItem.class)) {
                uuidOrPredicate = criteriaBuilder.or(
                        uuidOrPredicate,
                        prepareExpression(root, "cryptographicKey.uuid").in(objectUUIDsToBeFiltered));
            }
            return criteriaBuilder.and(propertyPredicates, uuidOrPredicate);
        }
        return propertyPredicates;
    }

    public static Predicate mapSearchFilter2Predicates(final List<SearchFilterRequestDto> dtos, final CriteriaBuilder criteriaBuilder, final Root root) {
        return mapSearchFilter2Predicates(dtos, criteriaBuilder, root, null);
    }

    public static Predicate mapSearchFilter2Predicate(final SearchFilterRequestDto dto, final CriteriaBuilder criteriaBuilder, final Root root) {
        return preparePredicateByConditions(dto, criteriaBuilder, root);
    }

    private static Predicate preparePredicateByConditions(final SearchFilterRequestDto dto, final CriteriaBuilder criteriaBuilder, final Root root) {
        final List<Predicate> predicates = new ArrayList<>();
        final List<Object> objects = readAndCheckIncomingValues(dto);
        for (final Object valueObject : objects) {
            predicates.add(processPredicate(criteriaBuilder, root, dto, valueObject));
        }
        return predicates.size() > 1 ? criteriaBuilder.or(predicates.toArray(new Predicate[]{})) : predicates.get(0);
    }

    private static Predicate processPredicate(final CriteriaBuilder criteriaBuilder, final Root root, final SearchFilterRequestDto dto, final Object valueObject) {

        final SearchableFields searchableFields = SearchableFields.valueOf(dto.getFieldIdentifier());
        final SearchCondition searchCondition = checkOrReplaceSearchCondition(dto, searchableFields);
        final SearchFieldTypeEnum searchFieldTypeEnum = SearchFieldNameEnum.getEnumBySearchableFields(searchableFields).getFieldTypeEnum();
        final boolean isDateFormat = SearchFieldTypeEnum.DATE.equals(searchFieldTypeEnum) || SearchFieldTypeEnum.DATETIME.equals(searchFieldTypeEnum);
        final Predicate predicate = checkCertificateValidationResult(root, criteriaBuilder, dto, valueObject, searchableFields);
        if (predicate == null) {
            final Object expressionValue = prepareValue(valueObject, searchableFields);
            final SearchFieldObject searchFieldObject = SearchFieldTypeEnum.DATETIME.equals(searchFieldTypeEnum) ? new SearchFieldObject(AttributeContentType.DATETIME) : null;
            return buildPredicateByCondition(criteriaBuilder, searchCondition, null, root, searchableFields, expressionValue, isDateFormat, SearchableFieldType.BOOLEAN.equals(searchFieldTypeEnum.getFieldType()), dto, searchFieldObject);
        }
        return predicate;
    }

    private static Predicate buildPredicateByCondition(final CriteriaBuilder criteriaBuilder, SearchCondition searchCondition, Expression expression, Root root, SearchableFields searchableFields, Object expressionValue, final boolean isDateFormat, final boolean isBoolean, final SearchFilterRequestDto dto, SearchFieldObject searchFieldObject) {
        if (expression == null) {
            if (searchableFields.getPathToBeJoin() == null) {
                expression = prepareExpression(root, searchableFields.getCode());
            }
            else {
                final Join join = prepareJoin(root, searchableFields.getPathToBeJoin());
                expression = join.get(searchableFields.getCode());
            }
        }

        if (expressionValue == null) {
            expressionValue = dto.getValue().toString();
        }

        if (isDateFormat) {
            if (searchFieldObject == null) {
                searchFieldObject = new SearchFieldObject(AttributeContentType.DATE);
            }
            return prepareDateTimePredicate(criteriaBuilder, searchCondition, expression, expressionValue.toString(), searchFieldObject);
        }

        Predicate predicate = null;
        if (isBoolean) {
            if (searchableFields == null || searchableFields.getExpectedValue() == null) {
                switch (searchCondition) {
                    case EQUALS -> predicate = criteriaBuilder.equal(expression.as(Boolean.class), Boolean.parseBoolean(expressionValue.toString()));
                    case NOT_EQUALS -> predicate = criteriaBuilder.notEqual(expression.as(Boolean.class), Boolean.parseBoolean(expressionValue.toString()));
                }
                return predicate;
            } else {
                final Boolean booleanValue = Boolean.parseBoolean(expressionValue.toString());
                expressionValue = searchableFields.getExpectedValue();
                if (SearchCondition.EQUALS.equals(searchCondition) && !booleanValue) {
                    searchCondition = SearchCondition.NOT_EQUALS;
                } else if (SearchCondition.NOT_EQUALS.equals(searchCondition) && !booleanValue) {
                    searchCondition = SearchCondition.EQUALS;
                }
            }
        }

        switch (searchCondition) {
            case EQUALS -> predicate = criteriaBuilder.equal(expression, expressionValue);
            case NOT_EQUALS -> {
                if (searchableFields != null && searchableFields.getPathToBeJoin() != null) {
                    predicate = criteriaBuilder.or(criteriaBuilder.and(criteriaBuilder.notEqual(expression, expressionValue), criteriaBuilder.equal(expression, expressionValue)), criteriaBuilder.isNull(expression));
                } else {
                    predicate = criteriaBuilder.or(criteriaBuilder.notEqual(expression, expressionValue), criteriaBuilder.isNull(expression));
                }
            }
            case STARTS_WITH -> predicate = criteriaBuilder.like(expression, expressionValue + "%");
            case ENDS_WITH -> predicate = criteriaBuilder.like(expression, "%" + expressionValue);
            case CONTAINS -> predicate = criteriaBuilder.like(expression, "%" + expressionValue + "%");
            case NOT_CONTAINS -> predicate = criteriaBuilder.or(
                    criteriaBuilder.notLike(expression, "%" + expressionValue + "%"),
                    retrievePredicateForNull(criteriaBuilder, root, searchableFields, expression)
            );
            case EMPTY -> predicate = retrievePredicateForNull(criteriaBuilder, root, searchableFields, expression);
            case NOT_EMPTY -> predicate = criteriaBuilder.isNotNull(expression);
            case GREATER, LESSER -> {
                if (searchCondition.equals(SearchCondition.GREATER)) {
                    predicate = criteriaBuilder.greaterThan(expression.as(Integer.class), Integer.valueOf(dto.getValue().toString()));
                } else {
                    predicate = criteriaBuilder.lessThan(expression.as(Integer.class), Integer.valueOf(dto.getValue().toString()));
                }
            }
        }
        return predicate;
    }

    private static Predicate retrievePredicateForNull(final CriteriaBuilder criteriaBuilder, final Root root, final SearchableFields searchableFields, final Expression expression) {
        if (searchableFields != null && searchableFields.getCode().contains(".")) {
            int indexOfDot = searchableFields.getCode().indexOf(".");
            final String mainPropertyString = searchableFields.getCode().substring(0, indexOfDot);
            final Expression mainExpression = prepareExpression(root, mainPropertyString);
            return criteriaBuilder.isNull(mainExpression);
        } else {
            return criteriaBuilder.isNull(expression);
        }
    }

    private static Predicate prepareDateTimePredicate(final CriteriaBuilder criteriaBuilder, final SearchCondition searchCondition, final Expression expression, final String value, final SearchFieldObject searchFieldObject) {
        Predicate dateTimePredicate = null;
        switch (searchCondition) {
            case EQUALS -> {
                switch (searchFieldObject.getAttributeContentType()) {
                    case DATETIME ->
                            dateTimePredicate = criteriaBuilder.equal(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateTimeFormat(value));
                    case DATE ->
                            dateTimePredicate = criteriaBuilder.equal(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateFormat(value));
                    case TIME ->
                            dateTimePredicate = criteriaBuilder.equal(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalTimeFormat(value));
                }
            }
            case NOT_EQUALS -> {
                switch (searchFieldObject.getAttributeContentType()) {
                    case DATETIME ->
                            dateTimePredicate = criteriaBuilder.notEqual(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateTimeFormat(value));
                    case DATE ->
                            dateTimePredicate = criteriaBuilder.notEqual(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateFormat(value));
                    case TIME ->
                            dateTimePredicate = criteriaBuilder.notEqual(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalTimeFormat(value));
                }
            }
            case GREATER -> {
                switch (searchFieldObject.getAttributeContentType()) {
                    case DATETIME ->
                            dateTimePredicate = criteriaBuilder.greaterThan(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateTimeFormat(value));
                    case DATE ->
                            dateTimePredicate = criteriaBuilder.greaterThan(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateFormat(value));
                    case TIME ->
                            dateTimePredicate = criteriaBuilder.greaterThan(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalTimeFormat(value));
                }
            }
            case LESSER -> {
                switch (searchFieldObject.getAttributeContentType()) {
                    case DATETIME ->
                            dateTimePredicate = criteriaBuilder.lessThan(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateTimeFormat(value));
                    case DATE ->
                            dateTimePredicate = criteriaBuilder.lessThan(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalDateFormat(value));
                    case TIME ->
                            dateTimePredicate = criteriaBuilder.lessThan(expression.as(searchFieldObject.getDateTimeFormatClass()), searchFieldObject.getLocalTimeFormat(value));
                }
            }
        }
        return dateTimePredicate;
    }

    private static SearchCondition checkOrReplaceSearchCondition(final SearchFilterRequestDto dto, final SearchableFields searchableFields) {
        if (searchableFields.getEnumClass() != null
                && searchableFields.getEnumClass().equals(KeyUsage.class)) {
            if (dto.getCondition().equals(SearchCondition.EQUALS)) {
                return SearchCondition.CONTAINS;
            } else if (dto.getCondition().equals(SearchCondition.NOT_EQUALS)) {
                return SearchCondition.NOT_CONTAINS;
            }
        }
        return dto.getCondition();
    }

    private static Expression<?> prepareExpression(final Root root, final String code) {
        final StringTokenizer stz = new StringTokenizer(code, ".");
        Path path = root.get(stz.nextToken());
        while (stz.hasMoreTokens()) {
            path = path.get(stz.nextToken());
        }
        return path;
    }

    private static Join prepareJoin(final Root root, final String joinPath) {
        final StringTokenizer stz = new StringTokenizer(joinPath, ".");
        Join join = root.join(stz.nextToken(), JoinType.LEFT);
        while (stz.hasMoreTokens()) {
            join = join.join(stz.nextToken(), JoinType.LEFT);
        }
        return join;
    }

    private static Object prepareValue(final Object valueObject, final SearchableFields searchableFields) {
        if (searchableFields.getEnumClass() != null) {
            if (searchableFields.getEnumClass().equals(KeyUsage.class)) {
                final KeyUsage keyUsage = (KeyUsage) findEnumByCustomValue(valueObject, searchableFields);
                if (keyUsage != null) {
                    return keyUsage.getBitmask();
                }
            }
            return findEnumByCustomValue(valueObject, searchableFields);
        }
        return valueObject.toString();
    }

    private static Object findEnumByCustomValue(Object valueObject, final SearchableFields searchableFields) {
        Optional<? extends IPlatformEnum> enumItem = Arrays.stream(searchableFields.getEnumClass().getEnumConstants()).filter(enumValue -> enumValue.getCode().equals(valueObject.toString())).findFirst();
        return enumItem.isPresent() ? enumItem.get() : null;
    }

    private static List<Object> readAndCheckIncomingValues(final SearchFilterRequestDto dto) {
        final List<Object> objects = new ArrayList<>();
        if (dto.getValue() instanceof List<?>) {
            objects.addAll((List<Object>) dto.getValue());
        } else {
            objects.add(dto.getValue());
        }
        return objects;
    }

    private static Predicate checkCertificateValidationResult(final Root root, final CriteriaBuilder criteriaBuilder, final SearchFilterRequestDto dto, final Object valueObject, final SearchableFields searchableFields) {
        if (List.of(SearchableFields.OCSP_VALIDATION, SearchableFields.CRL_VALIDATION, SearchableFields.SIGNATURE_VALIDATION).contains(searchableFields)) {
            String textToBeFormatted = null;
            switch (searchableFields) {
                case OCSP_VALIDATION -> textToBeFormatted = OCSP_VERIFICATION;
                case SIGNATURE_VALIDATION -> textToBeFormatted = SIGNATURE_VERIFICATION;
                case CRL_VALIDATION -> textToBeFormatted = CRL_VERIFICATION;
            }
            if (textToBeFormatted != null) {
                switch (dto.getCondition()) {
                    case EQUALS -> {
                        return criteriaBuilder.like(root.get("certificateValidationResult"), formatCertificateVerificationResultByStatus(textToBeFormatted, valueObject.toString()));
                    }
                    case NOT_EQUALS -> {
                        return criteriaBuilder.notLike(root.get("certificateValidationResult"), formatCertificateVerificationResultByStatus(textToBeFormatted, valueObject.toString()));
                    }
                }
            }
        }
        return null;
    }

    private static String formatCertificateVerificationResultByStatus(final String textToBeFormatted, final String statusCode) {
        return textToBeFormatted.replace("%STATUS%", statusCode);
    }

    public static CriteriaQueryDataObject prepareQueryToSearchIntoAttributes(final List<SearchFieldObject> searchableFields, final List<SearchFilterRequestDto> dtos, final CriteriaBuilder criteriaBuilder, final Resource resource) {
        final CriteriaQuery<UUID> criteriaQuery = criteriaBuilder.createQuery(UUID.class);
        final Root<AttributeContent2Object> root = criteriaQuery.from(AttributeContent2Object.class);

        criteriaQuery.distinct(true).select(root.get("objectUuid"));

        final List<Predicate> rootPredicates = new ArrayList<>();

        for (final SearchFilterRequestDto dto : dtos) {
            final SearchGroup searchGroup = dto.getSearchGroup();
            if (searchGroup == SearchGroup.CUSTOM || searchGroup == SearchGroup.META) {

                // --- SUB QUERY ---
                final Subquery<UUID> subquery = criteriaQuery.subquery(UUID.class);
                final Root<AttributeContent2Object> subRoot = subquery.from(AttributeContent2Object.class);
                final Join<AttributeContent2Object, AttributeContentItem> joinAttributeContentItem = subRoot.join("attributeContentItem");

                subquery.select(subRoot.get("objectUuid"));

                final List<Predicate> subPredicates = new ArrayList<>();
                subPredicates.add(criteriaBuilder.equal(subRoot.get("objectType"), resource));

                final String identifier = dto.getFieldIdentifier();
                final String[] fieldIdentifier = identifier.split("\\|");
                final AttributeContentType fieldAttributeContentType = AttributeContentType.valueOf(fieldIdentifier[1]);
                final String fieldIdentifierName = fieldIdentifier[0];
                final Optional<SearchFieldObject> searchFieldObject =
                        searchableFields.stream().filter(attr ->
                                attr.getAttributeType().equals(searchGroup.getAttributeType())
                                        && attr.getAttributeName().equals(fieldIdentifierName)
                                        && attr.getAttributeContentType().equals(fieldAttributeContentType)).findFirst();

                if (searchFieldObject.isPresent()) {

                    final SearchFieldObject searchField = searchFieldObject.get();

                    final Subquery<String> jsonValueQuery = subquery.subquery(String.class);
                    final Root subACIRoot = jsonValueQuery.from(AttributeContentItem.class);

                    final Expression expressionFunctionToGetJsonValue = criteriaBuilder.function("jsonb_extract_path_text", String.class, subACIRoot.get("json"),
                            criteriaBuilder.literal(searchField.getAttributeContentType().isFilterByData() ? "data" : "reference"));

                    final Predicate predicateForContentType = criteriaBuilder.equal(prepareExpression(subACIRoot, "attributeDefinition.contentType"), searchField.getAttributeContentType());
                    final Predicate predicateToKeepRelationWithUpperQuery = criteriaBuilder.equal(subACIRoot.get("uuid"), joinAttributeContentItem.get("uuid"));
                    final Predicate predicateGroup = criteriaBuilder.equal(prepareExpression(subACIRoot, "attributeDefinition.type"), searchField.getAttributeType());
                    final Predicate predicateAttributeName = criteriaBuilder.equal(prepareExpression(subACIRoot, "attributeDefinition.name"), fieldIdentifierName);

                    jsonValueQuery.select(expressionFunctionToGetJsonValue);
                    jsonValueQuery.where(predicateForContentType, predicateToKeepRelationWithUpperQuery, predicateAttributeName, predicateGroup);

                    final Predicate predicateOfTheExpression =
                            buildPredicateByCondition(criteriaBuilder, dto.getCondition(), jsonValueQuery, null, null, null, searchField.isDateTimeFormat(), searchField.isBooleanFormat(), dto, searchField);

                    subPredicates.add(predicateOfTheExpression);
                    subquery.where(subPredicates.toArray(new Predicate[]{}));
                    rootPredicates.add(criteriaBuilder.in(root.get("objectUuid")).value(subquery));
                }
            }
        }

        final CriteriaQueryDataObject cqdo = new CriteriaQueryDataObject();
        cqdo.setRoot(root);
        cqdo.setCriteriaQuery(criteriaQuery);
        cqdo.setPredicate(criteriaBuilder.and(rootPredicates.toArray(new Predicate[]{})));
        return cqdo;
    }

    public static Predicate constructFilterForJobHistory(final CriteriaBuilder cb, final Root<ScheduledJobHistory> root, final UUID scheduledJobUuid) {
        final Expression<?> expressionPath = prepareExpression(root, "scheduledJobUuid");
        return cb.equal(expressionPath, scheduledJobUuid);
    }


    public static class CriteriaQueryDataObject {

        private CriteriaQuery criteriaQuery;

        private Root root;

        private Predicate predicate;

        public CriteriaQuery getCriteriaQuery() {
            return criteriaQuery;
        }

        public void setCriteriaQuery(CriteriaQuery criteriaQuery) {
            this.criteriaQuery = criteriaQuery;
        }

        public Predicate getPredicate() {
            return predicate;
        }

        public void setPredicate(Predicate predicate) {
            this.predicate = predicate;
        }

        public Root getRoot() {
            return root;
        }

        public void setRoot(Root root) {
            this.root = root;
        }
    }


}


