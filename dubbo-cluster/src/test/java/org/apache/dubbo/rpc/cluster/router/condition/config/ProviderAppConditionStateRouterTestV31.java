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
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;
import org.apache.dubbo.rpc.cluster.router.MockInvoker;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.MultiDestCondition;
import org.apache.dubbo.rpc.cluster.router.state.BitList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_APPLICATION_KEY;

public class ProviderAppConditionStateRouterTestV31 {
    private static final String LOCAL_HOST = "127.0.0.1";
    private static final String RULE_SUFFIX = ".condition-router";

    private static GovernanceRuleRepository ruleRepository;
    private URL url = URL.valueOf("consumer://1.1.1.1/com.foo.BarService");
    private String rawRule =
            "configVersion: v3.1\n" +
                    "scope: service\n" +
                    "key: org.apache.dubbo.samples.CommentService\n" +
                    "force: false\n" +
                    "runtime: true\n" +
                    "enabled: true\n" +
                    "conditions:\n" +
                    "  - priority: 10\n" +
                    "    from:\n" +
                    "      match: region=$region&version=v1\n" +
                    "    trafficDisable: false\n" +
                    "    to:\n" +
                    "      - match: env=$env&region=shanghai\n" +
                    "        weight: 100\n" +
                    "      - match: env=$env&region=beijing\n" +
                    "        weight: 200\n" +
                    "      - match: env=$env&region=hangzhou\n" +
                    "        weight: 300\n" +
                    "    force: false\n" +
                    "    ratio: 20\n" +
                    "  - priority: 5\n" +
                    "    from:\n" +
                    "      match: version=v1\n" +
                    "    trafficDisable: true\n" +
                    "  - priority: 20\n" +
                    "    from:\n" + // 注意：这里假设没有具体的from匹配条件
                    "      match: \n" +
                    "    to:\n" +
                    "      - match: region=$region\n" +
                    "    ratio: 20\n";

    @Test
    public void testYml(){
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> map = yaml.load(rawRule);
        System.out.println("map = " + map);
        List<Map> conditions = (List<Map>) map.get("conditions");
        System.out.println("conditions = " + conditions);
        System.out.println("JsonUtils.convertObject(conditions.get(0), MultiDestCOndition.class) = "
                + JsonUtils.convertObject(conditions.get(0), MultiDestCondition.class));

        Object o = JsonUtils.convertObject(conditions.get(0), MultiDestCondition.class);
        System.out.println("((MultiDestCondition)o).getFrom().get(\"match\") = " + ((MultiDestCondition) o).getFrom()
                .get("match"));

    }
    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        ruleRepository = Mockito.mock(GovernanceRuleRepository.class);
    }

    @Test
    void test() {
        ProviderAppStateRouter<String> router = new ProviderAppStateRouter<>(url);
        router = Mockito.spy(router);
        Mockito.when(router.getRuleRepository()).thenReturn(ruleRepository);
        Mockito.when(ruleRepository.getRule("demo-provider" + RULE_SUFFIX, DynamicConfiguration.DEFAULT_GROUP))
                .thenReturn(rawRule);
        //        Mockito.when(ruleRepository.addListener()).thenReturn();

//        mock了几个invoker
        BitList<Invoker<String>> invokers = getInvokers();
        router.notify(invokers);

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");
        List<Invoker<String>> result = router.route(invokers.clone(), url, invocation, false, new Holder<>());
        Assertions.assertEquals(1, result.size());

        invocation.setMethodName("sayHi");
        result = router.route(invokers.clone(), url, invocation, false, new Holder<>());
        Assertions.assertEquals(3, result.size());
    }

    private BitList<Invoker<String>> getInvokers() {
        List<Invoker<String>> originInvokers = new ArrayList<Invoker<String>>();
        Invoker<String> invoker1 = new MockInvoker<String>(
                URL.valueOf("dubbo://10.20.3.3:20880/com.foo.BarService?" + REMOTE_APPLICATION_KEY + "=demo-provider"));
        Invoker<String> invoker2 = new MockInvoker<String>(URL.valueOf("dubbo://" + LOCAL_HOST
                + ":20880/com.foo.BarService?" + REMOTE_APPLICATION_KEY + "=demo-provider&region=hangzhou"));
        Invoker<String> invoker3 = new MockInvoker<String>(URL.valueOf(
                "dubbo://" + LOCAL_HOST + ":20880/com.foo.BarService?" + REMOTE_APPLICATION_KEY + "=demo-provider"));
        originInvokers.add(invoker1);
        originInvokers.add(invoker2);
        originInvokers.add(invoker3);
        BitList<Invoker<String>> invokers = new BitList<>(originInvokers);
        return invokers;
    }
}
