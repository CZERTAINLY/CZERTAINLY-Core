package com.czertainly.core.messaging.listeners;

import com.czertainly.api.clients.SchedulerApiClient;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionMessage;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.api.model.scheduler.SchedulerJobHistory;
import com.czertainly.core.messaging.configuration.RabbitMQConstants;
import com.czertainly.core.tasks.SchedulerJobProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SchedulerListener {

    private ApplicationContext applicationContext;

    private SchedulerApiClient schedulerApiClient;

    private static final Logger logger = LoggerFactory.getLogger(SchedulerListener.class);

    @RabbitListener(queues = RabbitMQConstants.QUEUE_SCHEDULER_NAME, messageConverter = "jsonMessageConverter")
    public void processMessage(SchedulerJobExecutionMessage schedulerMessage) {
        logger.info("Received scheduler message: {}", schedulerMessage);

        try {
            final Class<?> clazz = Class.forName(schedulerMessage.getClassToBeExecuted());
            final Object clazzObject = applicationContext.getBean(clazz);
            if (clazzObject != null && clazzObject instanceof SchedulerJobProcessor) {
                logger.info("Job {} is executed.", schedulerMessage.getJobName());
                final SchedulerJobProcessor schedulerJobProcessor = ((SchedulerJobProcessor) clazzObject);
                schedulerJobProcessor.processTask(schedulerMessage.getJobID());
                logger.info("Job {} was processed.", schedulerMessage.getJobName());
            }

        } catch (ConnectorException | ClassNotFoundException e) {
            logger.error("Unable to process the job {}", schedulerMessage.getJobName());
            try {
                schedulerApiClient.informJobHistory(new SchedulerJobHistory(schedulerMessage.getJobID(), SchedulerJobExecutionStatus.FAILED));
            } catch (ConnectorException ex) {
                logger.error("Unable to inform scheduler about the result of job {}.", schedulerMessage.getJobName(), e.getMessage());
            }
        }
    }

    // SETTERs

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Autowired
    public void setSchedulerApiClient(SchedulerApiClient schedulerApiClient) {
        this.schedulerApiClient = schedulerApiClient;
    }
}
