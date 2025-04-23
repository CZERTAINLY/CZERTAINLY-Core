package com.czertainly.core.events;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.evaluator.RuleEvaluator;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public class DiscoveryFinishedEventHandler extends EventHandler<DiscoveryHistory> {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryFinishedEventHandler.class);

    private RuleEvaluator<DiscoveryHistory> ruleEvaluator;

    private DiscoveryRepository discoveryRepository;

    @Autowired
    public void setRuleEvaluator(RuleEvaluator<DiscoveryHistory> ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    @Autowired
    public void setDiscoveryRepository(DiscoveryRepository discoveryRepository) {
        this.discoveryRepository = discoveryRepository;
    }

    @Override
    protected EventContext<DiscoveryHistory> prepareContext(EventMessage eventMessage) throws EventException {
        EventContext<DiscoveryHistory> context = new EventContext<>(eventMessage.getResource(), eventMessage.getResourceEvent(), ruleEvaluator, eventMessage.getUserUuid(), eventMessage.getOverrideObjectUuid());

        // TODO: load triggers from platform
        context.getResourceObjects().add(discoveryRepository.findByUuid(eventMessage.getObjectUuid()).orElseThrow(() -> new EventException(ResourceEvent.CERTIFICATE_DISCOVERED, "Discovery with UUID %s not found".formatted(eventMessage.getOverrideObjectUuid()))));
        return context;
    }

    @Override
    public void handleEvent(EventMessage eventMessage) throws EventException {
        // set discovery status

        EventContext<DiscoveryHistory> eventContext = prepareContext(eventMessage);
        processAllTriggers(eventContext);
        sendInternalNotifications(eventContext);
    }

    @Override
    protected void sendInternalNotifications(EventContext<DiscoveryHistory> eventContext) {
        DiscoveryHistory discovery = eventContext.getResourceObjects().getFirst();
        notificationProducer.produceNotificationText(Resource.DISCOVERY, discovery.getUuid(), NotificationRecipient.buildUserNotificationRecipient(eventContext.getUserUuid()), String.format("Discovery %s has finished with status %s", discovery.getName(), discovery.getStatus().getLabel()), discovery.getMessage());
    }

    public static EventMessage constructEventMessage(UUID discoveryUuid) {
        return new EventMessage(ResourceEvent.DISCOVERY_FINISHED, null, null, Resource.DISCOVERY, discoveryUuid, null);
    }

}
