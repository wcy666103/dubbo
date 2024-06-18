package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiDestCondition {
    private int priority;
    private Map<String,String> from = new HashMap<>();
    private boolean trafficDisable;
    private List<Map<String,String>> to = new ArrayList<>();
    private boolean force;
    private int ratio;

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
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

    public int getRatio() {
        return ratio;
    }

    public void setRatio(int ratio) {
        this.ratio = ratio;
    }

    @Override
    public String toString() {
        return "MultiDestCondition{" + "priority=" + priority + ", from=" + from + ", trafficDisable=" + trafficDisable
                + ", to=" + to + ", force=" + force + ", ratio=" + ratio + '}';
    }
}
