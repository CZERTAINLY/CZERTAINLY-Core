package com.czertainly.core.tasks;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.discovery.DiscoveryStatus;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.entity.ScheduledJob;
import com.czertainly.core.model.ScheduledTaskResult;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.core.util.AuthHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
@NoArgsConstructor
@Transactional
public class DiscoveryCertificateTask extends SchedulerJobProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryCertificateTask.class);

    private DiscoveryService discoveryService;

    private ObjectMapper mapper = new ObjectMapper();

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.FFF");

    @Autowired
    public void setDiscoveryService(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Override
    String getDefaultJobName() {
        return "DiscoveryCertificateTask";
    }

    @Override
    String getDefaultCronExpression() {
        return null;
    }

    @Override
    boolean isDefaultOneTimeJob() {
        return false;
    }

    @Override
    String getJobClassName() {
        return this.getClass().getName();
    }

    @Override
    boolean systemJob() {
        return false;
    }

    @Override
    @AuditLogged(originator = ObjectType.SCHEDULER, affected = ObjectType.DISCOVERY, operation = OperationType.CREATE)
    public ScheduledTaskResult performJob(final String jobName) {
        final ScheduledJob scheduledJob = scheduledJobsRepository.findByJobName(jobName);
        AuthHelper.authenticateAsUser(scheduledJob.getUserUuid());

        DiscoveryHistory discoveryHistoryModal = null;
        final DiscoveryDto discoveryDto = mapper.convertValue(scheduledJob.getObjectData(), DiscoveryDto.class);
        discoveryDto.setName(discoveryDto.getName() + prepareTimeSuffix());
        try {
            discoveryHistoryModal = discoveryService.createDiscoveryModal(discoveryDto, true);
            discoveryService.createDiscovery(discoveryHistoryModal);
        } catch (AlreadyExistException | ConnectorException e) {
            final String errorMessage = String.format("Unable to create discovery %s for job %s. Error: %s", discoveryDto.getName(), jobName, e.getMessage());
            logger.error(errorMessage);
            return new ScheduledTaskResult(SchedulerJobExecutionStatus.FAILED, errorMessage, discoveryHistoryModal != null ? Resource.DISCOVERY : null, discoveryHistoryModal != null ? discoveryHistoryModal.getUuid().toString() : null);
        }

        if (discoveryHistoryModal.getStatus() == DiscoveryStatus.COMPLETED) return new ScheduledTaskResult(SchedulerJobExecutionStatus.SUCCESS, null, Resource.DISCOVERY, discoveryHistoryModal.getUuid().toString());
        return new ScheduledTaskResult(SchedulerJobExecutionStatus.FAILED, discoveryHistoryModal.getMessage(), Resource.DISCOVERY, discoveryHistoryModal.getUuid().toString());
    }

    private String prepareTimeSuffix() {
        return "_" + sdf.format(new Date());
    }

}
