package com.czertainly.core.evaluator;

import com.czertainly.api.exception.ActionException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.client.metadata.ResponseMetadataDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.rules.RuleActionType;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.FilterFieldType;
import com.czertainly.api.model.core.search.SearchableFields;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.*;
import com.czertainly.core.enums.ResourceToClass;
import com.czertainly.core.enums.SearchFieldNameEnum;
import com.czertainly.core.util.AttributeDefinitionUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;

@Component
public class RuleEvaluator<T> implements IRuleEvaluator<T> {

    private static final Logger logger = LoggerFactory.getLogger(RuleEvaluator.class);
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    private AttributeEngine attributeEngine;

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    public boolean evaluateRules(List<Rule> rules, T object) throws RuleException {
        // Rule evaluated is check if any rule has been evaluated, no rules will be evaluated if all rules in the list have incompatible resource
        boolean ruleEvaluated = false;
        for (Rule rule : rules) {
            logger.debug("Evaluating rule '{}'.", rule.getName());
            // Check if resource in the rule corresponds to the class of evaluator
            if (!ResourceToClass.getClassByResource(rule.getResource()).isInstance(object)) {
                logger.debug("Rule '{}' has been skipped due to incompatible resource.", rule.getName());
                continue;
            }
            ruleEvaluated = true;
            for (RuleCondition condition : rule.getConditions()) {
                if (!evaluateCondition(condition, object, rule.getResource())) {
                    logger.debug("Rule {} is not satisfied, condition '{} {} {}' from source {} has been evaluated as false for the object.",
                            rule.getName(), condition.getFieldIdentifier(), condition.getOperator().getCode(), condition.getValue().toString(), condition.getFieldSource().getCode());
                    return false;
                }
            }

            for (RuleConditionGroup conditionGroup : rule.getConditionGroups()) {
                for (RuleCondition condition : conditionGroup.getConditions()) {
                    if (!evaluateCondition(condition, object, rule.getResource())) {
                        logger.debug("Rule {} is not satisfied, condition '{} {} {}' from source {} has been evaluated as false for the object.",
                                rule.getName(), condition.getFieldIdentifier(), condition.getOperator().getCode(), condition.getValue().toString(), condition.getFieldSource().getCode());
                        return false;
                    }
                }
            }
        }

        if (ruleEvaluated) {
            logger.debug("All rules in the list have been satisfied for the object.");
        } else {
            logger.debug("No rules from the list have been evaluated, rules are not satisfied for the object.");
        }
        return ruleEvaluated;
    }

    @Override
    public boolean evaluateRules(List<Rule> rules, List<T> listOfObjects) throws RuleException {
        for (T object : listOfObjects) {
            if (!evaluateRules(rules, object)) {
                logger.debug("Rules have not been satisfied for a object in the list, the list does not contain objects satisfying the rules.");
                return false;
            }
        }
        logger.debug("All objects in the list satisfy the rules.");
        return true;
    }

    @Override
    public Boolean evaluateCondition(RuleCondition condition, T object, Resource resource) throws RuleException {

        FilterFieldSource fieldSource = condition.getFieldSource();
        String fieldIdentifier = condition.getFieldIdentifier();
        FilterConditionOperator operator = condition.getOperator();
        Object conditionValue = condition.getValue();


        // First, check where from to get object value based on Field Source
        if (fieldSource == FilterFieldSource.PROPERTY) {

            // Get value of property from the object
            Object objectValue;
            try {
                objectValue = PropertyUtils.getProperty(object, fieldIdentifier);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuleException("Cannot get property " + fieldIdentifier + " from resource " + resource + ".");
            }
            // Determine field type from field identifier using Searchable field enum
            SearchFieldNameEnum propertyEnum;
            try {
                propertyEnum = SearchFieldNameEnum.getEnumBySearchableFields(SearchableFields.fromCode(fieldIdentifier));
            } catch (Exception e) {
                throw new RuleException("Field identifier '" + fieldIdentifier + "' is not supported.");
            }
            FilterFieldType fieldType = propertyEnum.getFieldTypeEnum().getFieldType();
            // Apply comparing function on value in object and value in condition, based on operator and field type, return whether the condition is satisfied
            try {
                return fieldTypeToOperatorActionMap.get(fieldType).get(operator).apply(objectValue, conditionValue);
            } catch (Exception e) {
                throw new RuleException("Condition is not set properly: " + e.getMessage());
            }
        }

        // Check for UUID in the object, if there is no UUID, it means that the object is not yet in database and therefore won't have any attributes linked to it
        UUID objectUuid;
        try {
            objectUuid = (UUID) PropertyUtils.getProperty(object, "uuid");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuleException("Cannot get uuid from resource " + resource + ".");
        }

        if (objectUuid != null) {
            if (fieldSource == FilterFieldSource.CUSTOM) {
                // If source is Custom Attribute, retrieve custom attributes of this object and find the attribute which has Name equal to Field Identifier
                List<ResponseAttributeDto> responseAttributeDtos = attributeEngine.getObjectCustomAttributesContent(resource, objectUuid);
                ResponseAttributeDto attributeToCompare = responseAttributeDtos.stream().filter(rad -> Objects.equals(rad.getName(), fieldIdentifier)).findFirst().orElse(null);
                if (attributeToCompare == null) return false;
                // Evaluate condition on each attribute content of the attribute, if at least teh condition is evaluated as satisfied at least once, the condition is satisfied for the object
                return evaluateConditionOnAttribute(attributeToCompare, conditionValue, operator);
            }

            if (fieldSource == FilterFieldSource.META) {
                // If the Field Source is Meta Attribute, we expect Field Identifier to be formatted as follows 'name|contentType', since there can be multiple Meta Attributes with the same name, the Content Type must be specified
                String[] split = fieldIdentifier.split("\\|");
                if (split.length < 2) throw new RuleException("Field identifier is not in correct format.");
                AttributeContentType fieldAttributeContentType = AttributeContentType.valueOf(split[1]);
                String fieldIdentifierName = split[0];
                // From all Metadata of the object, find those with matching Name and Content Type and evaluate condition on these, return true for the first satisfying attribute, otherwise continue wit next
                List<MetadataResponseDto> metadata = attributeEngine.getMappedMetadataContent(new ObjectAttributeContentInfo(resource, objectUuid));
                for (List<ResponseMetadataDto> responseMetadataDtos : metadata.stream().map(MetadataResponseDto::getItems).toList()) {
                    for (ResponseAttributeDto responseAttributeDto : responseMetadataDtos) {
                        if (Objects.equals(responseAttributeDto.getName(), fieldIdentifierName) & fieldAttributeContentType == responseAttributeDto.getContentType()) {
                            // Evaluate condition on each attribute content of the attribute, if at least teh condition is evaluated as satisfied at least once, the condition is satisfied for the object
                            if (evaluateConditionOnAttribute(responseAttributeDto, conditionValue, operator))
                                return true;
                        }
                    }
                }
                // If no attribute has been evaluated as satisfying, the condition is not satisfied as whole
                return false;
            }
        }
        // Field source is not Property and object is not database, therefore attributes can not be evaluated and condition is not satisfied
        return false;
    }

    @Override
    public void performRuleActions(RuleTrigger trigger, T object) throws ActionException, NotFoundException, AttributeException {
        if (!ResourceToClass.getClassByResource(trigger.getResource()).isInstance(object)) {
            logger.debug("Trigger '{}' cannot be executed due to incompatible resource.", trigger.getName());
            return;
        }
        if (trigger.getActions() != null) {
            for (RuleAction action : trigger.getActions()) {
                try {
                    performAction(action, object, trigger.getResource());

                } catch (Exception e) {
                    logger.debug("Action with UUID {} has not been performed, reason: {}.", action.getUuid(), e.getMessage());
                }
            }
        }
        if (trigger.getActionGroups() != null) {
            for (RuleActionGroup actionGroup : trigger.getActionGroups()) {
                for (RuleAction action : actionGroup.getActions()) {
                    try {
                        performAction(action, object, trigger.getResource());
                    } catch (Exception e) {
                        logger.debug("Action with UUID {} has not been performed, reason: {}.", action.getUuid(), e.getMessage());
                    }
                }
            }
        }
    }

    private void performAction(RuleAction action, T object, Resource resource) throws ActionException, NotFoundException, AttributeException {
        RuleActionType actionType = action.getActionType();
        String fieldIdentifier = action.getFieldIdentifier();
        Object actionData = action.getActionData();
        FilterFieldSource fieldSource = action.getFieldSource();

        if (actionType == RuleActionType.SET_FIELD) {
            if (fieldSource == FilterFieldSource.PROPERTY) {
                SearchFieldNameEnum propertyEnum;
                try {
                    propertyEnum = SearchFieldNameEnum.getEnumBySearchableFields(SearchableFields.fromCode(fieldIdentifier));
                } catch (Exception e) {
                    throw new ActionException("Field identifier '" + fieldIdentifier + "' is not supported.");
                }
                if (!propertyEnum.isActionEnabled())
                    throw new ActionException("Setting property '" + fieldIdentifier + "' is not supported.");
                try {
                    PropertyUtils.setProperty(object, action.getFieldIdentifier(), actionData);
                } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    throw new ActionException(e.getMessage());
                }
            }

            if (fieldSource == FilterFieldSource.CUSTOM) {
                UUID objectUuid;
                try {
                    objectUuid = (UUID) PropertyUtils.getProperty(object, "uuid");
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new ActionException("Cannot get uuid from resource " + resource + ".");
                }

                if (objectUuid == null)
                    throw new ActionException("Cannot perform action " + actionType + " on object not in database.");

                List<BaseAttributeContent> attributeContents = AttributeDefinitionUtils.deserializeAttributeContent(actionData.toString(), BaseAttributeContent.class);
                attributeEngine.updateObjectCustomAttributeContent(resource, objectUuid, null, fieldIdentifier, attributeContents);
            }
        }
    }





    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> commonOperatorFunctionMap;
    private static final Map<FilterFieldType, Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>>> fieldTypeToOperatorActionMap;
    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> stringOperatorFunctionMap;
    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> numberOperatorFunctionMap;
    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> dateOperatorFunctionMap;
    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> datetimeOperatorFunctionMap;


    static {
        commonOperatorFunctionMap = new HashMap<>();
        commonOperatorFunctionMap.put(FilterConditionOperator.EQUALS, Object::equals);
        commonOperatorFunctionMap.put(FilterConditionOperator.NOT_EQUALS, (o, c) -> !o.equals(c));
        commonOperatorFunctionMap.put(FilterConditionOperator.EMPTY, (o, c) -> o == null);
        commonOperatorFunctionMap.put(FilterConditionOperator.NOT_EMPTY, (o, c) -> o != null);

        fieldTypeToOperatorActionMap = new HashMap<>();

        stringOperatorFunctionMap = new HashMap<>();
        stringOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        stringOperatorFunctionMap.put(FilterConditionOperator.CONTAINS, (o, c) -> o.toString().contains(c.toString()));
        stringOperatorFunctionMap.put(FilterConditionOperator.NOT_CONTAINS, (o, c) -> !o.toString().contains(c.toString()));
        stringOperatorFunctionMap.put(FilterConditionOperator.STARTS_WITH, (o, c) -> o.toString().startsWith(c.toString()));
        stringOperatorFunctionMap.put(FilterConditionOperator.ENDS_WITH, (o, c) -> o.toString().endsWith(c.toString()));
        fieldTypeToOperatorActionMap.put(FilterFieldType.STRING, stringOperatorFunctionMap);

        numberOperatorFunctionMap = new HashMap<>();
        numberOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        numberOperatorFunctionMap.put(FilterConditionOperator.GREATER, (o, c) -> compareNumbers((Number) o, (Number) c) > 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.GREATER_OR_EQUAL, (o, c) -> compareNumbers((Number) o, (Number) c) > 0 || compareNumbers((Number) o, (Number) c) == 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.LESSER, (o, c) -> compareNumbers((Number) o, (Number) c) < 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.LESSER_OR_EQUAL, (o, c) -> compareNumbers((Number) o, (Number) c) < 0 || compareNumbers((Number) o, (Number) c) == 0);
        fieldTypeToOperatorActionMap.put(FilterFieldType.NUMBER, numberOperatorFunctionMap);

        dateOperatorFunctionMap = new HashMap<>();
        dateOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        dateOperatorFunctionMap.put(FilterConditionOperator.GREATER, (o, c) -> ((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(LocalDate.parse(c.toString())));
        dateOperatorFunctionMap.put(FilterConditionOperator.GREATER_OR_EQUAL, (o, c) -> !(((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.parse(c.toString()))));
        dateOperatorFunctionMap.put(FilterConditionOperator.LESSER, (o, c) -> ((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.parse(c.toString())));
        dateOperatorFunctionMap.put(FilterConditionOperator.LESSER_OR_EQUAL, (o, c) -> !(((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(LocalDate.parse(c.toString()))));
        fieldTypeToOperatorActionMap.put(FilterFieldType.DATE, dateOperatorFunctionMap);

        datetimeOperatorFunctionMap = new HashMap<>();
        datetimeOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        datetimeOperatorFunctionMap.put(FilterConditionOperator.GREATER, (o, c) -> ((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isAfter(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT))));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.GREATER_OR_EQUAL, (o, c) -> !(((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isBefore(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT)))));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.LESSER, (o, c) -> ((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isBefore(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT))));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.LESSER_OR_EQUAL, (o, c) -> !(((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().isAfter(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT)))));
        fieldTypeToOperatorActionMap.put(FilterFieldType.DATETIME, datetimeOperatorFunctionMap);

        fieldTypeToOperatorActionMap.put(FilterFieldType.LIST, commonOperatorFunctionMap);
        fieldTypeToOperatorActionMap.put(FilterFieldType.BOOLEAN, commonOperatorFunctionMap);

    }

    private static int compareNumbers(Number objectNumber, Number conditionNumber) {
        return Float.compare(objectNumber.floatValue(), conditionNumber.floatValue());
    }

    private boolean evaluateConditionOnAttribute(ResponseAttributeDto attributeDto, Object conditionValue, FilterConditionOperator operator) throws RuleException {
        AttributeContentType contentType = attributeDto.getContentType();
        for (BaseAttributeContent attributeContent : attributeDto.getContent()) {
            Object attributeValue = contentType.isFilterByData() ? attributeContent.getData() : attributeContent.getReference();
            try {
                if (fieldTypeToOperatorActionMap.get(contentTypeToFieldType(contentType)).get(operator).apply(attributeValue, conditionValue))
                    return true;
            } catch (Exception e) {
                throw new RuleException("Invalid condition.");
            }
        }
        return false;
    }


    private FilterFieldType contentTypeToFieldType(AttributeContentType contentType) {
        switch (contentType) {
            case STRING, TEXT, CODEBLOCK, SECRET, FILE, CREDENTIAL, OBJECT -> {
                return FilterFieldType.STRING;
            }
            case INTEGER, FLOAT -> {
                return FilterFieldType.NUMBER;
            }
            case BOOLEAN -> {
                return FilterFieldType.BOOLEAN;
            }
            case DATE -> {
                return FilterFieldType.DATE;
            }
            case TIME, DATETIME -> {
                return FilterFieldType.DATETIME;
            }
        }
        return null;
    }


}

