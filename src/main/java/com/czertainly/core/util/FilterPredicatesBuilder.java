package com.czertainly.core.util;

import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.enums.ResourceToClass;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import org.hibernate.query.criteria.JpaExpression;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FilterPredicatesBuilder {

    private FilterPredicatesBuilder() {
        throw new IllegalStateException("Static utility class");
    }

    private static final List<AttributeContentType> castedAttributeContentData = List.of(AttributeContentType.INTEGER, AttributeContentType.FLOAT, AttributeContentType.DATE, AttributeContentType.TIME, AttributeContentType.DATETIME);
    private static final String JSONB_EXTRACT_PATH_TEXT_FUNCTION_NAME = "jsonb_extract_path_text";

    public static <T> Predicate getFiltersPredicate(final CriteriaBuilder criteriaBuilder, final CommonAbstractCriteria query, final Root<T> root, final List<SearchFilterRequestDto> filterDtos) {
        Map<String, From> joinedAssociations = new HashMap<>();

        List<Predicate> predicates = new ArrayList<>();
        for (SearchFilterRequestDto filterDto : filterDtos) {
            if (filterDto.getFieldSource() == FilterFieldSource.PROPERTY) {
                predicates.add(getPropertyFilterPredicate(criteriaBuilder, query, root, filterDto, joinedAssociations));
            } else {
                predicates.add(getAttributeFilterPredicate(criteriaBuilder, query, root, filterDto));
            }
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[]{}));
    }

    private static <T> Predicate getAttributeFilterPredicate(final CriteriaBuilder criteriaBuilder, final CommonAbstractCriteria query, final Root<T> root, final SearchFilterRequestDto filterDto) {
        final Subquery<Integer> subquery = query.subquery(Integer.class);
        final Root<AttributeContent2Object> subqueryRoot = subquery.from(AttributeContent2Object.class);
        final Join joinContentItem = subqueryRoot.join(AttributeContent2Object_.attributeContentItem, JoinType.INNER);
        final Join joinDefinition = joinContentItem.join(AttributeContentItem_.attributeDefinition, JoinType.INNER);

        final Resource resource = ResourceToClass.getResourceByClass(root.getJavaType());
        final String identifier = filterDto.getFieldIdentifier();
        final String[] fieldIdentifier = identifier.split("\\|");
        final AttributeContentType contentType = AttributeContentType.valueOf(fieldIdentifier[1]);
        final String attributeName = fieldIdentifier[0];
        final boolean isNotExistCondition = List.of(FilterConditionOperator.NOT_EQUALS, FilterConditionOperator.NOT_CONTAINS, FilterConditionOperator.EMPTY).contains(filterDto.getCondition());

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(joinDefinition.get(AttributeDefinition_.type), filterDto.getFieldSource().getAttributeType()));
        predicates.add(criteriaBuilder.equal(joinDefinition.get(AttributeDefinition_.contentType), contentType));
        predicates.add(criteriaBuilder.equal(joinDefinition.get(AttributeDefinition_.name), attributeName));
        predicates.add(criteriaBuilder.equal(subqueryRoot.get(AttributeContent2Object_.objectType), resource));
        predicates.add(criteriaBuilder.equal(subqueryRoot.get(AttributeContent2Object_.objectUuid), root.get(UniquelyIdentified_.uuid.getName())));

        if (filterDto.getCondition() != FilterConditionOperator.EMPTY && filterDto.getCondition() != FilterConditionOperator.NOT_EMPTY) {
            Expression<String> attributeContentExpression = criteriaBuilder.function(JSONB_EXTRACT_PATH_TEXT_FUNCTION_NAME, String.class, joinContentItem.get(AttributeContentItem_.json), criteriaBuilder.literal(contentType.isFilterByData() ? "data" : "reference"));
            CriteriaBuilder.SimpleCase<AttributeContentType, Object> contentTypeCaseExpression = criteriaBuilder.selectCase(joinDefinition.get(AttributeDefinition_.contentType));

            if (castedAttributeContentData.contains(contentType)) {
                contentTypeCaseExpression.when(contentType, ((JpaExpression) attributeContentExpression).cast(contentType.getContentDataClass())).otherwise(criteriaBuilder.nullLiteral(contentType.getContentDataClass()));
            } else {
                contentTypeCaseExpression.when(contentType, attributeContentExpression).otherwise(criteriaBuilder.nullLiteral(String.class));
            }

            Predicate conditionPredicate = getAttributeFilterConditionPredicate(criteriaBuilder, filterDto, contentTypeCaseExpression, contentType);
            predicates.add(conditionPredicate);
        }

        subquery.select(criteriaBuilder.literal(1)).where(predicates.toArray(new Predicate[]{}));
        return isNotExistCondition ? criteriaBuilder.not(criteriaBuilder.exists(subquery)) : criteriaBuilder.exists(subquery);
    }

    private static Predicate getAttributeFilterConditionPredicate(final CriteriaBuilder criteriaBuilder, final SearchFilterRequestDto filterDto, final Expression expression, final AttributeContentType contentType) {
        List<Object> filterValues = prepareAttributeFilterValues(filterDto, contentType);
        boolean multipleValues = filterValues.size() > 1;

        Object filterValue = filterValues.isEmpty() ? null : filterValues.getFirst();
        FilterConditionOperator conditionOperator = (filterDto.getCondition() == FilterConditionOperator.NOT_EQUALS) ? FilterConditionOperator.EQUALS : ((filterDto.getCondition() == FilterConditionOperator.NOT_CONTAINS) ? FilterConditionOperator.CONTAINS : filterDto.getCondition());
        return switch (conditionOperator) {
            case EQUALS ->
                    multipleValues ? expression.in(filterValues) : criteriaBuilder.equal(expression, filterValue);
            case STARTS_WITH -> criteriaBuilder.like(expression, filterValue + "%");
            case ENDS_WITH -> criteriaBuilder.like(expression, "%" + filterValue);
            case CONTAINS -> criteriaBuilder.like(expression, "%" + filterValue + "%");
            case GREATER -> criteriaBuilder.greaterThan(expression, (Expression) criteriaBuilder.literal(filterValue));
            case GREATER_OR_EQUAL ->
                    criteriaBuilder.greaterThanOrEqualTo(expression, (Expression) criteriaBuilder.literal(filterValue));
            case LESSER -> criteriaBuilder.lessThan(expression, (Expression) criteriaBuilder.literal(filterValue));
            case LESSER_OR_EQUAL ->
                    criteriaBuilder.lessThanOrEqualTo(expression, (Expression) criteriaBuilder.literal(filterValue));
            case null, default -> null;
        };
    }

    private static List<Object> prepareAttributeFilterValues(final SearchFilterRequestDto filterDto, final AttributeContentType contentType) {
        Serializable filterValue = filterDto.getValue();

        if (filterValue == null) {
            return List.of();
        }

        final List<Object> preparedFilterValues = new ArrayList<>();
        List<Object> filterValues = filterValue instanceof List<?> ? (List<Object>) filterValue : List.of(filterValue);
        for (Object value : filterValues) {
            String stringValue = value.toString();
            Object preparedValue = switch (contentType) {
                case BOOLEAN -> Boolean.parseBoolean(stringValue) ? "true" : "false";
                case INTEGER -> Integer.parseInt(stringValue);
                case FLOAT -> Float.parseFloat(stringValue);
                case DATE -> LocalDate.parse(stringValue);
                case TIME -> LocalTime.parse(stringValue);
                case DATETIME -> {
                    if (!stringValue.contains("+") && !stringValue.endsWith("Z")) {
                        stringValue += "Z";
                    }
                    yield ZonedDateTime.parse(stringValue, DateTimeFormatter.ofPattern("[yyyy-MM-dd'T'HH:mm:ss.SSSXXX][yyyy-MM-dd'T'HH:mm:ssXXX][yyyy-MM-dd'T'HH:mmXXX]"));
                }
                case null, default -> stringValue;
            };

            preparedFilterValues.add(preparedValue);
        }

        return preparedFilterValues;
    }

    private static <T> Predicate getPropertyFilterPredicate(final CriteriaBuilder criteriaBuilder, final CommonAbstractCriteria query, final Root<T> root, SearchFilterRequestDto filterDto, Map<String, From> joinedAssociations) {
        final FilterField filterField = FilterField.valueOf(filterDto.getFieldIdentifier());

        From from = getJoinedAssociation(root, joinedAssociations, filterField);

        // prepare filter values, expression and set filter characteristics
        List<Object> filterValues = preparePropertyFilterValues(filterDto, filterField);
        Expression expression = from.get(filterField.getFieldAttribute().getName());

        if (filterField.getJsonPath() != null) {
            expression = switch (filterField.getJsonPath().length) {
                case 1 ->
                        criteriaBuilder.function(JSONB_EXTRACT_PATH_TEXT_FUNCTION_NAME, String.class, from.get(filterField.getFieldAttribute().getName()), criteriaBuilder.literal(filterField.getJsonPath()[0]));
                case 2 ->
                        criteriaBuilder.function(JSONB_EXTRACT_PATH_TEXT_FUNCTION_NAME, String.class, from.get(filterField.getFieldAttribute().getName()), criteriaBuilder.literal(filterField.getJsonPath()[0]), criteriaBuilder.literal(filterField.getJsonPath()[1]));
                case 3 ->
                        criteriaBuilder.function(JSONB_EXTRACT_PATH_TEXT_FUNCTION_NAME, String.class, from.get(filterField.getFieldAttribute().getName()), criteriaBuilder.literal(filterField.getJsonPath()[0]), criteriaBuilder.literal(filterField.getJsonPath()[1]), criteriaBuilder.literal(filterField.getJsonPath()[2]));
                case 4 ->
                        criteriaBuilder.function(JSONB_EXTRACT_PATH_TEXT_FUNCTION_NAME, String.class, from.get(filterField.getFieldAttribute().getName()), criteriaBuilder.literal(filterField.getJsonPath()[0]), criteriaBuilder.literal(filterField.getJsonPath()[1]), criteriaBuilder.literal(filterField.getJsonPath()[2]), criteriaBuilder.literal(filterField.getJsonPath()[3]));
                default ->
                        throw new ValidationException("Unexpected size of JSON path `%s`: %d".formatted(filterField.getJsonPath(), filterField.getJsonPath().length));
            };
        } else if (filterField.getType().getExpressionClass() != null && filterField.getExpectedValue() == null) {
            expression = ((JpaExpression) expression).cast(filterField.getType().getExpressionClass());
        }

        boolean multipleValues = filterValues.size() > 1;
        boolean hasParent = !filterField.getJoinAttributes().isEmpty() && filterField.getFieldResource() != Resource.USER; // workaround for owner => fieldResource = USER
        boolean isParentCollection = hasParent && filterField.getJoinAttributes().getLast().isCollection();
        PluralAttribute.CollectionType parentCollectionType = hasParent && isParentCollection ? ((PluralAttribute) filterField.getJoinAttributes().getLast()).getCollectionType() : null;

        // workaround for set attributes associations
        if (parentCollectionType == PluralAttribute.CollectionType.SET) {
            hasParent = false;
        }

        Predicate predicate = null;
        FilterConditionOperator conditionOperator = filterDto.getCondition();

        // update condition operator if filter is based on comparing field to expected value
        if (filterField.getExpectedValue() != null) {
            final Boolean booleanValue = Boolean.parseBoolean(filterDto.getValue().toString());
            if (conditionOperator == FilterConditionOperator.EQUALS && Boolean.FALSE.equals(booleanValue)) {
                conditionOperator = FilterConditionOperator.NOT_EQUALS;
            } else if (conditionOperator == FilterConditionOperator.NOT_EQUALS && Boolean.FALSE.equals(booleanValue)) {
                conditionOperator = FilterConditionOperator.EQUALS;
            }
        }
        switch (conditionOperator) {
            case EQUALS ->
                    predicate = multipleValues ? expression.in(filterValues) : criteriaBuilder.equal(expression, filterValues.getFirst());
            case NOT_EQUALS -> {
                // hack how to filter out correctly Has private key property filter for certificate. Needs to find correct solution for SET attributes predicates!
                if (filterField.getExpectedValue() != null && filterField == FilterField.PRIVATE_KEY) {
                    predicate = criteriaBuilder.or(criteriaBuilder.and(criteriaBuilder.notEqual(expression, filterValues.getFirst()), criteriaBuilder.equal(expression, filterValues.getFirst())), criteriaBuilder.isNull(expression));
                } else {
                    predicate = filterField.getFieldResource() == Resource.GROUP
                            ? getGroupNotExistPredicate(criteriaBuilder, query, root, filterField.getFieldAttribute(), filterValues, filterField.getRootResource())
                            : criteriaBuilder.or(getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection), multipleValues ? criteriaBuilder.not(expression.in(filterValues)) : criteriaBuilder.notEqual(expression, filterValues.getFirst()));
                }
            }
            case STARTS_WITH -> predicate = criteriaBuilder.like(expression, filterValues.getFirst() + "%");
            case ENDS_WITH -> predicate = criteriaBuilder.like(expression, "%" + filterValues.getFirst());
            case CONTAINS -> predicate = criteriaBuilder.like(expression, "%" + filterValues.getFirst() + "%");
            case NOT_CONTAINS ->
                    predicate = criteriaBuilder.or(getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection),
                            criteriaBuilder.notLike(expression, "%" + filterValues.getFirst() + "%"));
            case EMPTY ->
                    predicate = getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection);
            case NOT_EMPTY ->
                    predicate = criteriaBuilder.not(getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection));
            case GREATER ->
                    predicate = criteriaBuilder.greaterThan(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
            case GREATER_OR_EQUAL ->
                    predicate = criteriaBuilder.greaterThanOrEqualTo(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
            case LESSER ->
                    predicate = criteriaBuilder.lessThan(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
            case LESSER_OR_EQUAL ->
                    predicate = criteriaBuilder.lessThanOrEqualTo(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
            default -> throw new ValidationException("Unexpected value: " + conditionOperator);
        }
        return predicate;
    }

    private static <T> From getJoinedAssociation(Root<T> root, Map<String, From> joinedAssociations, FilterField filterField) {
        From from = root;
        From joinedAssociation;
        String associationFullPath = null;
        for (Attribute joinAttribute : filterField.getJoinAttributes()) {
            associationFullPath = associationFullPath == null ? joinAttribute.getName() : associationFullPath + "." + joinAttribute.getName();
            joinedAssociation = joinedAssociations.get(associationFullPath);

            if (joinedAssociation != null) {
                from = joinedAssociation;
            } else {
                from = from.join(joinAttribute.getName(), JoinType.LEFT);
                joinedAssociations.put(associationFullPath, from);
            }
        }
        return from;
    }

    private static List<Object> preparePropertyFilterValues(final SearchFilterRequestDto filterDto, final FilterField filterField) {
        Serializable filterValue = filterDto.getValue();

        if (filterValue == null) {
            return List.of();
        }

        final List<Object> preparedFilterValues = new ArrayList<>();
        List<Object> filterValues = filterValue instanceof List<?> ? (List<Object>) filterValue : List.of(filterValue);
        for (Object value : filterValues) {
            Object preparedFilterValue = null;
            if (filterField.getEnumClass() != null) {
                if (filterField.getEnumClass().equals(KeyUsage.class)) {
                    final KeyUsage keyUsage = (KeyUsage) findEnumByCustomValue(value, filterField.getEnumClass());
                    if (keyUsage != null) {
                        preparedFilterValue = keyUsage.getBitmask();
                    }
                } else {
                    preparedFilterValue = findEnumByCustomValue(value, filterField.getEnumClass());
                }
            } else {
                String stringValue = value.toString();
                if (filterField.getType() == SearchFieldTypeEnum.BOOLEAN) {
                    var boolValue = Boolean.parseBoolean(stringValue);
                    if (filterField.getExpectedValue() == null) {
                        preparedFilterValue = boolValue;
                    } else {
                        preparedFilterValue = filterField.getExpectedValue();
                    }
                } else if (filterField.getType() == SearchFieldTypeEnum.NUMBER) {
                    preparedFilterValue = stringValue.contains(".") ? Float.parseFloat(stringValue) : Integer.parseInt(stringValue);
                } else if (filterField.getType() == SearchFieldTypeEnum.DATE) {
                    preparedFilterValue = LocalDate.parse(stringValue);
                } else if (filterField.getType() == SearchFieldTypeEnum.DATETIME) {
                    preparedFilterValue = LocalDateTime.parse(stringValue, DateTimeFormatter.ofPattern("[yyyy-MM-dd'T'HH:mm:ss.SSSXXX][yyyy-MM-dd'T'HH:mm:ssXXX][yyyy-MM-dd'T'HH:mmXXX]"));
                } else {
                    preparedFilterValue = stringValue;
                }
            }

            preparedFilterValues.add(preparedFilterValue);
        }

        return preparedFilterValues;
    }

    private static Predicate getGroupNotExistPredicate(final CriteriaBuilder criteriaBuilder, final CommonAbstractCriteria query, Root originalRoot, Attribute fieldAttribute, List<Object> filterValues, Resource resource) {
        final Subquery<Integer> subquery = query.subquery(Integer.class);
        final Root<GroupAssociation> subqueryRoot = subquery.from(GroupAssociation.class);
        final Join joinGroup = subqueryRoot.join(GroupAssociation_.group);
        subquery.select(criteriaBuilder.literal(1)).where(
                criteriaBuilder.equal(subqueryRoot.get(ResourceObjectAssociation_.resource), resource),
                criteriaBuilder.equal(subqueryRoot.get(ResourceObjectAssociation_.objectUuid), resource == Resource.CRYPTOGRAPHIC_KEY ? originalRoot.get(CryptographicKeyItem_.keyUuid) : originalRoot.get(UniquelyIdentified_.uuid)),
                joinGroup.get(fieldAttribute.getName()).in(filterValues));

        return criteriaBuilder.not(criteriaBuilder.exists(subquery));
    }

    private static Predicate getNotPresentPredicate(final CriteriaBuilder criteriaBuilder, From from, Expression expression, boolean hasParent, boolean isParentCollection) {
        if (!hasParent) {
            return criteriaBuilder.isNull(expression);
        }
        if (!isParentCollection) {
            return criteriaBuilder.isNull(from);
        }

        return criteriaBuilder.isEmpty(from);
    }

    private static Object findEnumByCustomValue(Object valueObject, final Class<? extends IPlatformEnum> enumClass) {
        Optional<? extends IPlatformEnum> enumItem = Arrays.stream(enumClass.getEnumConstants()).filter(enumValue -> enumValue.getCode().equals(valueObject.toString())).findFirst();
        return enumItem.orElse(null);
    }

    public static Join prepareJoin(final Root root, final String joinPath) {
        final StringTokenizer stz = new StringTokenizer(joinPath, ".");
        Join join = root.join(stz.nextToken(), JoinType.LEFT);
        while (stz.hasMoreTokens()) {
            join = join.join(stz.nextToken(), JoinType.LEFT);
        }
        return join;
    }

    public static Expression<?> prepareExpression(final From from, final String attributeName) {
        final StringTokenizer stz = new StringTokenizer(attributeName, ".");
        Path path = from.get(stz.nextToken());
        while (stz.hasMoreTokens()) {
            path = path.get(stz.nextToken());
        }
        return path;
    }

    public static Query getAllValuesOfProperty(String property, Resource resource, EntityManager entityManager) {
        Class resourceClass = ResourceToClass.getClassByResource(resource);
        return entityManager.createQuery("SELECT DISTINCT " + property + " FROM " + resourceClass.getName());
    }

    public static Predicate constructFilterForJobHistory(final CriteriaBuilder cb, final Root<ScheduledJobHistory> root, final UUID scheduledJobUuid) {
        final Expression<?> expressionPath = prepareExpression(root, "scheduledJobUuid");
        return cb.equal(expressionPath, scheduledJobUuid);
    }

    public static String buildPathToProperty(FilterField filterField, boolean alreadyNested) {
        StringBuilder pathToPropertyBuilder = new StringBuilder();
        if (filterField.getJoinAttributes() != null) {
            List<String> joinAttributes = new ArrayList<>(filterField.getJoinAttributes().stream().map(Attribute::getName).toList());
            if (alreadyNested) joinAttributes.removeFirst();
            if (!joinAttributes.isEmpty()) {
                for (String property : joinAttributes) {
                    pathToPropertyBuilder.append(property).append(".");
                }
            }
        }
        pathToPropertyBuilder.append(filterField.getFieldAttribute().getName());
        return pathToPropertyBuilder.toString();
    }
}
