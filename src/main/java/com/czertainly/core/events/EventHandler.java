package com.czertainly.core.events;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.UniquelyIdentifiedObject;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerAssociation;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import com.czertainly.core.dao.repository.workflows.TriggerAssociationRepository;
import com.czertainly.core.evaluator.TriggerEvaluator;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.producers.EventProducer;
import com.czertainly.core.messaging.producers.NotificationProducer;
import com.czertainly.core.security.authz.SecuredUUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional
public abstract class EventHandler<T extends UniquelyIdentifiedObject> implements IEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);

    protected ObjectMapper objectMapper;
    protected EventProducer eventProducer;
    protected NotificationProducer notificationProducer;
    protected ApplicationEventPublisher applicationEventPublisher;

    protected final TriggerEvaluator<T> triggerEvaluator;
    protected final SecurityFilterRepository<T, UUID> repository;

    private TriggerAssociationRepository triggerAssociationRepository;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setEventProducer(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @Autowired
    public void setNotificationProducer(NotificationProducer notificationProducer) {
        this.notificationProducer = notificationProducer;
    }

    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired
    public void setTriggerAssociationRepository(TriggerAssociationRepository triggerAssociationRepository) {
        this.triggerAssociationRepository = triggerAssociationRepository;
    }

    protected EventHandler(SecurityFilterRepository<T, UUID> repository, TriggerEvaluator<T> triggerEvaluator) {
        this.repository = repository;
        this.triggerEvaluator = triggerEvaluator;
    }

    protected EventContext<T> prepareContext(EventMessage eventMessage) throws EventException {
        T resourceObject = repository.findByUuid(SecuredUUID.fromUUID(eventMessage.getObjectUuid())).orElseThrow(() -> new EventException(eventMessage.getEvent(), "%s with UUID %s not found".formatted(eventMessage.getResource().getLabel(), eventMessage.getObjectUuid())));

        EventContext<T> context = new EventContext<>(eventMessage, triggerEvaluator, resourceObject, getEventData(resourceObject, eventMessage.getData()));
        fetchEventTriggers(context, null, null); // triggers without resource and its UUID are platform ones

        return context;
    }

    protected abstract Object getEventData(T object, Object eventMessageData);

    protected List<EventContextTriggers> getOverridingTriggers(EventContext<T> eventContext, T object) throws EventException {
        return List.of();
    }

    public void handleEvent(EventMessage eventMessage) throws EventException {
        logger.debug("Going to handle event '{}'", eventMessage.getEvent().getLabel());

        EventContext<T> eventContext = prepareContext(eventMessage);
        processAllTriggers(eventContext);
        sendFollowUpEventsNotifications(eventContext);

        logger.debug("Event '{}' successfully handled", eventMessage.getEvent().getLabel());
    }

    protected void sendFollowUpEventsNotifications(EventContext<T> eventContext) {
        // No follow-up events or internal notifications are sent by default
    }

    protected EventContextTriggers fetchEventTriggers(EventContext<T> context, Resource resource, UUID objectUuid) throws EventException {
        List<TriggerAssociation> triggerAssociations = triggerAssociationRepository.findAllByEventAndResourceAndObjectUuidOrderByTriggerOrderAsc(context.getEvent(), resource, objectUuid);

        EventContextTriggers eventContextTriggers;
        if (resource == null && objectUuid == null) {
            eventContextTriggers = context.getPlatformTriggers();
        } else {
            if (resource == null || objectUuid == null) {
                throw new EventException(context.getEvent(), "Error in fetching triggers for event '%s'. %s is null".formatted(context.getEvent().getLabel(), resource == null ? "Resource" : "Object UUID"));
            }
            String triggersKey = "%s.%s".formatted(resource.toString(), objectUuid.toString());
            eventContextTriggers = context.getOverridingResourceTriggers().computeIfAbsent(triggersKey, key -> new EventContextTriggers(resource, objectUuid));
        }

        for (TriggerAssociation triggerAssociation : triggerAssociations) {
            if (triggerAssociation.getTrigger().isIgnoreTrigger()) {
                eventContextTriggers.getIgnoreTriggers().add(triggerAssociation);
            } else {
                eventContextTriggers.getTriggers().add(triggerAssociation);
            }
        }

        return eventContextTriggers;
    }

    protected void processAllTriggers(EventContext<T> context) throws EventException {
        for (int i = 0; i < context.getResourceObjects().size(); i++) {
            T resourceObject = context.getResourceObjects().get(i);
            Object eventData = context.getResourceObjectsEventData().get(i);

            // load overriding triggers
            List<EventContextTriggers> overridingTriggers = getOverridingTriggers(context, resourceObject);
            for (EventContextTriggers triggers : overridingTriggers) {
                processTriggers(context, triggers, resourceObject, eventData);
            }

            // at the end process platform triggers
            processTriggers(context, context.getPlatformTriggers(), resourceObject, eventData);
        }
        logger.debug("Triggers of event '{}' successfully handled", context.getEvent().getLabel());
    }

    protected void processTriggers(EventContext<T> context, EventContextTriggers eventTriggers, T resourceObject, Object eventData) {
        logger.debug("Going to process {} triggers from {} {} on {} object(s) registered for event '{}'", eventTriggers.getIgnoreTriggers().size() + eventTriggers.getTriggers().size(), eventTriggers.getResource() == null ? Resource.SETTINGS.getLabel() : eventTriggers.getResource().getLabel(), eventTriggers.getObjectUuid(), context.getResourceObjects().size(), context.getEvent().getLabel());
        try {
            // First, check the ignore triggers
            boolean isIgnored = false;
            for (TriggerAssociation triggerAssociation : eventTriggers.getIgnoreTriggers()) {
                Trigger trigger = triggerAssociation.getTrigger();
                TriggerHistory triggerHistory = context.getTriggerEvaluator().evaluateTrigger(trigger, triggerAssociation.getUuid(), resourceObject, null, eventData);
                if (triggerHistory.isActionsPerformed()) {
                    isIgnored = true;
                }
            }

            // If some trigger ignored this object, processing is stopped
            if (isIgnored) {
                return;
            }

            // Evaluate rest of the triggers in given order
            for (TriggerAssociation triggerAssociation : eventTriggers.getTriggers()) {
                // Create trigger history entry
                Trigger trigger = triggerAssociation.getTrigger();
                context.getTriggerEvaluator().evaluateTrigger(trigger, triggerAssociation.getUuid(), resourceObject, null, eventData);
            }
        } catch (RuleException e) {
            logger.error("Unable to process trigger on {} object {}. Message: {}", context.getResource().getLabel(), resourceObject.getUuid(), e.getMessage());
        }
    }
}
