package com.czertainly.core.api.web;

import com.czertainly.api.exception.*;
import com.czertainly.api.interfaces.core.web.DiscoveryController;
import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.czertainly.api.model.client.discovery.DiscoveryDto;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.api.model.common.UuidDto;
import com.czertainly.api.model.core.scheduler.ScheduleDiscoveryDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobDetailDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.DiscoveryService;
import com.czertainly.core.service.SchedulerService;
import com.czertainly.core.tasks.DiscoveryCertificateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class DiscoveryControllerImpl implements DiscoveryController {

    private final Logger logger = LoggerFactory.getLogger(DiscoveryControllerImpl.class);

    private DiscoveryService discoveryService;

    private SchedulerService schedulerService;

    @Autowired
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Autowired
    public void setDiscoveryService(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Override
    public DiscoveryResponseDto listDiscoveries(final SearchRequestDto request) {
        return discoveryService.listDiscoveries(SecurityFilter.create(), request);
    }

    @Override
    public DiscoveryHistoryDetailDto getDiscovery(@PathVariable String uuid) throws NotFoundException {
        return discoveryService.getDiscovery(SecuredUUID.fromString(uuid));
    }

    @Override
    public DiscoveryCertificateResponseDto getDiscoveryCertificates(
            String uuid,
            Boolean newlyDiscovered,
            int itemsPerPage,
            int pageNumber
    ) throws NotFoundException {
        return discoveryService.getDiscoveryCertificates(
                SecuredUUID.fromString(uuid),
                newlyDiscovered,
                itemsPerPage,
                pageNumber
        );
    }

    @Override
    public ResponseEntity<?> createDiscovery(@RequestBody DiscoveryDto request) throws ConnectorException, AlreadyExistException, AttributeException {
        final DiscoveryHistoryDetailDto modal = discoveryService.createDiscovery(request, true);
        discoveryService.runDiscoveryAsync(UUID.fromString(modal.getUuid()));
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(modal.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(modal.getUuid());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public ResponseEntity<?> scheduleDiscovery(final ScheduleDiscoveryDto scheduleDiscoveryDto) throws SchedulerException, ConnectorException, AlreadyExistException, AttributeException {
        final DiscoveryDto discoveryDto = scheduleDiscoveryDto.getRequest();
        discoveryService.createDiscovery(discoveryDto, false);

        String jobName;
        if (scheduleDiscoveryDto.getJobName() == null) {
            jobName = discoveryDto.getName();
        } else {
            jobName = scheduleDiscoveryDto.getJobName();
        }

        ScheduledJobDetailDto scheduledJob = schedulerService.registerScheduledJob(DiscoveryCertificateTask.class, jobName, scheduleDiscoveryDto.getCronExpression(), scheduleDiscoveryDto.isOneTime(), scheduleDiscoveryDto.getRequest());
        logger.info("Job {} was registered.", jobName);

        // TODO: construct location URI differently without hardcoded path
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/v1/scheduler/jobs/{uuid}")
                .buildAndExpand(scheduledJob.getUuid())
                .toUri();
        UuidDto dto = new UuidDto();
        dto.setUuid(scheduledJob.getUuid().toString());
        return ResponseEntity.created(location).body(dto);
    }

    @Override
    public void deleteDiscovery(@PathVariable String uuid) throws NotFoundException {
        discoveryService.deleteDiscovery(SecuredUUID.fromString(uuid));
    }

    @Override
    public void bulkDeleteDiscovery(List<String> discoveryUuids) throws NotFoundException {
        discoveryService.bulkRemoveDiscovery(SecuredUUID.fromList(discoveryUuids));
    }

    @Override
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return discoveryService.getSearchableFieldInformationByGroup();
    }

}
