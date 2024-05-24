package com.czertainly.core.evaluator;

import com.czertainly.api.exception.RuleException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.workflows.Rule;
import com.czertainly.core.dao.entity.workflows.ConditionItem;
import com.czertainly.core.dao.entity.workflows.Trigger;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;

import java.util.List;

public interface IRuleEvaluator<T> {

    /**
     * Method to evaluate a list of Rules on an Object
     *
     * @param rules    List of the Rules
     * @param object   Object to evaluate Rules on
     * @return True if all the rules are satisfied, false otherwise
     */
    public boolean evaluateRules(List<Rule> rules, T object, TriggerHistory triggerHistory) throws RuleException;
    /**
     * Method to evaluate a list of Rules on a list of Objects
     *
     * @param rules    List of the Rules
     * @param listOfObjects   List of Objects to evaluate rules on
     * @return True if all the rules for all the objects are satisfied, false otherwise
     */
    public boolean evaluateRules(List<Rule> rules, List<T> listOfObjects) throws RuleException;
    /**
     * Method to evaluate a Condition item on an Object
     *
     * @param conditionItem    Condition item to be evaluated
     * @param object   Object to evaluate conditionItem on
     * @return True if the condition item is satisfied, false otherwise
     */
    public Boolean evaluateConditionItem(ConditionItem conditionItem, T object, Resource resource) throws RuleException;

    /**
     * Method to perform Actions and Action Groups in a Trigger on an Object
     *
     * @param trigger        Trigger
     * @param object         Object to perform Actions in Trigger on
     * @param triggerHistory Trigger History to fill action results records for
     */
    public void performActions(Trigger trigger, T object, TriggerHistory triggerHistory) throws RuleException;

}
