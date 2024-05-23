package com.czertainly.core.api.web;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.TriggerController;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.service.TriggerService;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TriggerControllerImpl implements TriggerController {

    private TriggerService triggerService;

    @Autowired
    public void setTriggerService(TriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
    }

    @Override
    public List<TriggerDto> listTriggers(Resource resource, Resource eventResource) {
        return triggerService.listTriggers(resource, eventResource);
    }

    @Override
    public TriggerDetailDto createTrigger(TriggerRequestDto request) {
        return triggerService.createTrigger(request);
    }

    @Override
    public TriggerDetailDto getTrigger(String triggerUuid) throws NotFoundException {
        return triggerService.getTrigger(triggerUuid);
    }

    @Override
    public TriggerDetailDto updateTrigger(String triggerUuid, UpdateTriggerRequestDto request) throws NotFoundException {
        return triggerService.updateTrigger(triggerUuid, request);
    }

    @Override
    public void deleteTrigger(String triggerUuid) throws NotFoundException {
        triggerService.deleteTrigger(triggerUuid);
    }

    @Override
    public List<TriggerHistoryDto> getTriggerHistory(String triggerUuid, String triggerObjectUuid) {
        return triggerService.getTriggerHistory(triggerUuid, triggerObjectUuid);
    }
}
