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
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;
import org.apache.dubbo.rpc.cluster.router.MockInvoker;
import org.apache.dubbo.rpc.cluster.router.condition.ConditionStateRouterFactory;
import org.apache.dubbo.rpc.cluster.router.condition.config.model.MultiDestCondition;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.junit.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_APPLICATION_KEY;

public class ProviderAppConditionStateRouterTestV31 {

    private static BitList<Invoker<String>> invokers;
    @BeforeAll
    public static void setUp() {

        List<String> providerUrls = Arrays.asList(
                "dubbo://127.0.0.1/com.foo.BarService",
                "dubbo://127.0.0.1/com.foo.BarService",
                "dubbo://127.0.0.1/com.foo.BarService?env=normal",
                "dubbo://127.0.0.1/com.foo.BarService?env=normal",
                "dubbo://127.0.0.1/com.foo.BarService?env=normal",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=gray",
                "dubbo://127.0.0.1/com.foo.BarService?region=beijing&env=normal",
                "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou",
                "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou",
                "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=gray",
                "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=gray",
                "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=normal",
                "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=normal",
                "dubbo://127.0.0.1/com.foo.BarService?region=hangzhou&env=normal",
                "dubbo://dubbo.apache.org/com.foo.BarService",
                "dubbo://dubbo.apache.org/com.foo.BarService",
                "dubbo://dubbo.apache.org/com.foo.BarService?env=normal",
                "dubbo://dubbo.apache.org/com.foo.BarService?env=normal",
                "dubbo://dubbo.apache.org/com.foo.BarService?env=normal",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=gray",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=beijing&env=normal",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=gray",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=gray",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=normal",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=normal",
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=normal"
        );

        List<Invoker<String>> invokerList = providerUrls.stream()
                .map(url -> new MockInvoker<String>(URL.valueOf(url)))
                .collect(Collectors.toList());

        invokers = new BitList<>(invokerList);

    }

    @Test
    public void testConditionRoutePriority() throws Exception {
//        String config = "configVersion: v3.1\n" +
//                "scope: service\n" +
//                "force: false\n" +
//                "runtime: true\n" +
//                "enabled: true\n" +
//                "key: shop\n" +
//                "conditions:\n" +
//                "  - from:\n" +
//                "      match:\n" +
//                "    to:\n" +
//                "      - match: region=beijing & version=v1\n" +
//                "      - match: region=beijing & version=v2\n" +
//                "        weight: 200\n" +
//                "      - match: region=beijing & version=v3\n" +
//                "        weight: 300\n" +
//                "    force: false\n" +
//                "    ratio: 20\n" +
//                "    priority: 20\n" +
//                "  - from:\n" +
//                "      match: region=beijing & version=v1\n" +
//                "    to:\n" +
//                "      - match: env=gray & region=beijing\n" +
//                "    force: false\n" +
//                "    priority: 100\n";
        String config = "configVersion: v3.1\n" +
                "scope: service\n" +
                "force: false\n" +
                "runtime: true\n" +
                "enabled: true\n" +
                "key: shop\n" +
                "conditions:\n" +
                "  - from:\n" +
                "      match:\n" +
                "    to:\n" +
                "      - match: region=$region & version=v1\n" +
                "      - match: region=$region & version=v2\n" +
                "        weight: 200\n" +
                "      - match: region=$region & version=v3\n" +
                "        weight: 300\n" +
                "    force: false\n" +
                "    ratio: 20\n" +
                "    priority: 20\n" +
                "  - from:\n" +
                "      match: region=beijing & version=v1\n" +
                "    to:\n" +
                "      - match: env=$env & region=beijing\n" +
                "    force: false\n" +
                "    priority: 100\n";

        ServiceStateRouter<String> router = new ServiceStateRouter<>(URL.valueOf("consumer://127.0.0.1/com.foo.BarService?env=gray&region=beijing&version=v1"));
        router.process(new ConfigChangedEvent("com.foo.BarService","",config, ConfigChangeType.ADDED));

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("getComment");

        BitList<Invoker<String>> result = router.route(invokers.clone(), URL.valueOf("consumer://127.0.0.1/com.foo.BarService?env=gray&region=beijing&version=v1"), invocation, false, new Holder<>());

        int expectedLen = 0;
        for (Invoker<?> invoker : invokers) {
            if ("beijing".equals(invoker.getUrl().getParameter("region")) && "gray".equals(invoker.getUrl().getParameter("env"))) {
                expectedLen++;
            }
        }

        if (invokers.size() * 100 / expectedLen <= 20) {
            expectedLen = 0;
        }

        System.out.println("expectedLen = " + expectedLen); // expectedLen = 8

        Assertions.assertEquals(expectedLen, result.size());
    }

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

        RpcInvocation invocation = new RpcInvocation();
        invocation.setMethodName("sayHello");

        ServiceStateRouter<String> router = new ServiceStateRouter<>(url);
        router.process(new ConfigChangedEvent("","",rawRule, ConfigChangeType.ADDED));
        BitList<Invoker<String>> route = router.route(invokers, url, invocation, false, new Holder<>());
        System.out.println("route = " + route);


    }

}
