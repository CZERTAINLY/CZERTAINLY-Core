package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.SchedulerException;
import com.czertainly.api.interfaces.core.web.SchedulerController;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobDetailDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobHistoryResponseDto;
import com.czertainly.api.model.core.scheduler.ScheduledJobsResponseDto;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SchedulerControllerImpl implements SchedulerController {

    private SchedulerService schedulerService;

    @Override
    public ScheduledJobsResponseDto listScheduledJobs(final PaginationRequestDto pagination) {
        return schedulerService.listScheduledJobs(SecurityFilter.create(), pagination);
    }

    @Override
    public ScheduledJobDetailDto getScheduledJobDetail(final String uuid) throws NotFoundException {
        return schedulerService.getScheduledJobDetail(uuid);
    }

    @Override
    public void deleteScheduledJob(final String uuid) {
        schedulerService.deleteScheduledJob(uuid);
    }

    @Override
    public ScheduledJobHistoryResponseDto getScheduledJobHistory(final PaginationRequestDto pagination, String uuid) {
        return schedulerService.getScheduledJobHistory(SecurityFilter.create(), pagination, uuid);
    }

    @Override
    public void enableScheduledJob(String uuid) throws SchedulerException, NotFoundException {
        schedulerService.enableScheduledJob(uuid);
    }

    @Override
    public void disableScheduledJob(String uuid) throws SchedulerException, NotFoundException {
        schedulerService.disableScheduledJob(uuid);
    }

    // SETTERs

    @Autowired
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }
}
