package org.apache.dubbo.rpc.cluster.router.condition;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.router.RouterSnapshotNode;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.MultiDestCondition;
import org.apache.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcher;
import org.apache.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcherFactory;
import org.apache.dubbo.rpc.cluster.router.state.AbstractStateRouter;
import org.apache.dubbo.rpc.cluster.router.state.BitList;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.dubbo.rpc.cluster.Constants.DefaultRouteConditionSubSetWeight;

public class MultiDestConditionRouter<T> extends AbstractStateRouter<T> {
    public static final String NAME = "multi_condition";

    protected static final Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");
    private Map<String, ConditionMatcher> whenCondition;
    private boolean trafficDisable;
    private List<CondSet> thenCondition;
    private int ratio;
    private int priority;
    private boolean force;
    protected List<ConditionMatcherFactory> matcherFactories;
    private boolean enabled;

    public MultiDestConditionRouter(
            URL url,
            Map<String, String> from,
            List<Map<String, String>> to,
            boolean force,
            boolean enabled,
            int ratio,
            int priority) {
        super(url);
        //        父类属性
        this.setForce(force);
        this.enabled = enabled;
        matcherFactories = moduleModel.getExtensionLoader(ConditionMatcherFactory.class)
                .getActivateExtensions();
        if (enabled) {
            this.init(from, to);
        }
    }

    public MultiDestConditionRouter(
            URL url, MultiDestCondition multiDestCondition, boolean force, boolean enabled) {
        super(url);
        this.setForce(force);
        this.enabled = enabled;
        covert(multiDestCondition, this);
        this.init(multiDestCondition.getFrom(), multiDestCondition.getTo());
    }

    //    进来的是  region=Hangzhou & env=gray
    public void init(Map<String, String> from, List<Map<String, String>> to) {
        if (from == null || from.size() == 0 || to == null || to.size() == 0) {
            throw new IllegalArgumentException("Illegal route rule!");
        }
                try {
                    String whenRule = from.get("match");
                    Map<String, ConditionMatcher> when =
                            StringUtils.isBlank(whenRule) || "true".equals(whenRule) ? new HashMap<>() : parseRule
                            (whenRule);
                    this.whenCondition = when;

                    for (Map<String, String> toMap : to) {
                        String thenRule = toMap.get("match");
                        Map<String, ConditionMatcher> then =
                                StringUtils.isBlank(thenRule) || "false".equals(thenRule) ? null : parseRule(thenRule);
                        // NOTE: It should be determined on the business level whether the `When condition` can be empty or not.

                        this.thenCondition.add(new CondSet(then, Integer.valueOf(toMap.getOrDefault("weight",
                                String.valueOf(DefaultRouteConditionSubSetWeight)))));
                    }

                } catch (ParseException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
    }

    private Map<String, ConditionMatcher> parseRule(String rule) throws ParseException {
        Map<String, ConditionMatcher> condition = new HashMap<>();
        if (StringUtils.isBlank(rule)) {
            return condition;
        }
        // Key-Value pair, stores both match and mismatch conditions
        ConditionMatcher matcherPair = null;
        // Multiple values
        Set<String> values = null;
        final Matcher matcher = ROUTE_PATTERN.matcher(rule);
        while (matcher.find()) { // Try to match one by one
            //            分隔符
            String separator = matcher.group(1);
            //            内容
            String content = matcher.group(2);
            // Start part of the condition expression.
            if (StringUtils.isEmpty(separator)) {
                matcherPair = this.getMatcher(content);
                condition.put(content, matcherPair);
            }
            //            下边就是根据不同的 分隔符，来确定不同的动作
            // The KV part of the condition expression
            else if ("&".equals(separator)) {
                if (condition.get(content) == null) {
                    matcherPair = this.getMatcher(content);
                    condition.put(content, matcherPair);
                } else {
                    matcherPair = condition.get(content);
                }
            }
            // The Value in the KV part.
            else if ("=".equals(separator)) {
                if (matcherPair == null) {
                    throw new ParseException(
                            "Illegal route rule \""
                                    + rule + "\", The error char '" + separator
                                    + "' at index " + matcher.start() + " before \""
                                    + content + "\".",
                            matcher.start());
                }

                values = matcherPair.getMatches();
                values.add(content);
            }
            // The Value in the KV part.
            else if ("!=".equals(separator)) {
                if (matcherPair == null) {
                    throw new ParseException(
                            "Illegal route rule \""
                                    + rule + "\", The error char '" + separator
                                    + "' at index " + matcher.start() + " before \""
                                    + content + "\".",
                            matcher.start());
                }

                values = matcherPair.getMismatches();
                values.add(content);
            }
            // The Value in the KV part, if Value have more than one items.
            else if (",".equals(separator)) { // Should be separated by ','
                if (values == null || values.isEmpty()) {
                    throw new ParseException(
                            "Illegal route rule \""
                                    + rule + "\", The error char '" + separator
                                    + "' at index " + matcher.start() + " before \""
                                    + content + "\".",
                            matcher.start());
                }
                values.add(content);
            } else {
                throw new ParseException(
                        "Illegal route rule \"" + rule
                                + "\", The error char '" + separator + "' at index "
                                + matcher.start() + " before \"" + content + "\".",
                        matcher.start());
            }
        }
        return condition;
    }

    /**
     * 返回对应的matcher
     * @param key
     * @return
     */
    private ConditionMatcher getMatcher(String key) {
        for (ConditionMatcherFactory factory : matcherFactories) {
            if (factory.shouldMatch(key)) {
                //                直接只用返回一个？？
                return factory.createMatcher(key, moduleModel);
            }
        }
        //        如果 没有就默认使用 para
        return moduleModel
                .getExtensionLoader(ConditionMatcherFactory.class)
                .getExtension("param")
                .createMatcher(key, moduleModel);
    }

    @Override
    protected BitList<Invoker<T>> doRoute(
            BitList<Invoker<T>> invokers,
            URL url,
            Invocation invocation,
            boolean needToPrintMessage,
            Holder<RouterSnapshotNode<T>> routerSnapshotNodeHolder,
            Holder<String> messageHolder) throws RpcException {
        return null;
    }

    public void covert(MultiDestCondition multiDestCondition, MultiDestConditionRouter multiDestConditionRouter) {
        multiDestConditionRouter.setTrafficDisable(multiDestCondition.isTrafficDisable());
        multiDestConditionRouter.setRatio(multiDestCondition.getRatio());
        multiDestConditionRouter.setPriority(multiDestCondition.getPriority());
    }

    // Setter 方法
    public void setWhenCondition(Map<String, ConditionMatcher> whenCondition) {
        this.whenCondition = whenCondition;
    }

    public void setTrafficDisable(boolean trafficDisable) {
        this.trafficDisable = trafficDisable;
    }

    public void setThenCondition(List<CondSet> thenCondition) {
        this.thenCondition = thenCondition;
    }

    public void setRatio(int ratio) {
        this.ratio = ratio;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    // Getter 方法
    public Map<String, ConditionMatcher> getWhenCondition() {
        return whenCondition;
    }

    public boolean isTrafficDisable() {
        return trafficDisable;
    }

    public List<CondSet> getThenCondition() {
        return thenCondition;
    }

    public int getRatio() {
        return ratio;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isForce() {
        return force;
    }

}

class CondSet {
    private Map<String, ConditionMatcher> cond;
    private int subSetWeight;

    // 构造函数（可选）
    public CondSet() {
        subSetWeight = DefaultRouteConditionSubSetWeight;
    }

    public CondSet(Map<String, ConditionMatcher> cond, int subSetWeight) {
        this.cond = cond;
        this.subSetWeight = subSetWeight;
        if (subSetWeight <= 0) {
            this.subSetWeight = DefaultRouteConditionSubSetWeight;
        }

    }

    // Getter方法
    public Map<String, ConditionMatcher> getCond() {
        return cond;
    }

    // Setter方法
    public void setCond(Map<String, ConditionMatcher> cond) {
        this.cond = cond;
    }

    // Getter方法
    public int getSubSetWeight() {
        return subSetWeight;
    }

    // Setter方法
    public void setSubSetWeight(int subSetWeight) {
        this.subSetWeight = subSetWeight;
    }

}
class Dest {
    int weight;
    List<Invoker> ivks;

    Dest(int weight, List<Invoker> ivks) {
        this.weight = weight;
        this.ivks = ivks;
    }
}
class DestSet {
    private final List<Dest> dests;
    private int weightSum;
    private final Random random;

    public DestSet() {
        this.dests = new ArrayList<>();
        this.weightSum = 0;
        this.random = new Random();
    }

    public void addDest(int weight, List<Invoker> ivks) {
        dests.add(new Dest(weight, ivks));
        weightSum += weight;
    }

    public List<Invoker> randDest() {
        if (dests.size() == 1) {
            return dests.get(0).ivks;
        }
        int sum = random.nextInt(weightSum);
        for (Dest d : dests) {
            sum -= d.weight;
            if (sum <= 0) {
                return d.ivks;
            }
        }
        return null; // 应该永远不会到达这里
    }


}

