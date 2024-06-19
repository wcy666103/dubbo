package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import org.apache.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcher;

import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.rpc.cluster.Constants.DefaultRouteConditionSubSetWeight;

public class ConditionSubSet {
    private Map<String, ConditionMatcher> condition = new HashMap<>();
    private Integer subSetWeight;

    public ConditionSubSet() {
    }

    public ConditionSubSet(Map<String, ConditionMatcher> condition, Integer subSetWeight) {
        this.condition = condition;
        this.subSetWeight = subSetWeight;
        if (subSetWeight <= 0) {
            this.subSetWeight = DefaultRouteConditionSubSetWeight;
        }

    }

    // Getter方法
    public Map<String, ConditionMatcher> getCondition() {
        return condition;
    }

    // Setter方法
    public void setCondition(Map<String, ConditionMatcher> condition) {
        this.condition = condition;
    }

    // Getter方法
    public Integer getSubSetWeight() {
        return subSetWeight;
    }

    // Setter方法
    public void setSubSetWeight(int subSetWeight) {
        this.subSetWeight = subSetWeight;
    }

    @Override
    public String toString() {
        return "CondSet{" + "cond=" + condition + ", subSetWeight=" + subSetWeight + '}';
    }
}
