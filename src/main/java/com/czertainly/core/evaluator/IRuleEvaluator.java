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

    /**
     * Method to evaluate a list of Rules on an Object
     *
     * @param rules    List of the Rules
     * @param object   Object to evaluate Rules on
     * @return True if all the rules are satisfied, false otherwise
     */
    public boolean evaluateRules(List<Rule> rules, T object) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, RuleException;
    /**
     * Method to evaluate a list of Rules on a list of Objects
     *
     * @param rules    List of the Rules
     * @param listOfObjects   List of Objects to evaluate rules on
     * @return True if all the rules for all the objects are satisfied, false otherwise
     */
    public boolean evaluateRules(List<Rule> rules, List<T> listOfObjects) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, RuleException;
    /**
     * Method to evaluate a Condition on an Object
     *
     * @param condition    Condition to be evaluated
     * @param object   Object to evaluate condition on
     * @return True if the condition is satisfied, false otherwise
     */
    public Boolean evaluateCondition(RuleCondition condition, T object, Resource resource) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, RuleException;

}
