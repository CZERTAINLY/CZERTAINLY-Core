package com.czertainly.core.events.handlers;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.common.events.data.DiscoveryFinishedEventData;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.repository.DiscoveryRepository;
import com.czertainly.core.evaluator.TriggerEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.events.data.DiscoveryResult;
import com.czertainly.core.events.transaction.ScheduledJobFinishedEvent;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.tasks.ScheduledJobInfo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.DISCOVERY_FINISHED)
public class DiscoveryFinishedEventHandler extends EventHandler<DiscoveryHistory> {

    private final DiscoveryRepository discoveryRepository;

    protected DiscoveryFinishedEventHandler(DiscoveryRepository repository, TriggerEvaluator<DiscoveryHistory> ruleEvaluator) {
        super(repository, ruleEvaluator);
        discoveryRepository = repository;
    }

    @Override
    protected EventContext<DiscoveryHistory> prepareContext(EventMessage eventMessage) throws EventException {
        DiscoveryHistory discovery = discoveryRepository.findByUuid(eventMessage.getObjectUuid()).orElseThrow(() -> new EventException(eventMessage.getResourceEvent(), "Discovery with UUID %s not found".formatted(eventMessage.getObjectUuid())));
        DiscoveryResult discoveryResult = objectMapper.convertValue(eventMessage.getData(), DiscoveryResult.class);

        // set discovery status to completed when discovery is in preprocessing state coming from certificate discovered event
        if (discoveryResult.getDiscoveryStatus() == DiscoveryStatus.PROCESSING) {
            String message = discoveryResult.getMessage() == null ? "Discovery completed successfully." : "Discovery completed successfully. " + discoveryResult.getMessage();
            discovery.setMessage(message);
            discovery.setStatus(DiscoveryStatus.COMPLETED);
            discovery.setEndTime(new Date());
            discoveryRepository.save(discovery);
        }

        // TODO: load triggers from platform
        return new EventContext<>(eventMessage, triggerEvaluator, discovery, getEventData(discovery, eventMessage.getData()));
    }

    @Override
    protected Object getEventData(DiscoveryHistory discovery, Object eventMessageData) {
        DiscoveryFinishedEventData eventData = new DiscoveryFinishedEventData();
        eventData.setDiscoveryUuid(discovery.getUuid().toString());
        eventData.setDiscoveryName(discovery.getName());
        eventData.setDiscoveryConnectorUuid(discovery.getConnectorUuid().toString());
        eventData.setDiscoveryConnectorName(discovery.getConnectorName());
        eventData.setDiscoveryStatus(discovery.getStatus());
        eventData.setTotalCertificateDiscovered(discovery.getTotalCertificatesDiscovered() == null ? 0 : discovery.getTotalCertificatesDiscovered());
        eventData.setDiscoveryMessage(discovery.getMessage());

        return eventData;
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<DiscoveryHistory> eventContext) {
        DiscoveryHistory discovery = eventContext.getResourceObjects().getFirst();
        Object eventData = eventContext.getResourceObjectsEventData().getFirst();
        NotificationMessage notificationMessage = new NotificationMessage(eventContext.getResourceEvent(), Resource.DISCOVERY, discovery.getUuid(), null, NotificationRecipient.buildUserNotificationRecipient(eventContext.getUserUuid()), eventData);
        notificationProducer.produceMessage(notificationMessage);

        // if discovery was scheduled, raise application event to notify that scheduled discovery has finished
        if (eventContext.getScheduledJobInfo() != null) {
            ScheduledTaskResult scheduledTaskResult = new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, discovery.getMessage(), Resource.DISCOVERY, discovery.getUuid().toString());
            applicationEventPublisher.publishEvent(new ScheduledJobFinishedEvent(eventContext.getScheduledJobInfo(), scheduledTaskResult));
        }
    }

    public static EventMessage constructEventMessage(UUID discoveryUuid, UUID userUuid, ScheduledJobInfo scheduledJobInfo, DiscoveryResult discoveryResult) {
        return new EventMessage(ResourceEvent.DISCOVERY_FINISHED, Resource.DISCOVERY, discoveryUuid, null, null, discoveryResult, userUuid, scheduledJobInfo);
    }

}
