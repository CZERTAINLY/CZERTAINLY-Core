package com.czertainly.core.events;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.UniquelyIdentifiedObject;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerAssociation;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.repository.workflows.TriggerAssociationRepository;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.producers.EventProducer;
import com.czertainly.core.messaging.producers.NotificationProducer;
import com.czertainly.core.service.TriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Transactional
public abstract class EventHandler<T extends UniquelyIdentifiedObject> implements IEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);

    protected EventProducer eventProducer;
    protected NotificationProducer notificationProducer;
    protected ApplicationEventPublisher applicationEventPublisher;

    private TriggerService triggerService;
    private TriggerAssociationRepository triggerAssociationRepository;

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
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setTriggerAssociationRepository(TriggerAssociationRepository triggerAssociationRepository) {
        this.triggerAssociationRepository = triggerAssociationRepository;
    }

    protected abstract EventContext<T> prepareContext(EventMessage eventMessage) throws EventException;

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
        List<TriggerAssociation> triggerAssociations = triggerAssociationRepository.findAllByResourceEventAndResourceAndObjectUuidOrderByTriggerOrderAsc(context.getResourceEvent(), resource, objectUuid);
        for (TriggerAssociation triggerAssociation : triggerAssociations) {
            try {
                Trigger trigger = triggerService.getTriggerEntity(String.valueOf(triggerAssociation.getTriggerUuid()));
                if (triggerAssociation.getTriggerOrder() == -1) {
                    context.getIgnoreTriggers().add(trigger);
                } else {
                    context.getTriggers().add(trigger);
                }
            } catch (NotFoundException e) {
                logger.error(e.getMessage());
            }
        }
    }

    protected void processAllTriggers(EventContext<T> context) {
        logger.debug("Going to process {} triggers on {} objects registered for event '{}'", context.getIgnoreTriggers().size() + context.getTriggers().size(), context.getResourceObjects().size(), context.getResourceEvent().getLabel());
        for (T resourceObject : context.getResourceObjects()) {
            // First, check the triggers that have action with action type set to ignore
            List<TriggerHistory> triggerHistories = new ArrayList<>();
            try {
                boolean processed = processIgnoreTriggers(context, resourceObject, null, triggerHistories);

                // If some trigger ignored this object, processing is stopped
                if (!processed) {
                    continue;
                }

                processTriggers(context, resourceObject, null, triggerHistories);
            } catch (RuleException e) {
                logger.error("Unable to process trigger on {} object {}. Message: {}", context.getResource().getLabel(), resourceObject.getUuid(), e.getMessage());
            }
        }
        logger.debug("Triggers of event '{}' successfully handled", context.getResourceEvent().getLabel());
    }

    protected boolean processIgnoreTriggers(EventContext<T> context, T resourceObject, UUID referenceObjectUuid, List<TriggerHistory> triggerHistories) throws RuleException {
        for (Trigger trigger : context.getIgnoreTriggers()) {
            TriggerHistory triggerHistory = triggerService.createTriggerHistory(OffsetDateTime.now(), trigger.getUuid(), context.getAssociationObjectUuid(), resourceObject.getUuid(), referenceObjectUuid);
            triggerHistories.add(triggerHistory);
            if (context.getRuleEvaluator().evaluateRules(trigger.getRules(), resourceObject, triggerHistory)) {
                triggerHistory.setConditionsMatched(true);
                triggerHistory.setActionsPerformed(true);
                return false;
            } else {
                triggerHistory.setConditionsMatched(false);
                triggerHistory.setActionsPerformed(false);
            }
        }
        return true;
    }

    protected void processTriggers(EventContext<T> context, T resourceObject, UUID referenceObjectUuid, List<TriggerHistory> triggerHistories) throws RuleException {
        // Evaluate rest of the triggers in given order
        for (Trigger trigger : context.getTriggers()) {
            // Create trigger history entry
            TriggerHistory triggerHistory = triggerService.createTriggerHistory(OffsetDateTime.now(), trigger.getUuid(), context.getAssociationObjectUuid(), resourceObject.getUuid(), referenceObjectUuid);
            triggerHistories.add(triggerHistory);
            // If rules are satisfied, perform defined actions
            if (context.getRuleEvaluator().evaluateRules(trigger.getRules(), resourceObject, triggerHistory)) {
                triggerHistory.setConditionsMatched(true);
                context.getRuleEvaluator().performActions(trigger, resourceObject, triggerHistory);
                triggerHistory.setActionsPerformed(triggerHistory.getRecords().isEmpty());
            } else {
                triggerHistory.setConditionsMatched(false);
                triggerHistory.setActionsPerformed(false);
            }
        }
    }
}
