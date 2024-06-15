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
package org.apache.dubbo.rpc.cluster.router.condition.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.router.AbstractRouterRule;
import org.apache.dubbo.rpc.cluster.router.RouterSnapshotNode;
import org.apache.dubbo.rpc.cluster.router.condition.ConditionStateRouter;
import org.apache.dubbo.rpc.cluster.router.condition.MultiDestConditionRouter;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.ConditionRouterRule;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.ConditionRuleParser;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.MultiDestConditionRouterRule;
import org.apache.dubbo.rpc.cluster.router.state.AbstractStateRouter;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.apache.dubbo.rpc.cluster.router.state.TailStateRouter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.CLUSTER_FAILED_RULE_PARSING;

/**
 * Abstract router which listens to dynamic configuration
 * <p>
 * 侦听动态配置的抽象路由器，实现类有 serviceStateRouter、appStateRouter、providerStateRouter
 */
public abstract class ListenableStateRouter<T> extends AbstractStateRouter<T> implements ConfigurationListener {
    public static final String NAME = "LISTENABLE_ROUTER";
    public static final String RULE_SUFFIX = ".condition-router";

    private static final ErrorTypeAwareLogger logger =
            LoggerFactory.getErrorTypeAwareLogger(ListenableStateRouter.class);
    //    保留一个这个对象，下边的 conditionRouters 就是根据这个对象生成的
    private volatile AbstractRouterRule routerRule;
    private volatile List<ConditionStateRouter<T>> conditionRouters = Collections.emptyList();

    //    for 3.1
    //    private volatile MultiDestConditionRouterRule multiRouterRule;
    //    对应的是 接口名称/app名称
    private volatile List<MultiDestConditionRouter<T>> multiDestConditionRouters = Collections.emptyList();
    private final String ruleKey;

    public ListenableStateRouter(URL url, String ruleKey) {
        super(url);
        this.setForce(false);
        this.init(ruleKey);
        this.ruleKey = ruleKey;
    }

    /**
     * 来消息之后是在这 还是这里是最主要的方法，test类就是这样，通过staterouter的实现类去构建event，然后被调用的就是这个process方法
     *
     * @param event config change event
     */
    @Override
    public synchronized void process(ConfigChangedEvent event) {
        if (logger.isInfoEnabled()) {
            logger.info("Notification of condition rule, change type is: " + event.getChangeType() + ", raw rule is:\n "
                    + event.getContent());
        }

        if (event.getChangeType()
                .equals(ConfigChangeType.DELETED)) {
            routerRule = null;
            conditionRouters = Collections.emptyList();

            conditionRouters = Collections.emptyList();
        } else {
            try {
                //                只有这个地方进行了parse的调用，将配置文件进行解析，生成对象
                routerRule = ConditionRuleParser.parse(event.getContent());
                //                routerRule就是yml转成的对象，里边的ConditionRouterRules都是string的集合，需要的是
                //                generateConditions将string的rul转换
                generateConditions(routerRule);
            } catch (Exception e) {
                logger.error(CLUSTER_FAILED_RULE_PARSING, "Failed to parse the raw condition rule", "",
                        "Failed to parse the raw condition rule and it will not take effect, please check "
                                + "if the condition rule matches with the template, the raw rule is:\n "
                                + event.getContent(), e);
            }
        }
    }

    @Override
    public BitList<Invoker<T>> doRoute(
            BitList<Invoker<T>> invokers,
            URL url,
            Invocation invocation,
            boolean needToPrintMessage,
            Holder<RouterSnapshotNode<T>> nodeHolder,
            Holder<String> messageHolder) throws RpcException {
        if (CollectionUtils.isEmpty(invokers) || conditionRouters.size() == 0) {
            if (needToPrintMessage) {
                messageHolder.set("Directly return. Reason: Invokers from previous router is empty or "
                        + "conditionRouters is empty.");
            }
            return invokers;
        }

        // We will check enabled status inside each router.
        StringBuilder resultMessage = null;
        if (needToPrintMessage) {
            resultMessage = new StringBuilder();
        }
        for (AbstractStateRouter<T> router : conditionRouters) {
            invokers = router.route(invokers, url, invocation, needToPrintMessage, nodeHolder);
            if (needToPrintMessage) {
                resultMessage.append(messageHolder.get());
            }
        }

        if (needToPrintMessage) {
            messageHolder.set(resultMessage.toString());
        }

        return invokers;
    }

    @Override
    public boolean isForce() {
        return (routerRule != null && routerRule.isForce());
    }

    private boolean isRuleRuntime() {
        return routerRule != null && routerRule.isValid() && routerRule.isRuntime();
    }

    /**
     * 将String类型的rul，转换成 ConditionStateRouter集合
     *
     * @param rule
     */
    private void generateConditions(AbstractRouterRule rule) {
        if (rule == null || !rule.isValid()) {return;}

        if (rule instanceof ConditionRouterRule) {
            //            这里边已经是有了 scope属性的
            this.conditionRouters = ((ConditionRouterRule) rule).getConditions()
                    .stream()
                    //                    将 ConditionRouterRule转换成 ConditionStateRouter
                    .map(condition -> new ConditionStateRouter<T>(getUrl(), condition, rule.isForce(),
                            rule.isEnabled()))
                    .collect(Collectors.toList());
//getUrl() = consumer://10.12.37.62/org.apache.dubbo.springboot.demo.DemoService?application=dubbo-springboot-demo-consumer&background=false&dubbo=2.0.2&executor-management-mode=isolation&file-cache=true&interface=org.apache.dubbo.springboot.demo.DemoService&methods=sayHello,sayHelloAsync&pid=30316&release=3.2.13-SNAPSHOT&side=consumer&sticky=false&timestamp=1718419480020&unloadClusterRelated=false
            System.err.println("getUrl() = " + getUrl());
            //            直接构建链？将下一个置为null
            for (ConditionStateRouter<T> conditionRouter : this.conditionRouters) {
                conditionRouter.setNextRouter(TailStateRouter.getInstance());
            }
        } else if (rule instanceof MultiDestConditionRouterRule) {
//            todo
        }

    }

    private synchronized void init(String ruleKey) {
        if (StringUtils.isEmpty(ruleKey)) {
            return;
        }
        //        org.apache.dubbo.springboot.demo.DemoService::.condition-router
        String routerKey = ruleKey + RULE_SUFFIX;
        this.getRuleRepository()
                .addListener(routerKey, this);
        String rule = this.getRuleRepository()
                .getRule(routerKey, DynamicConfiguration.DEFAULT_GROUP);
        if (StringUtils.isNotEmpty(rule)) {
            this.process(new ConfigChangedEvent(routerKey, DynamicConfiguration.DEFAULT_GROUP, rule));
        }
    }

    @Override
    public void stop() {
        this.getRuleRepository()
                .removeListener(ruleKey + RULE_SUFFIX, this);
    }
}
