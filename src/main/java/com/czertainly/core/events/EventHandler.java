package com.czertainly.core.events;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.DiscoveryCertificate;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerAssociation;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.repository.workflows.TriggerAssociationRepository;
import com.czertainly.core.evaluator.RuleEvaluator;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.service.TriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public abstract class EventHandler<T> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);

    private TriggerService triggerService;
    private TriggerAssociationRepository triggerAssociationRepository;

    @Autowired
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Autowired
    public void setTriggerAssociationRepository(TriggerAssociationRepository triggerAssociationRepository) {
        this.triggerAssociationRepository = triggerAssociationRepository;
    }

    public void handleEvent(EventMessage eventMessage) {
        EventContext<T> eventContext = prepareContext(eventMessage);
        loadTriggers(eventContext, eventMessage.getResource(), eventMessage.getResourceUUID());
        processTriggers(eventContext);
    }

    protected void loadTriggers(EventContext<T> context, Resource resource, UUID objectUuid) {
        List<TriggerAssociation> triggerAssociations = triggerAssociationRepository.findAllByResourceEventAndResourceAndObjectUuidOrderByTriggerOrderAsc(resourceEvent, resource, objectUuid);
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

    private void processTriggers(T resourceObject, List<Trigger> triggers, boolean ignoreTriggers) throws RuleException {
        // First, check the triggers that have action with action type set to ignore
        boolean ignored = false;
        List<TriggerHistory> ignoreTriggerHistories = new ArrayList<>();
        for (Trigger trigger : ignoreTriggers) {
            TriggerHistory triggerHistory = triggerService.createTriggerHistory(OffsetDateTime.now(), trigger.getUuid(), discoveryUuid, null, discoveryCertificate.getUuid());
            if (context.getRuleEvaluator().evaluateRules(trigger.getRules(), resourceObject, triggerHistory)) {
                ignored = true;
                triggerHistory.setConditionsMatched(true);
                triggerHistory.setActionsPerformed(true);
                break;
            } else {
                triggerHistory.setConditionsMatched(false);
                triggerHistory.setActionsPerformed(false);
            }
            ignoreTriggerHistories.add(triggerHistory);
        }

        // If some trigger ignored this object, processing is stopped
        if (ignored) {
            return;
        }

        // Save certificate to database
        certificateService.updateCertificateEntity(certificate);

        // update objectUuid of not ignored certs
        for (TriggerHistory ignoreTriggerHistory : ignoreTriggerHistories) {
            ignoreTriggerHistory.setObjectUuid(certificate.getUuid());
        }

        // Evaluate rest of the triggers in given order
        for (Trigger trigger : orderedTriggers) {
            // Create trigger history entry
            TriggerHistory triggerHistory = triggerService.createTriggerHistory(OffsetDateTime.now(), trigger.getUuid(), discoveryUuid, certificate.getUuid(), discoveryCertificate.getUuid());
            // If rules are satisfied, perform defined actions
            if (certificateRuleEvaluator.evaluateRules(trigger.getRules(), certificate, triggerHistory)) {
                triggerHistory.setConditionsMatched(true);
                certificateRuleEvaluator.performActions(trigger, certificate, triggerHistory);
                triggerHistory.setActionsPerformed(triggerHistory.getRecords().isEmpty());
            } else {
                triggerHistory.setConditionsMatched(false);
                triggerHistory.setActionsPerformed(false);
            }
        }
    }

}
