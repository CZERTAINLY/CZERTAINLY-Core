package com.czertainly.core.util;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.enums.IPlatformEnum;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.enums.SearchFieldTypeEnum;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FilterPredicatesBuilder {
    public static <TRootEntity> Predicate getFiltersPredicate(final CriteriaBuilder criteriaBuilder, final CriteriaQuery query, final Root<TRootEntity> root, final List<SearchFilterRequestDto> filterDtos) {
        Map<String, From> joinedAssociations = new HashMap<>();

        List<Predicate> predicates = new ArrayList<>();
        for (SearchFilterRequestDto filterDto : filterDtos) {
            if (filterDto.getFieldSource() == FilterFieldSource.PROPERTY) {
                predicates.add(getPropertyFilterPredicate(criteriaBuilder, query, root, filterDto, joinedAssociations));
            }
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[]{}));
    }

    private static <TRootEntity> Predicate getPropertyFilterPredicate(final CriteriaBuilder criteriaBuilder, final CriteriaQuery query, final Root<TRootEntity> root, SearchFilterRequestDto filterDto, Map<String, From> joinedAssociations) {
        final FilterField filterField = FilterField.valueOf(filterDto.getFieldIdentifier());

        // join associations
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

        // prepare filter values, expression and set filter characteristics
        List<Object> filterValues = prepareFilterValues(filterDto, filterField);
        Expression expression = from.get(filterField.getFieldAttribute().getName());
        if (filterField.getType().getExpressionClass() != null && filterField.getExpectedValue() == null) {
            expression = expression.as(filterField.getType().getExpressionClass());
        }

        boolean multipleValues = filterValues != null && filterValues.size() > 1;
        boolean hasParent = !filterField.getJoinAttributes().isEmpty() && filterField.getFieldResource() != Resource.USER;
//        boolean hasParent = !filterField.getJoinAttributes().isEmpty() && filterField.getFieldResource() != Resource.USER && filterField.getFieldResource() != Resource.GROUP; // workaround for owner => fieldResource = USER
        boolean isParentCollection = hasParent && filterField.getJoinAttributes().getLast().isCollection();
        PluralAttribute.CollectionType parentCollectionType = hasParent && isParentCollection ? ((PluralAttribute) filterField.getJoinAttributes().getLast()).getCollectionType() : null;
        if (parentCollectionType == PluralAttribute.CollectionType.SET || parentCollectionType == PluralAttribute.CollectionType.MAP) {
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
            case NOT_EQUALS -> predicate = filterField.getFieldResource() == Resource.GROUP
                    ? getGroupNotExistPredicate(criteriaBuilder, query, root, filterField.getFieldAttribute(), filterValues, filterField.getRootResource())
                    : criteriaBuilder.or(getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection, parentCollectionType), multipleValues ? criteriaBuilder.not(expression.in(filterValues)) : criteriaBuilder.notEqual(expression, filterValues.getFirst()));
            case STARTS_WITH -> predicate = criteriaBuilder.like(expression, filterValues.getFirst() + "%");
            case ENDS_WITH -> predicate = criteriaBuilder.like(expression, "%" + filterValues.getFirst());
            case CONTAINS -> predicate = criteriaBuilder.like(expression, "%" + filterValues.getFirst() + "%");
            case NOT_CONTAINS ->
                    predicate = criteriaBuilder.or(getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection, parentCollectionType),
                            criteriaBuilder.notLike(expression, "%" + filterValues.getFirst() + "%"));
            case EMPTY ->
                    predicate = getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection, parentCollectionType);
            case NOT_EMPTY ->
                    predicate = criteriaBuilder.not(getNotPresentPredicate(criteriaBuilder, from, expression, hasParent, isParentCollection, parentCollectionType));
            case GREATER ->
                    predicate = criteriaBuilder.greaterThan(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
            case GREATER_OR_EQUAL ->
                    predicate = criteriaBuilder.greaterThanOrEqualTo(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
            case LESSER ->
                    predicate = criteriaBuilder.lessThan(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
            case LESSER_OR_EQUAL ->
                    predicate = criteriaBuilder.lessThanOrEqualTo(expression, (Expression) criteriaBuilder.literal(filterValues.getFirst()));
        }
        return predicate;
    }

    private static List<Object> prepareFilterValues(final SearchFilterRequestDto filterDto, final FilterField filterField) {
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
                    preparedFilterValue = LocalDateTime.parse(stringValue, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
                } else {
                    preparedFilterValue = stringValue;
                }
            }

            preparedFilterValues.add(preparedFilterValue);
        }

        return preparedFilterValues;
    }

    private static Predicate getGroupNotExistPredicate(final CriteriaBuilder criteriaBuilder, final CriteriaQuery query, Root originalRoot, Attribute fieldAttribute, List<Object> filterValues, Resource resource) {
        final Subquery<Integer> subquery = query.subquery(Integer.class);
        final Root<GroupAssociation> subqueryRoot = subquery.from(GroupAssociation.class);
        final Join joinGroup = subqueryRoot.join(GroupAssociation_.group);
        subquery.select(criteriaBuilder.literal(1)).where(
                criteriaBuilder.equal(subqueryRoot.get(ResourceObjectAssociation_.resource), resource),
                criteriaBuilder.equal(subqueryRoot.get(ResourceObjectAssociation_.objectUuid), originalRoot.get(UniquelyIdentified_.uuid)),
                joinGroup.get(fieldAttribute.getName()).in(filterValues));

        return criteriaBuilder.not(criteriaBuilder.exists(subquery));
    }

    private static Predicate getNotPresentPredicate(final CriteriaBuilder criteriaBuilder, From from, Expression expression, boolean hasParent, boolean isParentCollection, PluralAttribute.CollectionType parentCollectionType) {
        if (!hasParent) {
            return criteriaBuilder.isNull(expression);
        }
        if (!isParentCollection) {
            return criteriaBuilder.isNull(from);
        }

        return criteriaBuilder.isEmpty(from);

//        return parentCollectionType == PluralAttribute.CollectionType.LIST || parentCollectionType == PluralAttribute.CollectionType.COLLECTION ? criteriaBuilder.isEmpty(from) : criteriaBuilder.equal(criteriaBuilder.size(from), criteriaBuilder.literal(0));
    }

    private static Object findEnumByCustomValue(Object valueObject, final Class<? extends IPlatformEnum> enumClass) {
        Optional<? extends IPlatformEnum> enumItem = Arrays.stream(enumClass.getEnumConstants()).filter(enumValue -> enumValue.getCode().equals(valueObject.toString())).findFirst();
        return enumItem.orElse(null);
    }
}
