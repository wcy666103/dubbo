/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.apache.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcher;
import org.apache.dubbo.rpc.cluster.router.condition.matcher.ConditionMatcherFactory;
import org.apache.dubbo.rpc.cluster.router.condition.matcher.pattern.ValuePattern;
import org.apache.dubbo.rpc.cluster.router.state.AbstractStateRouter;
import org.apache.dubbo.rpc.cluster.router.state.BitList;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CLUSTER_CONDITIONAL_ROUTE_LIST_EMPTY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CLUSTER_FAILED_EXEC_CONDITION_ROUTER;
import static org.apache.dubbo.rpc.cluster.Constants.FORCE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RULE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RUNTIME_KEY;

/**
 * 继承自 AbstractStateRouter，其中的matchWhen和matchThen是自己根据自己特点加入的方法
 *
 * 这里边有condition对应的各种 matcherFactories，包括 Attachment、argument、urlparam
 * parseRule是关键方法，根据string来解析使用哪个matcher
 *
 * Condition Router directs traffics matching the 'when condition' to a particular address subset determined by the 'then condition'.
 * One typical condition rule is like below, with
 * 1. the 'when condition' on the left side of '=>' contains matching rule like 'method=sayHello' and 'method=sayHi'
 * 2. the 'then condition' on the right side of '=>' contains matching rule like 'region=hangzhou' and 'address=*:20881'
 * <p>
 * By default, condition router support matching rules like 'foo=bar', 'foo=bar*', 'arguments[0]=bar', 'attachments[foo]=bar', 'attachments[foo]=1~100', etc.
 * It's also very easy to add customized matching rules by extending {@link ConditionMatcherFactory}
 * and {@link ValuePattern}
 * <p>
 * ---
 * scope: service
 * force: true
 * runtime: true
 * enabled: true
 * key: org.apache.dubbo.samples.governance.api.DemoService
 * conditions:
 * - method=sayHello => region=hangzhou
 * - method=sayHi => address=*:20881
 * ...
 * 条件路由器将与“当条件”匹配的流量定向到由“然后条件”确定的特定地址子集。
 * 一个典型的条件规则如下所示，其中
 * 1.'=>' 左侧的 'when condition' 包含匹配规则，如 'method=sayHello' 和 'method=sayHi'
 * 2.“=>”右侧的“then condition”包含匹配规则，如“region=hangzhou”和“address=*：20881”
 * 默认情况下，条件路由器支持匹配规则，如 'foo=bar'、'foo=bar*'、'arguments[0]=bar'、'attachments[foo]=bar'、'attachments[foo]=1~100' 等。
 * 通过扩展 ConditionMatcherFactory 和 ValuePattern
 * ---范围： 服务力： true 运行时： true 启用： true key： org.apache.dubbo.samples.governance.api.Demo服务条件： - method=sayHello => region=hangzhou - method=sayHi => address=*：20881 ...
 */
public class ConditionStateRouter<T> extends AbstractStateRouter<T> {
    public static final String NAME = "condition";

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(AbstractStateRouter.class);
    //    综合起来，该正则表达式匹配的是以`&`、`!`、`=`或`,`作为分隔符的字符串，例如：`test&abc,def!123`。
    protected static final Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");
    protected Map<String, ConditionMatcher> whenCondition;
    protected Map<String, ConditionMatcher> thenCondition;
    protected List<ConditionMatcherFactory> matcherFactories;

    private final boolean enabled;

    public ConditionStateRouter(URL url, String rule, boolean force, boolean enabled) {
        super(url);
        //        父类属性
        this.setForce(force);
        this.enabled = enabled;
        matcherFactories =
                //                看到了，根据class进行取的
                moduleModel.getExtensionLoader(ConditionMatcherFactory.class).getActivateExtensions();
        if (enabled) {
            this.init(rule);
        }
    }

    /**
     * 这个应该是支持的是 2.7.x以下版本用这样url来构建的
     * @param url
     */
    public ConditionStateRouter(URL url) {
        super(url);
        this.setUrl(url);
        //        默认是false
        this.setForce(url.getParameter(FORCE_KEY, false));
        matcherFactories =
                moduleModel.getExtensionLoader(ConditionMatcherFactory.class).getActivateExtensions();
        //        默认启用
        this.enabled = url.getParameter(ENABLED_KEY, true);
        if (enabled) {
            //            应该加个this.init
            init(url.getParameterAndDecoded(RULE_KEY));
        }
    }

    //    进来的是 method=getComment => region=Hangzhou & env=gray
    public void init(String rule) {
        try {
            if (rule == null || rule.trim().length() == 0) {
                throw new IllegalArgumentException("Illegal route rule!");
            }
            rule = rule.replace("consumer.", "").replace("provider.", "");
            //            根据 => 的索引位置来分 when和then
            int i = rule.indexOf("=>");
            String whenRule = i < 0 ? null : rule.substring(0, i).trim();
            String thenRule = i < 0 ? rule.trim() : rule.substring(i + 2).trim();
            Map<String, ConditionMatcher> when =
                    StringUtils.isBlank(whenRule) || "true".equals(whenRule) ? new HashMap<>() : parseRule(whenRule);
            Map<String, ConditionMatcher> then =
                    StringUtils.isBlank(thenRule) || "false".equals(thenRule) ? null : parseRule(thenRule);
            // NOTE: It should be determined on the business level whether the `When condition` can be empty or not.
            this.whenCondition = when;
            this.thenCondition = then;
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 关键方法，根据 string 来解析 matcher使用哪个
     * rule： 第一次进来：method=getComment  第二次进来：region=Hangzhou & env=gray
     * @param rule method=getComment
     * @return  method:ConditionMatcher @{see ConditionMatcher}
     * @throws ParseException
     */
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
            // Start part of the condition expression. // 如果分隔符为空，说明这是条件表达式的开始部分
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

    @Override
    protected BitList<Invoker<T>> doRoute(
            BitList<Invoker<T>> invokers,
            URL url, // 本url
            Invocation invocation, // RpcInvocation [methodName=sayHello, parameterTypes=[class java.lang.String]]
            boolean needToPrintMessage,
            Holder<RouterSnapshotNode<T>> nodeHolder,
            Holder<String> messageHolder)
            throws RpcException {
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
            //            when 匹配上了 但是 then是空
            if (thenCondition == null) {
                logger.warn(
                        CLUSTER_CONDITIONAL_ROUTE_LIST_EMPTY,
                        "condition state router thenCondition is empty",
                        "",
                        "The current consumer in the service blocklist. consumer: " + NetUtils.getLocalHost()
                                + ", service: " + url.getServiceKey());
                if (needToPrintMessage) {
                    messageHolder.set("Empty return. Reason: ThenCondition is empty.");
                }
                return BitList.emptyList();
            }
            BitList<Invoker<T>> result = invokers.clone();
            result.removeIf(invoker -> !matchThen(invoker.getUrl(), url));

            //            返回 符合匹配的结果
            if (!result.isEmpty()) {
                if (needToPrintMessage) {
                    messageHolder.set("Match return.");
                }
                return result;
                //                如果开启了 force 需要打印 + 携带一些信息
            } else if (this.isForce()) {
                logger.warn(
                        CLUSTER_CONDITIONAL_ROUTE_LIST_EMPTY,
                        "execute condition state router result list is empty. and force=true",
                        "",
                        "The route result is empty and force execute. consumer: " + NetUtils.getLocalHost()
                                + ", service: " + url.getServiceKey() + ", router: "
                                + url.getParameterAndDecoded(RULE_KEY));
                if (needToPrintMessage) {
                    messageHolder.set("Empty return. Reason: Empty result from condition and condition is force.");
                }
                return result;
            }
        } catch (Throwable t) {
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

    @Override
    public boolean isRuntime() {
        //        对于以前定义的路由器，我们总是返回true，也就是说，旧的路由器不再支持缓存了。
        // We always return true for previously defined Router, that is, old Router doesn't support cache anymore.
        //        return true;
        return this.getUrl().getParameter(RUNTIME_KEY, false);
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

    boolean matchWhen(URL url, Invocation invocation) {
        if (CollectionUtils.isEmptyMap(whenCondition)) {
            return true;
        }

        return doMatch(url, null, invocation, whenCondition, true);
    }

    private boolean matchThen(URL url, URL param) {
        if (CollectionUtils.isEmptyMap(thenCondition)) {
            return false;
        }

        return doMatch(url, param, null, thenCondition, false);
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
}
