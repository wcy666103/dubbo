package org.apache.dubbo.rpc.cluster.router.condition.config.model;

import org.apache.dubbo.rpc.cluster.router.AbstractRouterRule;
import org.apache.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcher;

import java.util.List;
import java.util.Map;

import static org.apache.dubbo.rpc.cluster.Constants.CONDITIONS_KEY;

public class MultiDestConditionRouterRule extends AbstractRouterRule {

    private List<Map<String, Object>> conditions;

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
        Object conditions = map.get(CONDITIONS_KEY);
        if (conditions != null && Map.class.isAssignableFrom(conditions.getClass())) {
            //           这里边的每一条数据，应该都是按照 & 进行拆分之后然后分的
            //            进来的是consumer，也就是说需要按照
            //            multiDestConditionRouterRule.setConditions(((List<Object>) conditions).stream()
            //                    .map(String::valueOf)
            //                    .collect(Collectors.toList()));
        }

        //        确定此 Class 对象表示的类或接口是否与指定参数表示的类或接口相同，或者是该 Class 类或接口的超类或超接口。
        //        if (conditions != null && List.class.isAssignableFrom(conditions.getClass())) {
        //            //           这里边的每一条数据，应该都是按照 & 进行拆分之后然后分的
        //            //            进来的是consumer，也就是说需要按照
        //            multiDestConditionRouterRule.setConditions(
        //                    ((List<Object>) conditions).stream().map(String::valueOf).collect(Collectors.toList()));
        //        }
        //
        //        System.out.println("condition.config.model.ConditionRouterRule.parseFromMap = " + "==转换后==");
        //        //        method=sayHello => region=hangzhou
        //        conditionRouterRule.conditions.forEach(System.out::println);
        //
        //        return conditionRouterRule;
        return null;
    }

    public List<Map<String, Object>> getConditions() {
        return conditions;
    }

    public void setConditions(List<Map<String, Object>> conditions) {
        this.conditions = conditions;
    }
}
