package com.czertainly.core.evaluator;

import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.Rule;
import com.czertainly.core.dao.entity.RuleCondition;
import com.czertainly.core.dao.entity.UniquelyIdentified;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public interface IRuleEvaluator<T> {

    public boolean evaluateRules(List<Rule> rules, T object) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, RuleException;

    public boolean evaluateRulesOnList(List<Rule> rules, List<T> listOfObjects) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, RuleException;

    public Boolean evaluateCondition(RuleCondition condition, T object, Resource resource) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, RuleException;

}
