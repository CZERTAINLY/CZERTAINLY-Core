package com.czertainly.core.events;

import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.repository.DiscoveryCertificateRepository;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.evaluator.RuleEvaluator;
import com.czertainly.core.messaging.model.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

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
    protected EventContext<DiscoveryHistory> prepareContext(EventMessage eventMessage) {
        EventContext<DiscoveryHistory> context = new EventContext<>(eventMessage.getResource(), eventMessage.getResourceEvent(), ruleEvaluator, eventMessage.getOverrideObjectUuid());

        // TODO: add EventException to handle null resource object
        context.getResourceObjects().add(discoveryRepository.findByUuid(eventMessage.getObjectUuid()).orElse(null));
        return context;
    }

    @Override
    protected void sendInternalNotifications(EventContext<DiscoveryHistory> eventContext) {

    }

    @Override
    public void handleEvent(EventMessage eventMessage) {

    }

}
