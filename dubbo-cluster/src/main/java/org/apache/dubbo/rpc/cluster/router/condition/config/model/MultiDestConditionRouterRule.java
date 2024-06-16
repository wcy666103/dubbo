package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.rpc.cluster.router.AbstractRouterRule;
import org.apache.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.rpc.cluster.Constants.CONDITIONS_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.DefaultRoutePriority;
import static org.apache.dubbo.rpc.cluster.Constants.DefaultRouteRatio;
import static org.apache.dubbo.rpc.cluster.Constants.FORCE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.PRIORITY_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RATIO_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.TRAFFIC_DISABLE_KEY;

public class MultiDestConditionRouterRule extends AbstractRouterRule {

    private List<MultiDestCondition> conditions;


    /**
     * @param map map = {configVersion=v3.1, scope=service, key=org.apache.dubbo.samples.CommentService, force=false,
     *            runtime=true, enabled=true, conditions=[{priority=10, from={match=region=$region&version=v1},
     *            trafficDisable=false, to=[{match=env=$env&region=shanghai, weight=100},
     *            {match=env=$env&region=beijing, weight=200}, {match=env=$env&region=hangzhou, weight=300}],
     *            force=false, ratio=20}, {priority=5, from={match=version=v1}, trafficDisable=true}, {priority=20,
     *            from={match=null}, to=[{match=region=$region, ratio=20}]}]}
     * @return
     */
    public static AbstractRouterRule parseFromMap(Map<String, Object> map) {

        MultiDestConditionRouterRule multiDestConditionRouterRule = new MultiDestConditionRouterRule();
        //        抽象类提供的方法
        multiDestConditionRouterRule.parseFromMap0(map);
        //        条件处理在这
        List<Map<String,String>> conditions = (List<Map<String,String>>)map.get(CONDITIONS_KEY);
        List<MultiDestCondition> multiDestConditions = new ArrayList<>();

        for (Map<String, String> condition : conditions) {
//            MultiDestCondition multiDestCondition = new MultiDestCondition();
//            multiDestCondition.setPriority(Integer.valueOf(condition.getOrDefault(PRIORITY_KEY,
//                    String.valueOf(DefaultRoutePriority))));
//            multiDestCondition.setForce(Boolean.valueOf(condition.getOrDefault(FORCE_KEY, String.valueOf(false))));
//            multiDestCondition.setRatio(Integer.valueOf(condition.getOrDefault(RATIO_KEY, String.valueOf(DefaultRouteRatio))));
//            multiDestCondition.setTrafficDisable(Boolean.valueOf(condition.getOrDefault(TRAFFIC_DISABLE_KEY,
//                    String.valueOf(false))));
            multiDestConditions.add((MultiDestCondition) JsonUtils.convertObject(condition, MultiDestCondition.class));
        }
        multiDestConditions.sort((a,b) -> a.getPriority() - b.getPriority());
        multiDestConditionRouterRule.setConditions(multiDestConditions);

        System.err.println("multiDestConditionRouterRule = " + multiDestConditionRouterRule);

        return multiDestConditionRouterRule;
    }

    public List<MultiDestCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<MultiDestCondition> conditions) {
        this.conditions = conditions;
    }
}
