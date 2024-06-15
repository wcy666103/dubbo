package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import java.util.List;
import java.util.Map;

public class MultiDestCondition {
    private Integer priority;
    private Map<String,String> from;
    private boolean trafficDisable;
    private List<Map<String,String>> to;
    private boolean force;
    private Integer ratio;

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Map<String, String> getFrom() {
        return from;
    }

    public void setFrom(Map<String, String> from) {
        this.from = from;
    }

    public boolean isTrafficDisable() {
        return trafficDisable;
    }

    public void setTrafficDisable(boolean trafficDisable) {
        this.trafficDisable = trafficDisable;
    }

    public List<Map<String,String>> getTo() {
        return to;
    }

    public void setTo(List<Map<String,String>> to) {
        this.to = to;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public Integer getRatio() {
        return ratio;
    }

    public void setRatio(Integer ratio) {
        this.ratio = ratio;
    }

    @Override
    public String toString() {
        return "MultiDestCondition{" + "priority=" + priority + ", from=" + from + ", trafficDisable=" + trafficDisable
                + ", to=" + to + ", force=" + force + ", ratio=" + ratio + '}';
    }
}
