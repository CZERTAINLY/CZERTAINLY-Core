package com.czertainly.core.event.transaction;

import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.tasks.ScheduledJobInfo;

public record ScheduledJobFinishedEvent(ScheduledJobInfo scheduledJobInfo, ScheduledTaskResult result) {
}
