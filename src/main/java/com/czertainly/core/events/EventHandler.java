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
        T resourceObject = repository.findByUuid(SecuredUUID.fromUUID(eventMessage.getObjectUuid())).orElseThrow(() -> new EventException(eventMessage.getResourceEvent(), "%s with UUID %s not found".formatted(eventMessage.getResource().getLabel(), eventMessage.getObjectUuid())));

        EventContext<T> context = new EventContext<>(eventMessage, triggerEvaluator, resourceObject, getEventData(resourceObject, eventMessage.getData()));
        loadTriggers(context, null, null); // triggers without resource and its UUID are platform ones

        return context;
    }

    protected abstract Object getEventData(T object, Object eventMessageData);

    public void handleEvent(EventMessage eventMessage) throws EventException {
        logger.debug("Going to handle event '{}'", eventMessage.getResourceEvent().getLabel());

        EventContext<T> eventContext = prepareContext(eventMessage);
        processAllTriggers(eventContext);
        sendFollowUpEventsNotifications(eventContext);
        logger.debug("Event '{}' successfully handled", eventMessage.getResourceEvent().getLabel());
    }

    protected void sendFollowUpEventsNotifications(EventContext<T> eventContext) {
        // No follow-up events or internal notifications are sent by default
    }

    protected void loadTriggers(EventContext<T> context, Resource resource, UUID objectUuid) {
        List<TriggerAssociation> triggerAssociations = triggerAssociationRepository.findAllByEventAndResourceAndObjectUuidOrderByTriggerOrderAsc(context.getResourceEvent(), resource, objectUuid);
        for (TriggerAssociation triggerAssociation : triggerAssociations) {
            if (triggerAssociation.getTrigger().isIgnoreTrigger()) {
                context.getIgnoreTriggers().add(triggerAssociation);
            } else {
                context.getTriggers().add(triggerAssociation);
            }
        }
    }

    protected void processAllTriggers(EventContext<T> context) {
        logger.debug("Going to process {} triggers on {} objects registered for event '{}'", context.getIgnoreTriggers().size() + context.getTriggers().size(), context.getResourceObjects().size(), context.getResourceEvent().getLabel());
        for (int i = 0; i < context.getResourceObjects().size(); i++) {
            T resourceObject = context.getResourceObjects().get(i);
            Object eventData = context.getResourceObjectsEventData().get(i);
            try {
                // First, check the ignore triggers
                boolean isIgnored = false;
                for (TriggerAssociation triggerAssociation : context.getIgnoreTriggers()) {
                    Trigger trigger = triggerAssociation.getTrigger();
                    TriggerHistory triggerHistory = context.getTriggerEvaluator().evaluateTrigger(trigger, triggerAssociation.getUuid(), resourceObject, null, eventData);
                    if (triggerHistory.isActionsPerformed()) {
                        isIgnored = true;
                    }
                }

                // If some trigger ignored this object, processing is stopped
                if (isIgnored) {
                    continue;
                }

                // Evaluate rest of the triggers in given order
                for (TriggerAssociation triggerAssociation : context.getTriggers()) {
                    // Create trigger history entry
                    Trigger trigger = triggerAssociation.getTrigger();
                    context.getTriggerEvaluator().evaluateTrigger(trigger, triggerAssociation.getUuid(), resourceObject, null, eventData);
                }
            } catch (RuleException e) {
                logger.error("Unable to process trigger on {} object {}. Message: {}", context.getResource().getLabel(), resourceObject.getUuid(), e.getMessage());
            }
        }
        logger.debug("Triggers of event '{}' successfully handled", context.getResourceEvent().getLabel());
    }
}
