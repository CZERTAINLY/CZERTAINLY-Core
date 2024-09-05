package com.czertainly.core.evaluator;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.client.metadata.ResponseMetadataDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.FilterFieldType;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.workflows.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.enums.ResourceToClass;
import com.czertainly.core.service.TriggerService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import jakarta.persistence.metamodel.Attribute;
import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;

@Component
@Transactional
public class RuleEvaluator<T> implements IRuleEvaluator<T> {

    private static final Logger logger = LoggerFactory.getLogger(RuleEvaluator.class);
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    private AttributeEngine attributeEngine;
    private TriggerService triggerService;

    @Autowired
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Override
    public boolean evaluateRules(List<Rule> rules, T object, TriggerHistory triggerHistory) throws RuleException {
        // if trigger has no rules, return true as it is trigger that should perform actions on all objects
        if (rules.isEmpty()) {
            return true;
        }

        // Rule evaluated is check if any rule has been evaluated, no rules will be evaluated if all rules in the list have incompatible resource
        boolean ruleEvaluated = false;
        for (Rule rule : rules) {
            logger.debug("Evaluating rule '{}'.", rule.getName());
            // Check if resource in the rule corresponds to the class of evaluator

            Class resourceClass = ResourceToClass.getClassByResource(rule.getResource());
            if (resourceClass == null) {
                throw new RuleException("Unknown class for resource " + rule.getResource().getLabel());
            }

            if (!resourceClass.isInstance(object)) {
                logger.debug("Rule '{}' has been skipped due to incompatible resource.", rule.getName());
                continue;
            }
            ruleEvaluated = true;
            for (Condition condition : rule.getConditions()) {
                for (ConditionItem conditionItem : condition.getItems()) {
                    if (!getConditionEvaluationResult(conditionItem, object, triggerHistory, rule)) return false;
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
            if (!evaluateRules(rules, object, null)) {
                logger.debug("Rules have not been satisfied for a object in the list, the list does not contain objects satisfying the rules.");
                return false;
            }
        }
        logger.debug("All objects in the list satisfy the rules.");
        return true;
    }

    @Override
    public Boolean evaluateConditionItem(ConditionItem conditionItem, T object, Resource resource) throws RuleException {
        FilterFieldSource fieldSource = conditionItem.getFieldSource();
        String fieldIdentifier = conditionItem.getFieldIdentifier();
        FilterConditionOperator operator = conditionItem.getOperator();
        Object conditionValue = conditionItem.getValue();

        // First, check where from to get object value based on Field Source
        if (fieldSource == FilterFieldSource.PROPERTY) {
            Object objectValue;
            FilterField field;
            try {
                field = Enum.valueOf(FilterField.class, fieldIdentifier);
            } catch (IllegalArgumentException e) {
                throw new RuleException("Field identifier '" + fieldIdentifier + "' is not supported.");
            }
            // Get value of property from the object
            try {
                objectValue = getPropertyValue(object, field, false);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuleException("Cannot get property " + fieldIdentifier + " from resource " + resource + ".");
            }
//            // Determine field type from field identifier using Searchable field enum
//            SearchFieldNameEnum propertyEnum;
//            try {
//                propertyEnum = SearchFieldNameEnum.getEnumBySearchableFields(field);
//            } catch (Exception e) {
//                throw new RuleException("Field identifier '" + fieldIdentifier + "' is not supported.");
//            }
//            if (propertyEnum == null) {
//                throw new RuleException("Unknown property field identifier: " + fieldIdentifier);
//            }

            FilterFieldType fieldType = field.getType().getFieldType();
            // Apply comparing function on value in object and value in condition, based on operator and field type, return whether the condition is satisfied
            try {
                if (!(objectValue instanceof Collection<?> objectValues)) {
                    return fieldTypeToOperatorActionMap.get(fieldType).get(operator).apply(objectValue, conditionValue);
                }
                for (Object item : objectValues) {
                    Object o = getPropertyValue(item, field, true);
                    if (!fieldTypeToOperatorActionMap.get(fieldType).get(operator).apply(o, conditionValue)) {
                        return false;
                    }
                }
                return true;
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
                            // Evaluate condition on each attribute content of the attribute, if at least one condition is evaluated as satisfied at least once, the condition is satisfied for the object
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
    public void performActions(Trigger trigger, T object, TriggerHistory triggerHistory) throws RuleException {
        Class resourceClass = ResourceToClass.getClassByResource(trigger.getResource());
        if (resourceClass == null) {
            throw new RuleException("Unknown class for resource " + trigger.getResource().getLabel());
        }

        if (!resourceClass.isInstance(object)) {
            logger.debug("Trigger '{}' cannot be executed due to incompatible resource.", trigger.getName());
            return;
        }

        if (trigger.getActions() != null) {
            for (Action action : trigger.getActions()) {
                for (Execution execution : action.getExecutions()) {
                    for (ExecutionItem executionItem : execution.getItems()) {
                        try {
                            performAction(executionItem, object, trigger.getResource());
                            logger.debug("Action with UUID {} has been performed.", action.getUuid());
                        } catch (Exception e) {
                            logger.debug("Action with UUID {} has not been performed. Reason: {}", action.getUuid(), e.getMessage());
                            TriggerHistoryRecord triggerHistoryRecord = triggerService.createTriggerHistoryRecord(triggerHistory, null, execution.getUuid(), e.getMessage());
                            triggerHistory.getRecords().add(triggerHistoryRecord);
                        }
                    }
                }
            }
        }
    }


    public void performAction(ExecutionItem executionItem, T object, Resource resource) throws RuleException, NotFoundException, AttributeException, CertificateOperationException {
        String fieldIdentifier = executionItem.getFieldIdentifier();
        Object actionData = executionItem.getData();
        FilterFieldSource fieldSource = executionItem.getFieldSource();

        // Set a property of the object using setter, the property must be set as settable
        if (fieldSource == FilterFieldSource.PROPERTY) {
            FilterField propertyEnum = Enum.valueOf(FilterField.class, fieldIdentifier);
            if (propertyEnum == null) {
                throw new RuleException("Field identifier '" + fieldIdentifier + "' is not supported.");
            }
            if (!propertyEnum.isSettable())
                throw new RuleException("Setting property '" + fieldIdentifier + "' is not supported.");
            try {
                PropertyUtils.setProperty(object, propertyEnum.getFieldAttribute().getName(), actionData);
            } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuleException(e.getMessage());
            }
        }
        // Set a custom attribute for the object
        if (fieldSource == FilterFieldSource.CUSTOM) {
            UUID objectUuid;
            try {
                objectUuid = (UUID) PropertyUtils.getProperty(object, "uuid");
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuleException("Cannot get uuid from resource " + resource + ".");
            }

            if (objectUuid == null)
                throw new RuleException("Cannot set custom attributes for an object not in database.");

            List<BaseAttributeContent> attributeContents = AttributeDefinitionUtils.convertContentItemsFromObject(actionData);
            attributeEngine.updateObjectCustomAttributeContent(resource, objectUuid, null, fieldIdentifier.substring(0, fieldIdentifier.indexOf("|")), attributeContents);
        }
    }

    private Object getPropertyValue(Object object, FilterField filterField, boolean alreadyNested) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        boolean isNested = filterField.getJoinAttributes() != null;
        StringBuilder pathToPropertyBuilder = new StringBuilder();
        if (filterField.getJoinAttributes() != null && !alreadyNested) {
            for (String property : filterField.getJoinAttributes().stream().map(Attribute::getName).toList()) {
                pathToPropertyBuilder.append(property).append(".");
            }
        }

        pathToPropertyBuilder.append(filterField.getFieldAttribute().getName());

        try {
            if (alreadyNested) {
                return PropertyUtils.getProperty(object, pathToPropertyBuilder.toString());
            }
            return PropertyUtils.getProperty(object, pathToPropertyBuilder.toString());
        } catch (NoSuchMethodException e) {
            if (!isNested || alreadyNested) {
                throw e;
            }

            Object tmpValue = PropertyUtils.getProperty(object, filterField.getJoinAttributes().getFirst().getName());
            if (tmpValue instanceof Collection<?>) {
                return tmpValue;
            }

            throw e;

        }

    }


    private boolean getConditionEvaluationResult(ConditionItem conditionItem, T object, TriggerHistory triggerHistory, Rule rule) {
        try {
            if (!evaluateConditionItem(conditionItem, object, rule.getResource())) {
                String message = String.format("Condition item '%s %s %s %s' is false.", conditionItem.getFieldSource().getLabel(), conditionItem.getFieldIdentifier(), conditionItem.getOperator().getLabel(), conditionItem.getValue() != null ? conditionItem.getValue().toString() : "");
                logger.debug("Rule {} is not satisfied. Reason: {}", rule.getName(), message);
                TriggerHistoryRecord triggerHistoryRecord = triggerService.createTriggerHistoryRecord(triggerHistory, conditionItem.getCondition().getUuid(), null, message);
                triggerHistory.getRecords().add(triggerHistoryRecord);
                return false;
            }
        } catch (RuleException e) {
            TriggerHistoryRecord triggerHistoryRecord = triggerService.createTriggerHistoryRecord(triggerHistory, conditionItem.getCondition().getUuid(), null, e.getMessage());
            triggerHistory.getRecords().add(triggerHistoryRecord);
            return false;
        }
        return true;
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

