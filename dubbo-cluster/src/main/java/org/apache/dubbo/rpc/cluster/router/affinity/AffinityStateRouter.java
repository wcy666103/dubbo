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
package org.apache.dubbo.rpc.cluster.router.affinity;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.NetUtils;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CLUSTER_CONDITIONAL_ROUTE_LIST_EMPTY;
import static org.apache.dubbo.common.constants.LoggerCodeConstants.CLUSTER_FAILED_EXEC_CONDITION_ROUTER;
import static org.apache.dubbo.rpc.cluster.Constants.AFFINITY_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.DefaultAffinityRatio;
import static org.apache.dubbo.rpc.cluster.Constants.RATIO_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RULE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RUNTIME_KEY;

/**
 * 继承自 AbstractStateRouter，其中的matchWhen和matchThen是自己根据自己特点加入的方法
 * <p>
 * 这里边有condition对应的各种 matcherFactories，包括 Attachment、argument、urlparam parseRule是关键方法，根据string来解析使用哪个matcher
 * <p>
 * Condition Router directs traffics matching the 'when condition' to a particular address subset determined by the
 * 'then condition'. One typical condition rule is like below, with 1. the 'when condition' on the left side of '=>'
 * contains matching rule like 'method=sayHello' and 'method=sayHi' 2. the 'then condition' on the right side of '=>'
 * contains matching rule like 'region=hangzhou' and 'address=*:20881'
 * <p>
 * By default, condition router support matching rules like 'foo=bar', 'foo=bar*', 'arguments[0]=bar',
 * 'attachments[foo]=bar', 'attachments[foo]=1~100', etc. It's also very easy to add customized matching rules by
 * extending {@link ConditionMatcherFactory} and {@link ValuePattern}
 * <p>
 * --- scope: service force: true runtime: true enabled: true key: org.apache.dubbo.samples.governance.api.DemoService
 * conditions: - method=sayHello => region=hangzhou - method=sayHi => address=*:20881 ...
 * 条件路由器将与“当条件”匹配的流量定向到由“然后条件”确定的特定地址子集。 一个典型的条件规则如下所示，其中 1.'=>' 左侧的 'when condition' 包含匹配规则，如 'method=sayHello' 和
 * 'method=sayHi' 2.“=>”右侧的“then condition”包含匹配规则，如“region=hangzhou”和“address=*：20881” 默认情况下，条件路由器支持匹配规则，如
 * 'foo=bar'、'foo=bar*'、'arguments[0]=bar'、'attachments[foo]=bar'、'attachments[foo]=1~100' 等。 通过扩展
 * ConditionMatcherFactory 和 ValuePattern ---范围： 服务力： true 运行时： true 启用： true key：
 * org.apache.dubbo.samples.governance.api.Demo服务条件： - method=sayHello => region=hangzhou - method=sayHi =>
 * address=*：20881 ...
 */
public class AffinityStateRouter<T> extends AbstractStateRouter<T> {
    public static final String NAME = "affinity";

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(AbstractStateRouter.class);

    protected String affinityKey;
    protected Double ratio;
    protected ConditionMatcher matchMatcher;
    protected List<ConditionMatcherFactory> matcherFactories;

    private final boolean enabled;

    public AffinityStateRouter(URL url) {
        super(url);
        this.enabled = url.getParameter(ENABLED_KEY, true);
        this.affinityKey = url.getParameter(AFFINITY_KEY, "");
        this.ratio = url.getParameter(RATIO_KEY, DefaultAffinityRatio);
        this.matcherFactories = moduleModel.getExtensionLoader(ConditionMatcherFactory.class)
                .getActivateExtensions();
        if (this.enabled) {
            this.init(affinityKey);
        }
    }

    public AffinityStateRouter(URL url, String affinityKey, Double ratio, boolean enabled) {
        super(url);
        this.enabled = enabled;
        this.affinityKey = affinityKey;
        this.ratio = ratio;
        matcherFactories = moduleModel.getExtensionLoader(ConditionMatcherFactory.class)
                .getActivateExtensions();
        // 是否启用 亲和性
        if (this.enabled) {
            this.init(affinityKey);
        }
    }

    public void init(String rule) {
        try {
            if (rule == null || rule.trim()
                    .isEmpty()) {
                throw new IllegalArgumentException("Illegal affinity rule!");
            }
            this.matchMatcher = parseRule(affinityKey);
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private ConditionMatcher parseRule(String rule) throws ParseException {
        ConditionMatcher matcher = getMatcher(rule);
        // Multiple values
        Set<String> values = matcher.getMatches();
        values.add(getUrl().getParameter(rule));
        return matcher;
    }

    @Override
    protected BitList<Invoker<T>> doRoute(
            BitList<Invoker<T>> invokers,
            URL url,
            // 本url
            Invocation invocation,
            // RpcInvocation [methodName=sayHello, parameterTypes=[class java.lang.String]]
            boolean needToPrintMessage,
            Holder<RouterSnapshotNode<T>> nodeHolder,
            Holder<String> messageHolder) throws RpcException {
        if (!enabled) {
            if (needToPrintMessage) {
                messageHolder.set("Directly return. Reason: AffinityRouter disabled.");
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
            BitList<Invoker<T>> result = invokers.clone();
            result.removeIf(invoker -> !matchInvoker(invoker.getUrl(),url));

            if (result.size() / (double) invokers.size() >= ratio / (double) 100) {
                if (needToPrintMessage) {
                    messageHolder.set("Match return.");
                }
                return result;
                //                如果开启了 force 需要打印 + 携带一些信息
            } else {
                logger.warn(CLUSTER_CONDITIONAL_ROUTE_LIST_EMPTY,
                        "execute affinity state router result is less than defined" + this.ratio, "",
                        "The affinity result is ignored. consumer: " + NetUtils.getLocalHost()
                                + ", service: " + url.getServiceKey() + ", router: "
                                + url.getParameterAndDecoded(RULE_KEY));
                if (needToPrintMessage) {
                    messageHolder.set("Directly return. Reason: Affinity state router result is less than defined.");
                }
                return invokers;
            }
        } catch (Throwable t) {
            logger.error(CLUSTER_FAILED_EXEC_CONDITION_ROUTER, "execute affinity state router exception", "",
                    "Failed to execute affinity router rule: " + getUrl() + ", invokers: " + invokers + ", cause: "
                            + t.getMessage(), t);
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
        return this.getUrl()
                .getParameter(RUNTIME_KEY, false);
    }

    /**
     * 返回对应的matcher
     *
     * @param key
     * @return
     */
    private ConditionMatcher getMatcher(String key) {
        //        如果 没有就默认使用 para
        return moduleModel.getExtensionLoader(ConditionMatcherFactory.class)
                .getExtension("param")
                .createMatcher(key, moduleModel);
    }

    private boolean matchInvoker(URL url, URL param) {
        return doMatch(url, param, null, matchMatcher);
    }

    private boolean doMatch(URL url, URL param, Invocation invocation, ConditionMatcher matcher) {
        Map<String, String> sample = url.toOriginalMap();
        if (!matcher.isMatch(sample, param, invocation, false)) {
            return false;
        }
        return true;
    }
}
