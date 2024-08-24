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
package org.apache.dubbo.rpc.cluster.router.state;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.ConcurrentHashMapUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * If you want to provide a router implementation based on design of v2.7.0, please extend from this abstract class.
 * For 2.6.x style router, please implement and use RouterFactory directly.
 * 如果你想提供一个基于 v2.7.0 设计的路由器实现，请从这个抽象类扩展。 对于 2.6.x 风格的路由器，请直接实现并使用 RouterFactory
 */
public abstract class CacheableStateRouterFactory implements StateRouterFactory {
    // TODO reuse StateRouter for all routerChain

    //    缓存，存储 url
    private final ConcurrentMap<String, StateRouter> routerMap = new ConcurrentHashMap<>();

    @Override
    public <T> StateRouter<T> getRouter(Class<T> interfaceClass, URL url) {
        System.err.println("CacheableStateRouterFactory.getRouter==" + "==创建router==");
        //        url =
        // consumer://10.12.37.62/org.apache.dubbo.springboot.demo.DemoService?application=dubbo-springboot-demo-consumer&background=false&dubbo=2.0.2&executor-management-mode=isolation&file-cache=true&interface=org.apache.dubbo.springboot.demo.DemoService&methods=sayHello,sayHelloAsync&pid=13916&registry-type=service&release=3.2.13-SNAPSHOT&side=consumer&sticky=false&timestamp=1718270382357&unloadClusterRelated=false
        System.err.println("url = " + url);

        //        如果 key对应的value是null，则应用这个 func 新建一个value放进去
        //        对于serviceKey ： The format of return value is '{group}/{interfaceName}:{version}'
        return ConcurrentHashMapUtils.computeIfAbsent(
                routerMap, url.getServiceKey(), k -> createRouter(interfaceClass, url));
    }

    //    这是不同的实现类自己实现的方法

    protected abstract <T> StateRouter<T> createRouter(Class<T> interfaceClass, URL url);
}
