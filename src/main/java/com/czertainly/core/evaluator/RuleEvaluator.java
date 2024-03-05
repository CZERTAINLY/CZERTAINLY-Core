package com.czertainly.core.evaluator;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.FilterFieldType;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.dao.entity.Rule;
import com.czertainly.core.dao.entity.RuleCondition;
import com.czertainly.core.dao.entity.RuleConditionGroup;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class RuleEvaluator<T> implements IRuleEvaluator<T> {

//    @Autowired
//    SecurityFilterRepository<T, Long> repository;

    @Override
    public boolean evaluateRule(Rule rule, T object) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        for (RuleCondition condition : rule.getConditions()) {
            if (evaluateCondition(condition, object)) return false;
        }

        for (RuleConditionGroup conditionGroup : rule.getConditionGroups()) {
            for (RuleCondition condition : conditionGroup.getConditions()) {
                if (evaluateCondition(condition, object)) return false;
            }
        }

        return true;
    }

    @Override
    public boolean evaluateRuleOnList(Rule rule, List<T> list) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        for (T object : list) {
            if (!evaluateRule(rule, object)) return false;
        }
        return true;
    }

    @Override
    public boolean evaluateCondition(RuleCondition condition, T object) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        FilterFieldSource fieldSource = condition.getFieldSource();
        String fieldIdentifier = condition.getFieldIdentifier();
        FilterConditionOperator operator = condition.getOperator();
        Object conditionValue = condition.getValue();
        switch (fieldSource) {
            case PROPERTY -> {
                String getterName = "get" + Character.toUpperCase(fieldIdentifier.charAt(0)) + fieldIdentifier.substring(1);
                Method getter = object.getClass().getMethod(getterName);
                Object objectValue = getter.invoke(object);
                SearchFieldNameEnum propertyEnum = SearchFieldNameEnum.getEnumBySearchableFields(SearchableFields.fromCode(fieldIdentifier));
                FilterFieldType fieldType = propertyEnum.getFieldTypeEnum().getFieldType();
                switch (operator) {
                    case EQUALS -> {
                        return objectValue == conditionValue;
                    }
                    case NOT_EQUALS -> {
                        return objectValue != conditionValue;
                    }
                    case CONTAINS -> {
                        return objectValue.toString().contains(conditionValue.toString());
                    }
                    case NOT_CONTAINS -> {
                        return !((objectValue.toString()).contains(conditionValue.toString()));
                    }
                    case STARTS_WITH -> {
                        return (objectValue.toString()).startsWith(conditionValue.toString());
                    }

                    case ENDS_WITH -> {
                        return !(objectValue.toString()).startsWith(conditionValue.toString());
                    }

                    case EMPTY -> {
                        return objectValue == null;
                    }

                    case NOT_EMPTY -> {
                        return objectValue != null;
                    }
                    case LESSER -> {
                        if (fieldType == FilterFieldType.NUMBER) return (float) objectValue < (float) conditionValue;
                        if (fieldType == FilterFieldType.DATE) return ((Date) objectValue).before((Date) conditionValue);
                        if (fieldType == FilterFieldType.DATETIME) return ((LocalDateTime) objectValue).isBefore((LocalDateTime) conditionValue);
                    }

                    case LESSER_OR_EQUAL -> {
                        if (fieldType == FilterFieldType.NUMBER) return (float) objectValue <= (float) conditionValue;
                        if (fieldType == FilterFieldType.DATE) return !((Date) objectValue).after((Date) conditionValue);
                        if (fieldType == FilterFieldType.DATETIME) return !((LocalDateTime) objectValue).isAfter((LocalDateTime) conditionValue);

                    }

                    case GREATER -> {
                        if (fieldType == FilterFieldType.NUMBER) return (float) objectValue > (float) conditionValue;
                        if (fieldType == FilterFieldType.DATE) return ((Date) objectValue).after((Date) conditionValue);
                        if (fieldType == FilterFieldType.DATETIME) return ((LocalDateTime) objectValue).isAfter((LocalDateTime) conditionValue);
                    }

                    case GREATER_OR_EQUAL -> {
                        if (fieldType == FilterFieldType.NUMBER) return (float) objectValue >= (float) conditionValue;
                        if (fieldType == FilterFieldType.DATE) return !((Date) objectValue).before((Date) conditionValue);
                        if (fieldType == FilterFieldType.DATETIME) return !((LocalDateTime) objectValue).isBefore((LocalDateTime) conditionValue);
                    }
                }

                return true;
            }
            case META -> {


                return true;
            }
            case CUSTOM -> {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<T> fetchObjectsSatisfyingRules(List<Rule> rules) {
        List<SearchFilterRequestDto> searchFilterRequestDtos = new ArrayList<>();
        for (Rule rule : rules) {
            searchFilterRequestDtos.addAll(rule.getConditions().stream().map(RuleCondition::mapToSearchFilterRequestDto).toList());
            for (RuleConditionGroup conditionGroup : rule.getConditionGroups()) {
                searchFilterRequestDtos.addAll(conditionGroup.getConditions().stream().map(RuleCondition::mapToSearchFilterRequestDto).toList());
            }
        }
        List<T> resourceObjects = new ArrayList<>();
        final BiFunction<Root<T>, CriteriaBuilder, Predicate> additionalWhereClause = (root, cb) -> Sql2PredicateConverter.mapSearchFilter2Predicates(searchFilterRequestDtos, cb, root, null);
//        return repository.findUsingSecurityFilter(filter, additionalWhereClause, p, (root, cb) -> cb.desc(root.get("created")))
//                .stream()
//                .map(Certificate::mapToListDto).toList();
        return resourceObjects;
    }

    private SearchFilterRequestDto ruleToSearchFilter(Rule rule) {
    return null;
    }

    private Map<String, Function> fieldIdentifierToGetter;


}
