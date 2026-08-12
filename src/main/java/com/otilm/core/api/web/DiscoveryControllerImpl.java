package com.otilm.core.api.web;

import com.otilm.api.exception.AlreadyExistException;
import com.otilm.api.exception.AttributeException;
import com.otilm.api.exception.ConnectorException;
import com.otilm.api.exception.NotFoundException;
import com.otilm.api.exception.NotSupportedException;
import com.otilm.api.exception.SchedulerException;
import com.otilm.api.interfaces.core.web.DiscoveryController;
import com.otilm.api.model.client.certificate.DiscoveryResponseDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.discovery.DiscoveryCertificateResponseDto;
import com.otilm.api.model.client.discovery.DiscoveryDetailDto;
import com.otilm.api.model.client.discovery.DiscoveryDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.common.UuidDto;
import com.otilm.api.model.common.attribute.common.BaseAttribute;
import com.otilm.api.model.connector.discovery.v2.DiscoverySupportedResourceDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.discovery.DiscoveryItemDto;
import com.otilm.api.model.core.logging.enums.Module;
import com.otilm.api.model.core.logging.enums.Operation;
import com.otilm.api.model.core.scheduler.ScheduleDiscoveryDto;
import com.otilm.api.model.core.scheduler.ScheduledJobDetailDto;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.core.aop.AuditLogged;
import com.otilm.core.logging.LogResource;
import com.otilm.core.security.authz.SecuredUUID;
import com.otilm.core.security.authz.SecurityFilter;
import com.otilm.core.service.DiscoveryExternalService;
import com.otilm.core.service.SchedulerExternalService;
import com.otilm.core.tasks.DiscoveryCertificateTask;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class DiscoveryControllerImpl implements DiscoveryController {

    private final Logger logger = LoggerFactory.getLogger(DiscoveryControllerImpl.class);

    private DiscoveryExternalService discoveryService;

    private SchedulerExternalService schedulerService;

    @Autowired
    public void setSchedulerService(SchedulerExternalService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @Autowired
    public void setDiscoveryService(DiscoveryExternalService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Override
    @AuditLogged(module = Module.DISCOVERY, resource = Resource.DISCOVERY, operation = Operation.LIST)
    public DiscoveryResponseDto listDiscoveries(final SearchRequestDto request) {
        return discoveryService.listDiscoveries(SecurityFilter.create(), request);
    }

    @Override
    @AuditLogged(module = Module.DISCOVERY, resource = Resource.DISCOVERY, operation = Operation.DETAIL)
    public DiscoveryDetailDto getDiscovery(@LogResource(uuid = true) @PathVariable String uuid)
            throws NotFoundException {
        return discoveryService.getDiscovery(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.DISCOVERY, resource = Resource.DISCOVERY, affiliatedResource = Resource.CERTIFICATE,
            operation = Operation.LIST)
    public DiscoveryCertificateResponseDto getDiscoveryCertificates(@LogResource(uuid = true) String uuid,
            Boolean newlyDiscovered, int itemsPerPage, int pageNumber) throws NotFoundException {
        return discoveryService
                .getDiscoveryCertificates(SecuredUUID.fromString(uuid), newlyDiscovered, itemsPerPage, pageNumber);
    }

    @Override
    @AuditLogged(module = Module.DISCOVERY, resource = Resource.DISCOVERY, operation = Operation.CREATE)
    public ResponseEntity<?> createDiscovery(@RequestBody DiscoveryDto request)
            throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException {
        final DiscoveryDetailDto modal = discoveryService.createDiscovery(request, true);
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
    @AuditLogged(module = Module.SCHEDULER, resource = Resource.SCHEDULED_JOB, affiliatedResource = Resource.DISCOVERY,
            operation = Operation.SCHEDULE)
    public ResponseEntity<?> scheduleDiscovery(final ScheduleDiscoveryDto scheduleDiscoveryDto)
            throws SchedulerException, ConnectorException, AlreadyExistException, AttributeException,
            NotFoundException {
        final DiscoveryDto discoveryDto = scheduleDiscoveryDto.getRequest();
        discoveryService.createDiscovery(discoveryDto, false);

        String jobName;
        if (scheduleDiscoveryDto.getJobName() == null) {
            jobName = discoveryDto.getName();
        } else {
            jobName = scheduleDiscoveryDto.getJobName();
        }

        ScheduledJobDetailDto scheduledJob = schedulerService
                .registerScheduledJob(DiscoveryCertificateTask.class, jobName, scheduleDiscoveryDto.getCronExpression(),
                        scheduleDiscoveryDto.isOneTime(), scheduleDiscoveryDto.getRequest());
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
    @AuditLogged(module = Module.DISCOVERY, resource = Resource.DISCOVERY, operation = Operation.DELETE)
    public void deleteDiscovery(@LogResource(uuid = true) @PathVariable String uuid) throws NotFoundException {
        discoveryService.deleteDiscovery(SecuredUUID.fromString(uuid));
    }

    @Override
    @AuditLogged(module = Module.DISCOVERY, resource = Resource.DISCOVERY, operation = Operation.DELETE)
    public void bulkDeleteDiscovery(@LogResource(uuid = true) List<String> discoveryUuids) throws NotFoundException {
        discoveryService.bulkRemoveDiscovery(SecuredUUID.fromList(discoveryUuids));
    }

    @Override
    @AuditLogged(module = Module.CORE, resource = Resource.SEARCH_FILTER, affiliatedResource = Resource.DISCOVERY,
            operation = Operation.LIST)
    public List<SearchFieldDataByGroupDto> getSearchableFieldInformation() {
        return discoveryService.getSearchableFieldInformationByGroup();
    }

    /*
     * Discovery v2, not implemented yet. These seven exist because DiscoveryController declares them abstract, so this
     * class does not compile without them; they answer 501 rather than nothing, so a caller reaching one gets an answer
     * it can act on.
     *
     * Deliberately carrying neither @AuditLogged nor authorization annotations: there is no operation to audit and no
     * resource to check while the body does nothing. Authentication still applies -- SecurityConfig authenticates every
     * request -- and the resource-level authorization these need arrives with the real implementations. The checked
     * exceptions stay on the signatures so filling them in later does not change the contract.
     */

    @Override
    public PaginationResponseDto<DiscoveryItemDto> getDiscoveryItems(String uuid, Resource resource,
            Boolean newlyDiscovered, int itemsPerPage, int pageNumber) throws NotFoundException {
        throw notImplemented();
    }

    @Override
    public void stopDiscovery(String uuid) throws NotFoundException, ConnectorException {
        throw notImplemented();
    }

    @Override
    public void resumeDiscovery(String uuid) throws NotFoundException, ConnectorException {
        throw notImplemented();
    }

    @Override
    public void cancelDiscovery(String uuid) throws NotFoundException, ConnectorException {
        throw notImplemented();
    }

    @Override
    public List<DiscoverySupportedResourceDto> listDiscoveryResources(String connectorUuid) throws NotFoundException {
        throw notImplemented();
    }

    @Override
    public List<BaseAttribute> getDiscoveryAttributes(String connectorUuid)
            throws NotFoundException, ConnectorException {
        throw notImplemented();
    }

    @Override
    public List<BaseAttribute> getDiscoveryResourceAttributes(String connectorUuid, Resource resource)
            throws NotFoundException, ConnectorException {
        throw notImplemented();
    }

    // NotSupportedException, not ResponseStatusException: the global ExceptionHandlingAdvice catches every
    // Exception into a 500 before Spring's response-status resolver runs, so only an exception with its own
    // dedicated handler reaches the wire with the intended status -- NotSupportedException maps to 501 there.
    private static NotSupportedException notImplemented() {
        return new NotSupportedException("Discovery v2 is not implemented by this deployment yet");
    }

}
