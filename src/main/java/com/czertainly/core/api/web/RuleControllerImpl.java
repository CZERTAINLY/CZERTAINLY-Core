package com.czertainly.core.api.web;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.web.RuleController;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.service.RuleService;
import com.czertainly.core.util.converter.ResourceCodeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RuleControllerImpl implements RuleController {

    private RuleService ruleService;

    @Autowired
    public void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @InitBinder
    public void initBinder(final WebDataBinder webdataBinder) {
        webdataBinder.registerCustomEditor(Resource.class, new ResourceCodeConverter());
    }

    @Override
    public List<ConditionDto> listConditions(Resource resource) {
        return ruleService.listConditions(resource);
    }

    @Override
    public ConditionDto createCondition(ConditionRequestDto request) throws AlreadyExistException {
        return ruleService.createCondition(request);
    }

    @Override
    public ConditionDto getCondition(String conditionUuid) throws NotFoundException {
        return ruleService.getCondition(conditionUuid);
    }

    @Override
    public ConditionDto updateCondition(String conditionUuid, UpdateConditionRequestDto request) throws NotFoundException {
        return ruleService.updateCondition(conditionUuid, request);
    }

    @Override
    public void deleteCondition(String conditionUuid) throws NotFoundException {
        ruleService.deleteCondition(conditionUuid);
    }

    @Override
    public List<RuleDto> listRules(Resource resource) {
        return ruleService.listRules(resource);
    }

    @Override
    public RuleDetailDto createRule(RuleRequestDto request) throws NotFoundException, AlreadyExistException {
        return ruleService.createRule(request);
    }

    @Override
    public RuleDetailDto getRule(String ruleUuid) throws NotFoundException {
        return ruleService.getRule(ruleUuid);
    }

    @Override
    public RuleDetailDto updateRule(String ruleUuid, UpdateRuleRequestDto request) throws NotFoundException {
        return ruleService.updateRule(ruleUuid, request);
    }

    @Override
    public void deleteRule(String ruleUuid) throws NotFoundException {
        ruleService.deleteRule(ruleUuid);
    }
}
