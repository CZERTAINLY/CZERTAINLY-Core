package com.czertainly.core.events.handlers;

import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.core.dao.entity.ScheduledJob;
import com.czertainly.core.dao.repository.ScheduledJobsRepository;
import com.czertainly.core.evaluator.RuleEvaluator;
import com.czertainly.core.events.EventContext;
import com.czertainly.core.events.EventHandler;
import com.czertainly.core.messaging.model.EventMessage;
import com.czertainly.core.model.ScheduledTaskResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Component(ResourceEvent.Codes.SCHEDULED_JOB_FINISHED)
public class ScheduledJobFinishedEventHandler extends EventHandler<ScheduledJob> {

    protected ScheduledJobFinishedEventHandler(ScheduledJobsRepository repository, RuleEvaluator<ScheduledJob> ruleEvaluator) {
        super(repository, ruleEvaluator);
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
