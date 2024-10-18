package com.czertainly.core.tasks;

import com.czertainly.core.model.ScheduledTaskResult;

public interface ScheduledJobTask {

    String getDefaultJobName();

    String getDefaultCronExpression();

    boolean isDefaultOneTimeJob();

    String getJobClassName();

    boolean isSystemJob();

    ScheduledTaskResult performJob(final ScheduledJobInfo scheduledJobInfo, final Object taskData);

}
