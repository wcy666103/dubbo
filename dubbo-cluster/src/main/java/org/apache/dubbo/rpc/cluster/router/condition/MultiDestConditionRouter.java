package org.apache.dubbo.rpc.cluster.router.condition;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.router.RouterSnapshotNode;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.MultiDestCondition;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.MultiDestConditionRouterRule;
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

import static org.apache.dubbo.common.constants.LoggerCodeConstants.CLUSTER_CONDITIONAL_ROUTE_LIST_EMPTY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CLUSTER_FAILED_EXEC_CONDITION_ROUTER;
import static org.apache.dubbo.rpc.cluster.Constants.DefaultRouteConditionSubSetWeight;

public class MultiDestConditionRouter<T> extends AbstractStateRouter<T> {
    public static final String NAME = "multi_condition";

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(AbstractStateRouter.class);
    protected static final Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");
    private Map<String, ConditionMatcher> whenCondition;
    private boolean trafficDisable;
    private List<CondSet<T>> thenCondition;
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
            boolean trafficDisable,
            int ratio,
            int priority) {
        super(url);
        //        父类属性
        this.setForce(force);
        this.trafficDisable = trafficDisable;
        this.ratio = ratio;
        this.priority = priority;
        matcherFactories = moduleModel.getExtensionLoader(ConditionMatcherFactory.class)
                .getActivateExtensions();
        if (enabled) {
            this.init(from, to);
        }
    }

    public MultiDestConditionRouter(
            URL url, MultiDestCondition multiDestCondition,boolean enabled) {
        super(url);
//        this.setForce(force);
        this.enabled = enabled;
        matcherFactories =
                moduleModel.getExtensionLoader(ConditionMatcherFactory.class).getActivateExtensions();
        covert(multiDestCondition, this);
        this.init(multiDestCondition.getFrom(), multiDestCondition.getTo());
    }

    //    进来的是  region=Hangzhou & env=gray
    public void init(Map<String, String> from, List<Map<String, String>> to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Illegal route rule!");
        }
                try {
                    String whenRule = from.get("match");
                    Map<String, ConditionMatcher> when =
                            StringUtils.isBlank(whenRule) || "true".equals(whenRule) ? new HashMap<>() : parseRule
                            (whenRule);
                    this.whenCondition = when;

                    List<CondSet<T>> thenConditions = new ArrayList<>();
                    for (Map<String, String> toMap : to) {
                        String thenRule = toMap.get("match");
                        Map<String, ConditionMatcher> then =
                                StringUtils.isBlank(thenRule) || "false".equals(thenRule) ? null : parseRule(thenRule);
                        // NOTE: It should be determined on the business level whether the `When condition` can be empty or not.

                        thenConditions.add(new CondSet(then, Integer.valueOf(toMap.getOrDefault("weight",
                                String.valueOf(DefaultRouteConditionSubSetWeight)))));
                    }
                    this.thenCondition = thenConditions;
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

        if (!enabled) {
            if (needToPrintMessage) {
                messageHolder.set("Directly return. Reason: ConditionRouter disabled.");
            }
            return invokers;
        }

        if (CollectionUtils.isEmpty(invokers)) {
            if (needToPrintMessage) {
                //                以前路由器的调用器为空
                messageHolder.set("Directly return. Reason: Invokers from previous router is empty.");
            }
            return invokers;
        }

        try {
            if (!matchWhen(url, invocation)) {
                if (needToPrintMessage) {
                    //                    when 条件不满足
                    messageHolder.set("Directly return. Reason: WhenCondition not match.");
                }
                return invokers;
            }
            if (trafficDisable) {
                invocation.setAttachment("TrafficDisableKey", new Object());
                if (needToPrintMessage) {
                    //                    when 条件不满足
                    messageHolder.set("Directly return. Reason: TrafficDisableKey is true.");
                }
                return invokers;
            }
            //            when 匹配上了 但是 then是空
            if (thenCondition == null || thenCondition.size() == 0) {
                logger.warn(CLUSTER_CONDITIONAL_ROUTE_LIST_EMPTY, "condition state router thenCondition is empty", "",
                        "The current consumer in the service blocklist. consumer: " + NetUtils.getLocalHost()
                                + ", service: " + url.getServiceKey());
                if (needToPrintMessage) {
                    messageHolder.set("Empty return. Reason: ThenCondition is empty.");
                }
                return BitList.emptyList();
            }

            DestSet destinations = new DestSet();
            for (CondSet condition : thenCondition) {
                BitList<Invoker<T>> res = invokers.clone();

                for (Invoker invoker : invokers) {
                    if (!doMatch(invoker.getUrl(), url, null, condition.getCond(), false)) {
                        res.remove(invoker);
                    }
                }
                if (!res.isEmpty()) {
                    destinations.addDest(condition.getSubSetWeight() == null ? DefaultRouteConditionSubSetWeight : condition.getSubSetWeight(), res.clone());
                }
            }

            if (!destinations.getDests()
                    .isEmpty()) {
                BitList<Invoker<T>> res = destinations.randDest();
                if (res.size() * 100 / invokers.size() > ratio) {
                    return res;
                }else {
                    return BitList.emptyList();
                }
            }else {
                return invokers;
            }

        }  catch (Throwable t) {
            logger.error(
                    CLUSTER_FAILED_EXEC_CONDITION_ROUTER,
                    "execute condition state router exception",
                    "",
                    "Failed to execute condition router rule: " + getUrl() + ", invokers: " + invokers + ", cause: "
                            + t.getMessage(),
                    t);
        }

        if (needToPrintMessage) {
            messageHolder.set("Directly return. Reason: Error occurred ( or result is empty ).");
        }
        return invokers;
    }

    public void covert(MultiDestCondition multiDestCondition, MultiDestConditionRouter multiDestConditionRouter) {
        if (multiDestCondition == null) {
            throw new IllegalStateException("多地址条件为空");
        }
        multiDestConditionRouter.setTrafficDisable(multiDestCondition.isTrafficDisable());
        multiDestConditionRouter.setRatio(multiDestCondition.getRatio());
        multiDestConditionRouter.setPriority(multiDestCondition.getPriority());
        multiDestConditionRouter.setForce(multiDestCondition.isForce());
        multiDestConditionRouter.setTrafficDisable(multiDestCondition.isTrafficDisable());
    }

    boolean matchWhen(URL url, Invocation invocation) {
        if (CollectionUtils.isEmptyMap(whenCondition)) {
            return true;
        }

        return doMatch(url, null, invocation, whenCondition, true);
    }

    private boolean doMatch(
            URL url,
            URL param,
            Invocation invocation,
            Map<String, ConditionMatcher> conditions,
            boolean isWhenCondition) {
        Map<String, String> sample = url.toOriginalMap();
        for (Map.Entry<String, ConditionMatcher> entry : conditions.entrySet()) {
            ConditionMatcher matchPair = entry.getValue();

            if (!matchPair.isMatch(sample, param, invocation, isWhenCondition)) {
                return false;
            }
        }
        return true;
    }

    // Setter 方法
    public void setWhenCondition(Map<String, ConditionMatcher> whenCondition) {
        this.whenCondition = whenCondition;
    }

    public void setTrafficDisable(boolean trafficDisable) {
        this.trafficDisable = trafficDisable;
    }

    public void setThenCondition(List<CondSet<T>> thenCondition) {
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

//    public List<CondSet<T>> getThenCondition() {
//        return thenCondition;
//    }

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

class CondSet<T> {
    private Map<String, ConditionMatcher> cond;
    private Integer subSetWeight;

    // 构造函数（可选）
    public CondSet() {
        subSetWeight = DefaultRouteConditionSubSetWeight;
    }

    public CondSet(Map<String, ConditionMatcher> cond, Integer subSetWeight) {
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
    public Integer getSubSetWeight() {
        return subSetWeight;
    }

    // Setter方法
    public void setSubSetWeight(int subSetWeight) {
        this.subSetWeight = subSetWeight;
    }

    @Override
    public String toString() {
        return "CondSet{" + "cond=" + cond + ", subSetWeight=" + subSetWeight + '}';
    }
}
class Dest<T> {
    int weight;
    BitList<Invoker<T>> ivks;

    Dest(int weight, BitList<Invoker<T>> ivks) {
        this.weight = weight;
        this.ivks = ivks;
    }
}
class DestSet<T> {
    private final List<Dest<T>> dests;
    private int weightSum;
    private final Random random;

    public DestSet() {
        this.dests = new ArrayList<>();
        this.weightSum = 0;
        this.random = new Random();
    }

    public void addDest(int weight, BitList<Invoker<T>> ivks) {
        dests.add(new Dest(weight, ivks));
        weightSum += weight;
    }

    public BitList<Invoker<T>> randDest() {
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

    public List<Dest<T>> getDests() {
        return dests;
    }

    public int getWeightSum() {
        return weightSum;
    }

    public void setWeightSum(int weightSum) {
        this.weightSum = weightSum;
    }

    public Random getRandom() {
        return random;
    }
}

