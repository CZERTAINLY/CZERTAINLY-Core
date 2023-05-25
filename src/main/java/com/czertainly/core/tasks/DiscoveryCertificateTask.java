package com.czertainly.core.tasks;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.core.audit.ObjectType;
import com.czertainly.api.model.core.audit.OperationType;
import com.czertainly.api.model.scheduler.SchedulerJobExecutionStatus;
import com.czertainly.core.aop.AuditLogged;
import com.czertainly.core.dao.entity.DiscoveryHistory;
import com.czertainly.core.dao.entity.ScheduledJob;
import com.czertainly.core.dao.repository.ScheduledJobsRepository;
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
public class DiscoveryCertificateTask extends SchedulerJobProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryCertificateTask.class);

    private ScheduledJobsRepository scheduledJobsRepository;

    private DiscoveryService discoveryService;

    private ObjectMapper mapper = new ObjectMapper();

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");

    @Override
    String getDefaultJobName() {
        return "DiscoveryCertificateTask";
    }

    @Override
    String getDefaultCronExpression() {
        return null;
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
    @Transactional
    SchedulerJobExecutionStatus performJob(final String jobName) {
        final ScheduledJob scheduledJob = scheduledJobsRepository.findByJobName(jobName);
        AuthHelper.authenticateAsUser(scheduledJob.getUserUuid());

        final DiscoveryDto discoveryDto = mapper.convertValue(scheduledJob.getObjectData(), DiscoveryDto.class);
        discoveryDto.setName(discoveryDto.getName() + prepareTimeSuffix());
        try {
            final DiscoveryHistory discoveryHistoryModal = discoveryService.createDiscoveryModal(discoveryDto, true);
            discoveryService.createDiscovery(discoveryHistoryModal);
        } catch (AlreadyExistException | ConnectorException e) {
            logger.error("Unable to create discovery {}", jobName);
            return SchedulerJobExecutionStatus.FAILED;
        }
        return SchedulerJobExecutionStatus.SUCCESS;
    }

    private String prepareTimeSuffix() {
        return "_" + sdf.format(new Date());
    }

    //SETTERs

    @Autowired
    public void setScheduledJobsRepository(ScheduledJobsRepository scheduledJobsRepository) {
        this.scheduledJobsRepository = scheduledJobsRepository;
    }

    @Autowired
    public void setDiscoveryService(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

}
