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
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.router.MockInvoker;
import org.apache.dubbo.rpc.cluster.router.state.BitList;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffinityRouteTest {

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
                "dubbo://dubbo.apache.org/com.foo.BarService?region=hangzhou&env=normal");

        List<Invoker<String>> invokerList = providerUrls.stream()
                .map(url -> new MockInvoker<String>(URL.valueOf(url)))
                .collect(Collectors.toList());

        invokers = new BitList<>(invokerList);
    }

    @Test
    void testAffinityRoute() {
        AffinityRoute affinityRoute = new AffinityRoute();

        List<Invoker> invokers = buildInvokers();
        URL url = newUrl("consumer://127.0.0.1/com.foo.BarService?env=gray&region=beijing");
        Invocation invocation = new RPCInvocation("getComment", null, null);

        // Test base affinity router
        affinityRoute.setEnabled(true);
        affinityRoute.setMatcher(genMatcher("region"));
        affinityRoute.setRatio(20);

        InvokersFilters filters = new InvokersFilters().addMatcher("region=$region");

        List<Invoker> res = affinityRoute.route(invokers, url, invocation);
        List<Invoker> filtered = filters.filtrate(invokers, url, invocation);
        if (filtered.size() < providerUrls.length * (affinityRoute.getRatio() / 100.0)) {
            assertEquals(0, filtered.size());
        } else {
            assertEquals(filtered, res);
        }

        // Test bad ratio
        affinityRoute.setRatio(101);
        res = affinityRoute.route(invokers, url, invocation);
        filtered = filters.filtrate(invokers, url, invocation);
        assertTrue(res.isEmpty());

        // Test ratio false
        affinityRoute.setRatio(80);
        res = affinityRoute.route(invokers, url, invocation);
        filtered = filters.filtrate(invokers, url, invocation);
        assertEquals(filtered, res);

        // Test ignore affinity route
        affinityRoute.setMatcher(genMatcher("bad-key"));
        res = affinityRoute.route(invokers, url, invocation);
        filtered = filters.filtrate(invokers, url, invocation);
        assertEquals(filtered, res);
    }
}
