package com.czertainly.core.tasks;

import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.DiscoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Component
@NoArgsConstructor
@Transactional
public class DiscoveryCertificateTask implements ScheduledJobTask {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryCertificateTask.class);

    private DiscoveryService discoveryService;

    private ObjectMapper mapper = new ObjectMapper();

    private PlatformTransactionManager transactionManager;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.FFF");

    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Autowired
    public void setDiscoveryService(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    public String getDefaultJobName() {
        return "DiscoveryCertificateTask";
    }

    public String getDefaultCronExpression() {
        return null;
    }

    public boolean isDefaultOneTimeJob() {
        return false;
    }

    public String getJobClassName() {
        return this.getClass().getName();
    }

    public boolean isSystemJob() {
        return false;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ScheduledTaskResult performJob(final ScheduledJobInfo scheduledJobInfo, final Object taskData) {
        final DiscoveryDto discoveryDto = mapper.convertValue(taskData, DiscoveryDto.class);
        discoveryDto.setName(discoveryDto.getName() + prepareTimeSuffix());

        // Define a new transaction
        DiscoveryHistoryDetailDto discovery = null;
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            final DiscoveryHistoryDetailDto discoveryCreated = discoveryService.createDiscovery(discoveryDto, true);
            discovery = discoveryCreated;

            // Register an after-commit synchronization
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // After the transaction with new discovery persisting commits, run discovery
                    discoveryService.runDiscovery(UUID.fromString(discoveryCreated.getUuid()), scheduledJobInfo); // Use the entity saved in the transaction
                }
            });
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            final String errorMessage = String.format("Unable to create discovery %s for job %s. Error: %s", discoveryDto.getName(), scheduledJobInfo == null ? "" : scheduledJobInfo.jobName(), e.getMessage());
            logger.error(errorMessage);
            return new ScheduledTaskResult(SchedulerJobExecutionStatus.FAILED, errorMessage, discovery != null ? Resource.DISCOVERY : null, discovery != null ? discovery.getUuid() : null);
        }

        return null;
    }

    private String prepareTimeSuffix() {
        return "_" + sdf.format(new Date());
    }
}
