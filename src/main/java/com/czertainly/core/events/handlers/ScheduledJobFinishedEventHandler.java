package com.czertainly.core.events.handlers;

import com.czertainly.api.exception.EventException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.ScheduledJob;
import com.czertainly.core.dao.repository.ScheduledJobsRepository;
import com.czertainly.core.evaluator.RuleEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.security.authz.SecuredUUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.SCHEDULED_JOB_FINISHED)
public class ScheduledJobFinishedEventHandler extends EventHandler<ScheduledJob> {

    private RuleEvaluator<ScheduledJob> ruleEvaluator;
    private ScheduledJobsRepository scheduledJobsRepository;

    @Autowired
    public void setRuleEvaluator(RuleEvaluator<ScheduledJob> ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    @Autowired
    public void setScheduledJobsRepository(ScheduledJobsRepository scheduledJobsRepository) {
        this.scheduledJobsRepository = scheduledJobsRepository;
    }

    @Override
    protected EventContext<ScheduledJob> prepareContext(EventMessage eventMessage) throws EventException {
        final ScheduledJob scheduledJob = scheduledJobsRepository.findByUuid(SecuredUUID.fromUUID(eventMessage.getObjectUuid())).orElseThrow(() -> new EventException(eventMessage.getResourceEvent(), "Scheduled job with UUID %s not found".formatted(eventMessage.getObjectUuid())));

        // TODO: load triggers from platform
        return new EventContext<>(eventMessage, ruleEvaluator, scheduledJob);
    }

    @Override
    protected void sendFollowUpEventsNotifications(EventContext<ScheduledJob> eventContext) {
        final ScheduledJob scheduledJob = eventContext.getResourceObjects().getFirst();
        final ScheduledTaskResult result = objectMapper.convertValue(eventContext.getData(), ScheduledTaskResult.class);
        notificationProducer.produceNotificationScheduledJobCompleted(scheduledJob.getUuid(), scheduledJob.getUserUuid(), scheduledJob.getJobName(), scheduledJob.getJobType(), result.getStatus().getLabel());
    }

    public static EventMessage constructEventMessage(UUID scheduledJobUuid, ScheduledTaskResult result) {
        return new EventMessage(ResourceEvent.SCHEDULED_JOB_FINISHED, Resource.SCHEDULED_JOB, scheduledJobUuid, result);
    }
}
