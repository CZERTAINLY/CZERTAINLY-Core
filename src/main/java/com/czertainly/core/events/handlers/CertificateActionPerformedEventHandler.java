package com.czertainly.core.events.handlers;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Group;
import com.czertainly.core.dao.entity.UniquelyIdentifiedAndAudited;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.evaluator.CertificateTriggerEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventContextTriggers;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.events.data.EventDataBuilder;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.messaging.model.NotificationMessage;
import com.czertainly.core.messaging.model.NotificationRecipient;
import com.czertainly.core.model.auth.ResourceAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.CERTIFICATE_ACTION_PERFORMED)
public class CertificateActionPerformedEventHandler extends EventHandler<Certificate> {

    protected CertificateActionPerformedEventHandler(CertificateRepository repository, CertificateTriggerEvaluator ruleEvaluator) {
        super(repository, ruleEvaluator);
    }

    @Override
    protected Object getEventData(Certificate certificate, Object eventMessageData) {
        ResourceAction action = objectMapper.convertValue(eventMessageData, ResourceAction.class);

        return EventDataBuilder.getCertificateActionPerformedEventData(certificate, action);
    }

    @Override
    protected List<EventContextTriggers> getOverridingTriggers(EventContext<Certificate> eventContext, Certificate object) throws EventException {
        List<EventContextTriggers> eventContextTriggers = new ArrayList<>();

        if (object.getGroups() != null && !object.getGroups().isEmpty()) {
            for (Group group : object.getGroups()) {
                eventContextTriggers.add(loadTriggers(eventContext, Resource.GROUP, group.getUuid()));
            }
        }
        if (object.getRaProfileUuid() != null) {
            eventContextTriggers.add(loadTriggers(eventContext, Resource.RA_PROFILE, object.getRaProfileUuid()));
        }

        return eventContextTriggers;
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<Certificate> eventContext) {
        // TODO: move event history logging to event handler? And should it also trigger action performed failed as event?
        Certificate certificate = eventContext.getResourceObjects().getFirst();
        Object eventData = eventContext.getResourceObjectsEventData().getFirst();

        List<NotificationRecipient> recipients = NotificationRecipient.buildUsersAndGroupsNotificationRecipients(certificate.getOwner() == null ? null : List.of(certificate.getOwner().getUuid()), certificate.getGroups() == null ? null : certificate.getGroups().stream().map(UniquelyIdentifiedAndAudited::getUuid).toList());
        NotificationMessage notificationMessage = new NotificationMessage(eventContext.getEvent(), Resource.CERTIFICATE, certificate.getUuid(), null, recipients, eventData);
        notificationProducer.produceMessage(notificationMessage);
    }

    public static EventMessage constructEventMessage(UUID certificateUuid, ResourceAction action) {
        return new EventMessage(ResourceEvent.CERTIFICATE_ACTION_PERFORMED, Resource.CERTIFICATE, certificateUuid, action);
    }

}
