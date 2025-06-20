package com.czertainly.core.evaluator;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.ResponseAttributeDto;
import com.czertainly.api.model.client.metadata.MetadataResponseDto;
import com.czertainly.api.model.client.metadata.ResponseMetadataDto;
import com.czertainly.api.model.common.attribute.v2.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.BaseAttributeContent;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.czertainly.api.model.core.search.FilterFieldType;
import com.czertainly.api.model.core.workflows.ExecutionType;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.UniquelyIdentifiedObject;
import com.czertainly.core.dao.entity.workflows.*;
import com.czertainly.core.enums.FilterField;
import com.czertainly.core.enums.ResourceToClass;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.producers.NotificationProducer;
import com.czertainly.core.service.TriggerService;
import com.czertainly.core.util.AttributeDefinitionUtils;
import com.czertainly.core.util.FilterPredicatesBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;

@Component
@Transactional
public class TriggerEvaluator<T extends UniquelyIdentifiedObject> implements ITriggerEvaluator<T> {

    protected static final Logger logger = LoggerFactory.getLogger(TriggerEvaluator.class);
    private static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    private ObjectMapper objectMapper;
    private AttributeEngine attributeEngine;

    private TriggerService triggerService;
    private NotificationProducer notificationProducer;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setAttributeEngine(AttributeEngine attributeEngine) {
        this.attributeEngine = attributeEngine;
    }

    @Autowired
    public void setNotificationProducer(NotificationProducer notificationProducer) {
        this.notificationProducer = notificationProducer;
    }

    @Override
    public TriggerHistory evaluateTrigger(Trigger trigger, UUID triggerAssociationUuid, T object, UUID referenceObjectUuid, Object data) throws RuleException {
        TriggerHistory triggerHistory = triggerService.createTriggerHistory(trigger.getUuid(), triggerAssociationUuid, object.getUuid(), referenceObjectUuid);
        if (evaluateRules(triggerHistory, trigger.getRules(), object)) {
            triggerHistory.setConditionsMatched(true);
            if (trigger.isIgnoreTrigger()) {
                triggerHistory.setActionsPerformed(true);
            } else {
                performActions(trigger, triggerHistory, object, data);
                triggerHistory.setActionsPerformed(triggerHistory.getRecords().isEmpty());
            }
        } else {
            triggerHistory.setConditionsMatched(false);
            triggerHistory.setActionsPerformed(false);
        }

        return triggerHistory;
    }

    @Override
    public boolean evaluateRules(TriggerHistory triggerHistory, Set<Rule> rules, T object) throws RuleException {
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
    public boolean evaluateConditionItem(ConditionItem conditionItem, T object, Resource resource) throws RuleException {
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

            FilterFieldType fieldType = field.getType().getFieldType();
            // Apply comparing function on value in object and value in condition, based on operator and field type, return whether the condition is satisfied
            try {
                if (!(objectValue instanceof Collection<?> objectValues)) {
                    return fieldTypeToOperatorActionMap.get(fieldType).get(operator).apply(objectValue, conditionValue);
                }
                if (operator == FilterConditionOperator.EMPTY) return objectValues.isEmpty();
                if (operator == FilterConditionOperator.NOT_EMPTY) return !objectValues.isEmpty();
                for (Object item : objectValues) {
                    Object o = getPropertyValue(item, field, true);
                    if (Boolean.FALSE.equals(fieldTypeToOperatorActionMap.get(fieldType).get(operator).apply(o, conditionValue))) {
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
                        if (Objects.equals(responseAttributeDto.getName(), fieldIdentifierName) && fieldAttributeContentType == responseAttributeDto.getContentType()) {
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
    public void performActions(Trigger trigger, TriggerHistory triggerHistory, T object, Object data) throws RuleException {
        Class resourceClass = ResourceToClass.getClassByResource(trigger.getResource());
        if (resourceClass == null) {
            throw new RuleException("Unknown class for resource " + trigger.getResource().getLabel());
        }

        if (!resourceClass.isInstance(object)) {
            logger.debug("Trigger '{}' cannot be executed due to incompatible resource.", trigger.getName());
            return;
        }

        ResourceEvent event = null;
        if (triggerHistory.getTriggerAssociationUuid() != null) {
            event = triggerHistory.getTriggerAssociation().getEvent();
        }

        if (trigger.getActions() != null) {
            for (Action action : trigger.getActions()) {
                for (Execution execution : action.getExecutions()) {
                    try {
                        if (execution.getType() == ExecutionType.SET_FIELD) {
                            performSetFieldExecution(trigger.getResource(), execution, object);
                        } else {
                            performSendNotificationAction(trigger.getResource(), event, execution, object, data);
                        }
                        logger.debug("Execution '{}' of action '{}' has been performed.", action.getName(), execution.getName());
                    } catch (Exception e) {
                        logger.debug("Execution '{}' of action '{}' has not been performed. Reason: {}", action.getName(), execution.getName(), e.getMessage());
                        TriggerHistoryRecord triggerHistoryRecord = triggerService.createTriggerHistoryRecord(triggerHistory, null, execution.getUuid(), e.getMessage());
                        triggerHistory.getRecords().add(triggerHistoryRecord);
                    }
                }
                logger.debug("Action '{}' has been performed.", action.getName());
            }
        }
    }

    protected void performSetFieldExecution(Resource resource, Execution execution, T object) throws RuleException, NotFoundException, AttributeException, CertificateOperationException {
        for (ExecutionItem executionItem : execution.getItems()) {
            String fieldIdentifier = executionItem.getFieldIdentifier();
            Object actionData = executionItem.getData();
            FilterFieldSource fieldSource = executionItem.getFieldSource();

            // Set a property of the object using setter, the property must be set as settable
            if (fieldSource == FilterFieldSource.PROPERTY) {
                performSetFieldPropertyExecution(fieldIdentifier, actionData, object);
            } else if (fieldSource == FilterFieldSource.CUSTOM) { // Set a custom attribute for the object
                performSetFieldAttributeExecution(resource, fieldIdentifier, actionData, object);
            }
        }
    }

    protected void performSetFieldPropertyExecution(String fieldIdentifier, Object actionData, T object) throws RuleException, CertificateOperationException, NotFoundException, AttributeException {
        FilterField propertyEnum = Enum.valueOf(FilterField.class, fieldIdentifier);
        if (!propertyEnum.isSettable())
            throw new RuleException("Setting property '" + fieldIdentifier + "' is not supported.");
        try {
            PropertyUtils.setProperty(object, propertyEnum.getFieldAttribute().getName(), actionData);
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuleException(e.getMessage());
        }
    }

    protected void performSetFieldAttributeExecution(Resource resource, String fieldIdentifier, Object actionData, T object) throws RuleException, NotFoundException, AttributeException {
        UUID objectUuid;
        try {
            objectUuid = (UUID) PropertyUtils.getProperty(object, "uuid");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuleException("Cannot get uuid from resource " + resource + ".");
        }

        if (objectUuid == null) {
            throw new RuleException("Cannot set custom attributes for an object not in database.");
        }

        List<BaseAttributeContent> attributeContents = AttributeDefinitionUtils.convertContentItemsFromObject(actionData);
        attributeEngine.updateObjectCustomAttributeContent(resource, objectUuid, null, fieldIdentifier.substring(0, fieldIdentifier.indexOf("|")), attributeContents);
    }

    protected void performSendNotificationAction(Resource resource, ResourceEvent event, Execution execution, T object, Object data) {
        List<UUID> notificationProfileUuids = new ArrayList<>();
        for (ExecutionItem executionItem : execution.getItems()) {
            notificationProfileUuids.add(executionItem.getNotificationProfileUuid());
        }

        NotificationMessage message = new NotificationMessage(event, resource, object.getUuid(), notificationProfileUuids, null, data);
        notificationProducer.produceMessage(message);
    }

    private Object getPropertyValue(Object object, FilterField filterField, boolean alreadyNested) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        boolean isNested = filterField.getJoinAttributes() != null;
        String pathToProperty = FilterPredicatesBuilder.buildPathToProperty(filterField, alreadyNested);

        try {
            if (alreadyNested) {
                return PropertyUtils.getProperty(object, pathToProperty);
            }
            return PropertyUtils.getProperty(object, pathToProperty);
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
    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> listOperatorFunctionMap;
    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> dateOperatorFunctionMap;
    private static final Map<FilterConditionOperator, BiFunction<Object, Object, Boolean>> datetimeOperatorFunctionMap;


    static {
        commonOperatorFunctionMap = new EnumMap<>(FilterConditionOperator.class);
        commonOperatorFunctionMap.put(FilterConditionOperator.EQUALS, Object::equals);
        commonOperatorFunctionMap.put(FilterConditionOperator.NOT_EQUALS, (o, c) -> !o.equals(c));
        commonOperatorFunctionMap.put(FilterConditionOperator.EMPTY, (o, c) -> o == null);
        commonOperatorFunctionMap.put(FilterConditionOperator.NOT_EMPTY, (o, c) -> o != null);

        fieldTypeToOperatorActionMap = new EnumMap<>(FilterFieldType.class);

        stringOperatorFunctionMap = new EnumMap<>(FilterConditionOperator.class);
        stringOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        stringOperatorFunctionMap.put(FilterConditionOperator.CONTAINS, (o, c) -> o.toString().contains(c.toString()));
        stringOperatorFunctionMap.put(FilterConditionOperator.NOT_CONTAINS, (o, c) -> !o.toString().contains(c.toString()));
        stringOperatorFunctionMap.put(FilterConditionOperator.STARTS_WITH, (o, c) -> o.toString().startsWith(c.toString()));
        stringOperatorFunctionMap.put(FilterConditionOperator.ENDS_WITH, (o, c) -> o.toString().endsWith(c.toString()));
        fieldTypeToOperatorActionMap.put(FilterFieldType.STRING, stringOperatorFunctionMap);

        numberOperatorFunctionMap = new EnumMap<>(FilterConditionOperator.class);
        numberOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        numberOperatorFunctionMap.put(FilterConditionOperator.EQUALS, (o, c) -> compareNumbers((Number) o, c) == 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.NOT_EQUALS, (o, c) -> compareNumbers((Number) o, c) != 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.GREATER, (o, c) -> compareNumbers((Number) o, c) > 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.GREATER_OR_EQUAL, (o, c) -> compareNumbers((Number) o, c) > 0 || compareNumbers((Number) o, c) == 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.LESSER, (o, c) -> compareNumbers((Number) o, c) < 0);
        numberOperatorFunctionMap.put(FilterConditionOperator.LESSER_OR_EQUAL, (o, c) -> compareNumbers((Number) o, c) < 0 || compareNumbers((Number) o, c) == 0);
        fieldTypeToOperatorActionMap.put(FilterFieldType.NUMBER, numberOperatorFunctionMap);

        dateOperatorFunctionMap = new EnumMap<>(FilterConditionOperator.class);
        dateOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        dateOperatorFunctionMap.put(FilterConditionOperator.GREATER, (o, c) -> getLocalDate((Date) o).isAfter(LocalDate.parse(c.toString())));
        dateOperatorFunctionMap.put(FilterConditionOperator.GREATER_OR_EQUAL, (o, c) -> !(getLocalDate((Date) o).isBefore(LocalDate.parse(c.toString()))));
        dateOperatorFunctionMap.put(FilterConditionOperator.LESSER, (o, c) -> getLocalDate((Date) o).isBefore(LocalDate.parse(c.toString())));
        dateOperatorFunctionMap.put(FilterConditionOperator.LESSER_OR_EQUAL, (o, c) -> !(getLocalDate((Date) o).isAfter(LocalDate.parse(c.toString()))));
        dateOperatorFunctionMap.put(FilterConditionOperator.IN_PAST, (o, c) -> (getLocalDate((Date) o)).isBefore(LocalDate.now()) && (getLocalDate((Date) o)).isAfter(getLocalDateNowMinusDuration(c.toString())));
        dateOperatorFunctionMap.put(FilterConditionOperator.IN_NEXT, (o, c) -> (getLocalDate((Date) o)).isAfter(LocalDate.now()) && (getLocalDate((Date) o)).isBefore(getLocalDateNowPlusDuration(c.toString())));

        fieldTypeToOperatorActionMap.put(FilterFieldType.DATE, dateOperatorFunctionMap);

        datetimeOperatorFunctionMap = new EnumMap<>(FilterConditionOperator.class);
        datetimeOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        datetimeOperatorFunctionMap.put(FilterConditionOperator.GREATER, (o, c) -> getLocalDateTime((Date) o).isAfter(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT))));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.GREATER_OR_EQUAL, (o, c) -> !(getLocalDateTime((Date) o).isBefore(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT)))));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.LESSER, (o, c) -> getLocalDateTime((Date) o).isBefore(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT))));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.LESSER_OR_EQUAL, (o, c) -> !(getLocalDateTime((Date) o).isAfter(LocalDateTime.parse(c.toString(), DateTimeFormatter.ofPattern(DATETIME_FORMAT)))));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.IN_PAST, (o, c) -> (getLocalDateTime((Date) o)).isBefore(LocalDateTime.now()) && (getLocalDateTime((Date) o)).isAfter(getLocalDateTimeNowMinusDuration(c.toString())));
        datetimeOperatorFunctionMap.put(FilterConditionOperator.IN_NEXT, (o, c) -> (getLocalDateTime((Date) o)).isAfter(LocalDateTime.now()) && (getLocalDateTime((Date) o)).isBefore(getLocalDateTimeNowPlusDuration(c.toString())));

        fieldTypeToOperatorActionMap.put(FilterFieldType.DATETIME, datetimeOperatorFunctionMap);

        listOperatorFunctionMap = new EnumMap<>(FilterConditionOperator.class);
        listOperatorFunctionMap.putAll(commonOperatorFunctionMap);
        listOperatorFunctionMap.put(FilterConditionOperator.EQUALS, (o, c) -> ((Collection<?>) c).contains(o));
        listOperatorFunctionMap.put(FilterConditionOperator.NOT_EQUALS, (o, c) -> !((Collection<?>) c).contains(o));
        fieldTypeToOperatorActionMap.put(FilterFieldType.LIST, listOperatorFunctionMap);

        fieldTypeToOperatorActionMap.put(FilterFieldType.BOOLEAN, commonOperatorFunctionMap);

    }

    private static LocalDate getLocalDate(Date o) {
        return o.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static LocalDateTime getLocalDateTime(Date o) {
        return o.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static LocalDateTime getLocalDateTimeNowMinusDuration(String duration) {
        Duration durationParsed = getDurationParsed(duration);
        return LocalDateTime.now().minus(Period.of(durationParsed.getYears(), durationParsed.getMonths(), durationParsed.getDays()))
                .minusHours(durationParsed.getHours()).minusMinutes(durationParsed.getMinutes()).minusSeconds(durationParsed.getSeconds());
    }

    private static LocalDate getLocalDateNowMinusDuration(String duration) {
        Duration durationParsed = getDurationParsed(duration);
        return LocalDate.now().minus(Period.of(durationParsed.getYears(), durationParsed.getMonths(), durationParsed.getDays()));
    }

    private static LocalDateTime getLocalDateTimeNowPlusDuration(String duration) {
        Duration durationParsed = getDurationParsed(duration);
        return LocalDateTime.now().plus(Period.of(durationParsed.getYears(), durationParsed.getMonths(), durationParsed.getDays()))
                .plusHours(durationParsed.getHours()).plusMinutes(durationParsed.getMinutes()).plusSeconds(durationParsed.getSeconds());
    }

    private static LocalDate getLocalDateNowPlusDuration(String duration) {
        Duration durationParsed = getDurationParsed(duration);
        return LocalDate.now().plus(Period.of(durationParsed.getYears(), durationParsed.getMonths(), durationParsed.getDays()));
    }

    private static Duration getDurationParsed(String duration) {
        Duration durationParsed;
        try {
            durationParsed = DatatypeFactory.newInstance().newDuration(duration);
        } catch (Exception e) {
            throw new ValidationException("Cannot parse " + duration + "to Duration: " + e.getMessage());
        }
        return durationParsed;
    }

    private static int compareNumbers(Number objectNumber, Object conditionNumber) {
        if (conditionNumber instanceof String) {
            conditionNumber = Float.parseFloat(conditionNumber.toString());
        } else if (!(conditionNumber instanceof Number)) {
            throw new ValidationException("Invalid type for conditionNumber. Expected String or Number, but got: " 
                    + (conditionNumber == null ? "null" : conditionNumber.getClass().getSimpleName()));
        }
        return Float.compare(objectNumber.floatValue(), ((Number) conditionNumber).floatValue());
    }

    private boolean evaluateConditionOnAttribute(ResponseAttributeDto attributeDto, Object conditionValue, FilterConditionOperator operator) throws RuleException {
        AttributeContentType contentType = attributeDto.getContentType();
        for (BaseAttributeContent attributeContent : attributeDto.getContent()) {
            Object attributeValue = contentType.isFilterByData() ? attributeContent.getData() : attributeContent.getReference();
            try {
                if (Boolean.TRUE.equals(fieldTypeToOperatorActionMap.get(contentTypeToFieldType(contentType)).get(operator).apply(attributeValue, conditionValue)))
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

