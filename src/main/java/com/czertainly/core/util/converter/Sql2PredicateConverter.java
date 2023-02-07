package com.czertainly.core.util.converter;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.search.SearchCondition;
import jakarta.persistence.criteria.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

public class Sql2PredicateConverter {

    public static Predicate mapSearchFilter2Predicates(final List<SearchFilterRequestDto> dtos, final CriteriaBuilder criteriaBuilder, final Root root) {
        final List<Predicate> predicates = new ArrayList<>();
        for (final SearchFilterRequestDto dto : dtos) {
            predicates.add(mapSearchFilter2Predicate(dto, criteriaBuilder, root));
        }
        return criteriaBuilder.and(predicates.toArray(new Predicate[]{}));
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
        Predicate predicate = null;
        final SearchCondition searchCondition = checkOrReplaceSearchConfition(dto);
        switch (searchCondition) {
            case EQUALS ->
                    predicate = criteriaBuilder.equal(prepareExpression(root, dto.getField().getCode()), prepareValue(dto, valueObject));
            case NOT_EQUALS ->
                    predicate = criteriaBuilder.notEqual(prepareExpression(root, dto.getField().getCode()), prepareValue(dto, valueObject));
            case STARTS_WITH ->
                    predicate = criteriaBuilder.like((Expression<String>) prepareExpression(root, dto.getField().getCode()), prepareValue(dto, valueObject) + "%");
            case ENDS_WITH ->
                    predicate = criteriaBuilder.like((Expression<String>) prepareExpression(root, dto.getField().getCode()), "%" + prepareValue(dto, valueObject));
            case CONTAINS ->
                    predicate = criteriaBuilder.like((Expression<String>) prepareExpression(root, dto.getField().getCode()), "%" + prepareValue(dto, valueObject) + "%");
            case NOT_CONTAINS -> predicate = criteriaBuilder.or(
                    criteriaBuilder.notLike((Expression<String>) prepareExpression(root, dto.getField().getCode()), "%" + prepareValue(dto, valueObject) + "%"),
                    criteriaBuilder.isNull(prepareExpression(root, dto.getField().getCode()))
            );
            case EMPTY -> predicate = criteriaBuilder.isNull(prepareExpression(root, dto.getField().getCode()));
            case NOT_EMPTY -> predicate = criteriaBuilder.isNotNull(prepareExpression(root, dto.getField().getCode()));
            case GREATER ->
                    predicate = criteriaBuilder.greaterThan(prepareExpression(root, dto.getField().getCode()).as(Integer.class), Integer.parseInt(dto.getValue().toString()));
            case LESSER ->
                    predicate = criteriaBuilder.lessThan(prepareExpression(root, dto.getField().getCode()).as(Integer.class), Integer.parseInt(dto.getValue().toString()));
        }
        return predicate;
    }

    private static SearchCondition checkOrReplaceSearchConfition(final SearchFilterRequestDto dto) {
        if (dto.getField().getEnumClass().equals(KeyUsage.class)) {
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

    private static Object prepareValue(final SearchFilterRequestDto dto, final Object valueObject) {
        if (dto.getField().getEnumClass() != null) {
            if (dto.getField().getEnumClass().equals(KeyUsage.class)) {
                final KeyUsage keyUsage = (KeyUsage) findEnumByCustomValue(dto, valueObject);
                return keyUsage.getId();
            }
            return findEnumByCustomValue(dto, valueObject);
        }
        return valueObject.toString();
    }

    private static Object findEnumByCustomValue(SearchFilterRequestDto dto, Object valueObject) {
        return Arrays.stream(dto.getField().getEnumClass().getEnumConstants()).filter(enumValue -> enumValue.getEnumLabel().equals(valueObject.toString())).findFirst().get();
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


}
