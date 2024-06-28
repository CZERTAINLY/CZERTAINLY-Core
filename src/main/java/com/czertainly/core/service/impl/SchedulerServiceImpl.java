package com.czertainly.core.service.impl;

import com.czertainly.api.clients.SchedulerApiClient;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.SchedulerException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.*;
import com.czertainly.api.model.scheduler.SchedulerJobDto;
import com.czertainly.api.model.scheduler.SchedulerRequestDto;
import com.czertainly.api.model.scheduler.UpdateScheduledJob;
import com.czertainly.core.dao.entity.ScheduledJob;
import com.czertainly.core.dao.entity.ScheduledJobHistory;
import com.czertainly.core.dao.repository.ScheduledJobHistoryRepository;
import com.czertainly.core.dao.repository.ScheduledJobsRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.SchedulerService;
import com.czertainly.core.util.RequestValidatorHelper;
import com.czertainly.core.util.converter.Sql2PredicateConverter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class SchedulerServiceImpl implements SchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerServiceImpl.class);

    private ScheduledJobsRepository scheduledJobsRepository;

    private ScheduledJobHistoryRepository scheduledJobHistoryRepository;

    private SchedulerApiClient schedulerApiClient;

    @Override
    @ExternalAuthorization(resource = Resource.SCHEDULED_JOB, action = ResourceAction.LIST)
    public ScheduledJobsResponseDto listScheduledJobs(final SecurityFilter filter, final PaginationRequestDto paginationRequestDto) {
        RequestValidatorHelper.revalidatePaginationRequestDto(paginationRequestDto);
        final Pageable pageable = PageRequest.of(paginationRequestDto.getPageNumber() - 1, paginationRequestDto.getItemsPerPage());
        final List<ScheduledJob> scheduledJobList = scheduledJobsRepository.findUsingSecurityFilter(filter, List.of(), null, pageable, null);

        final Long maxItems = scheduledJobsRepository.countUsingSecurityFilter(filter, null);
        final ScheduledJobsResponseDto responseDto = new ScheduledJobsResponseDto();
        responseDto.setScheduledJobs(scheduledJobList.stream()
                .map(job -> job.mapToDto(scheduledJobHistoryRepository.findTopByScheduledJobUuidOrderByJobExecutionDesc(job.getUuid())))
                .collect(Collectors.toList()));
        responseDto.setItemsPerPage(paginationRequestDto.getItemsPerPage());
        responseDto.setPageNumber(paginationRequestDto.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / paginationRequestDto.getItemsPerPage()));
        return responseDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SCHEDULED_JOB, action = ResourceAction.DETAIL)
    public ScheduledJobDetailDto getScheduledJobDetail(final String uuid) throws NotFoundException {
        final ScheduledJob scheduledJob = scheduledJobsRepository.findByUuid(SecuredUUID.fromString(uuid)).orElseThrow(() -> new NotFoundException(ScheduledJob.class, uuid));
        return scheduledJob.mapToDetailDto(scheduledJobHistoryRepository.findTopByScheduledJobUuidOrderByJobExecutionDesc(UUID.fromString(uuid)));
    }

    @Override
    @ExternalAuthorization(resource = Resource.SCHEDULED_JOB, action = ResourceAction.DELETE)
    public void deleteScheduledJob(final String uuid) {
        final Optional<ScheduledJob> scheduledJobOptional = scheduledJobsRepository.findByUuid(SecuredUUID.fromString(uuid));
        if (scheduledJobOptional.isPresent()) {
            final ScheduledJob scheduledJob = scheduledJobOptional.get();

            if (scheduledJob.isSystem()) {
                logger.warn("Unable to delete system job.");
                throw new ValidationException(ValidationError.create("Unable to delete system job."));
            }

            if(scheduledJobHistoryRepository.existsByScheduledJobUuid(UUID.fromString(uuid))) {
                logger.warn("Unable to delete job with existing history.");
                throw new ValidationException(ValidationError.create("Unable to delete job with existing history."));
            }

            try {
                schedulerApiClient.deleteScheduledJob(scheduledJob.getJobName());
                scheduledJobsRepository.deleteById(UUID.fromString(uuid));
            } catch (SchedulerException e) {
                logger.error("Unable to delete job {}", scheduledJob.getJobName(), e.getMessage());
            }
        }
    }

    @Override
    @ExternalAuthorization(resource = Resource.SCHEDULED_JOB, action = ResourceAction.DETAIL)
    public ScheduledJobHistoryResponseDto getScheduledJobHistory(final SecurityFilter filter, final PaginationRequestDto paginationRequestDto, final String uuid) {
        final BiFunction<Root<ScheduledJobHistory>, CriteriaBuilder, Predicate> additionalWhereClause = (root, cb) -> Sql2PredicateConverter.constructFilterForJobHistory(cb, root, UUID.fromString(uuid));

        RequestValidatorHelper.revalidatePaginationRequestDto(paginationRequestDto);
        final Pageable pageable = PageRequest.of(paginationRequestDto.getPageNumber() - 1, paginationRequestDto.getItemsPerPage());
        final List<ScheduledJobHistory> scheduledJobHistoryList = scheduledJobHistoryRepository.findUsingSecurityFilter(filter, List.of(), additionalWhereClause, pageable, (root, cb) -> cb.desc(root.get("jobExecution")));

        final Long maxItems = scheduledJobHistoryRepository.countUsingSecurityFilter(filter, additionalWhereClause);
        final ScheduledJobHistoryResponseDto responseDto = new ScheduledJobHistoryResponseDto();
        responseDto.setScheduledJobHistory(scheduledJobHistoryList.stream().map(ScheduledJobHistory::mapToDto).collect(Collectors.toList()));
        responseDto.setItemsPerPage(paginationRequestDto.getItemsPerPage());
        responseDto.setPageNumber(paginationRequestDto.getPageNumber());
        responseDto.setTotalItems(maxItems);
        responseDto.setTotalPages((int) Math.ceil((double) maxItems / paginationRequestDto.getItemsPerPage()));

        return responseDto;
    }

    @Override
    @ExternalAuthorization(resource = Resource.SCHEDULED_JOB, action = ResourceAction.ENABLE)
    public void enableScheduledJob(final String uuid) throws SchedulerException, NotFoundException {
        changeScheduledJobState(uuid, true);
    }

    @Override
    @ExternalAuthorization(resource = Resource.SCHEDULED_JOB, action = ResourceAction.ENABLE)
    public void disableScheduledJob(final String uuid) throws SchedulerException, NotFoundException {
        changeScheduledJobState(uuid, false);
    }

    @Override
    public ScheduledJobDetailDto updateScheduledJob(String uuid, UpdateScheduledJob request) throws NotFoundException, SchedulerException {
        Optional<ScheduledJob> scheduledJobOptional = scheduledJobsRepository.findByUuid(SecuredUUID.fromString(uuid));
        if (scheduledJobOptional.isPresent()) {
            ScheduledJob scheduledJob = scheduledJobOptional.get();
            scheduledJob.setCronExpression(request.getCronExpression());
            scheduledJobsRepository.save(scheduledJob);
            if (scheduledJob.isSystem()) throw  new ValidationException("Cannot updated system job.");
            SchedulerRequestDto schedulerRequestDto = new SchedulerRequestDto(
                    new SchedulerJobDto(scheduledJob.getUuid(), scheduledJob.getJobName(), request.getCronExpression(), scheduledJob.getJobClassName())
            );
            schedulerApiClient.updateScheduledJob(schedulerRequestDto);

        } else {
            throw new NotFoundException(ScheduledJob.class, uuid);
        }

        return null;
    }

    private void changeScheduledJobState(final String uuid, final boolean enabled) throws SchedulerException, NotFoundException {
        final Optional<ScheduledJob> scheduledJobOptional = scheduledJobsRepository.findByUuid(SecuredUUID.fromString(uuid));
        if (scheduledJobOptional.isPresent()) {
            final ScheduledJob scheduledJob = scheduledJobOptional.get();
            if (enabled) {
                schedulerApiClient.enableScheduledJob(scheduledJob.getJobName());
                scheduledJob.setEnabled(true);
            } else {
                schedulerApiClient.disableScheduledJob(scheduledJob.getJobName());
                scheduledJob.setEnabled(false);
            }
            scheduledJobsRepository.save(scheduledJob);
        } else {
            throw new NotFoundException("There is no such scheduled job {}", uuid);
        }
    }

    // SETTERs

    @Autowired
    public void setScheduledJobsRepository(ScheduledJobsRepository scheduledJobsRepository) {
        this.scheduledJobsRepository = scheduledJobsRepository;
    }

    @Autowired
    public void setSchedulerApiClient(SchedulerApiClient schedulerApiClient) {
        this.schedulerApiClient = schedulerApiClient;
    }

    @Autowired
    public void setScheduledJobHistoryRepository(ScheduledJobHistoryRepository scheduledJobHistoryRepository) {
        this.scheduledJobHistoryRepository = scheduledJobHistoryRepository;
    }
}
