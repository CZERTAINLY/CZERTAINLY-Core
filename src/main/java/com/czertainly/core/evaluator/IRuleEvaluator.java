package com.czertainly.core.evaluator;

import com.czertainly.core.dao.entity.Rule;
import com.czertainly.core.dao.entity.RuleCondition;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@Component
public interface IRuleEvaluator<T> {

    public boolean evaluateRule(Rule rule, T object) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException;

    public boolean evaluateRuleOnList(Rule rule, List<T> list) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException;

    public boolean evaluateCondition(RuleCondition condition, T object) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException;

    public List<T> fetchObjectsSatisfyingRules(List<Rule> rules);
}
