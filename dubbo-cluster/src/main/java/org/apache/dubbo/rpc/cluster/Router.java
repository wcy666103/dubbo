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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.router.RouterResult;

import java.util.List;

/**
 * 路由器的抽象类，包括优先级、route方法，等，感觉跟StateRouter接口差不多
 * 不知道区别在哪
 * 该接口定义的比较早，可能是为了兼容？？
 * Router. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Routing">Routing</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory, boolean)
 * @see org.apache.dubbo.rpc.cluster.Directory#list(Invocation)
 */
public interface Router extends Comparable<Router> {

//    默认优先级？
    int DEFAULT_PRIORITY = Integer.MAX_VALUE;

    /**
     * Get the router url.
     *
     * @return url
     */
    URL getUrl();

    /**
     * Filter invokers with current routing rule and only return the invokers that comply with the rule.
     * 使用当前路由规则筛选调用者，只返回符合规则的调用者。
     * @param invokers   invoker list
     * @param url        refer url
     * @param invocation invocation
     * @return routed invokers
     * @throws RpcException
     */
    @Deprecated
    default <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        return null;
    }

    /**
     * ** This method can return the state of whether routerChain needed to continue route. **
     * Filter invokers with current routing rule and only return the invokers that comply with the rule.
     * ** 该方法可以返回路由器链是否需要继续路由的状态。** 使用当前路由规则筛选调用程序，并仅返回符合规则的调用程序
     *
     * @param invokers   invoker list
     * @param url        refer url
     * @param invocation invocation
     * @param needToPrintMessage whether to print router state. Such as `use router branch a`.
     * @return state with route result
     * @throws RpcException
     */
    default <T> RouterResult<Invoker<T>> route(
            List<Invoker<T>> invokers, URL url, Invocation invocation, boolean needToPrintMessage) throws RpcException {
        return new RouterResult<>(route(invokers, url, invocation));
    }

    /**
     * Notify the router the invoker list. Invoker list may change from time to time. This method gives the router a
     * chance to prepare before {@link Router#route(List, URL, Invocation)} gets called.
     *
     * 通知路由器调用程序列表。调用程序列表可能会不时更改。此方法使路由器有机会在被调用之前 route(List, URL, Invocation) 进行准备。
     * 就是当 Directory 对象有更新的时候哦，会调用这个 notify方法
     * @param invokers invoker list
     * @param <T>      invoker's type
     */
    default <T> void notify(List<Invoker<T>> invokers) {}

    /**
     * To decide whether this router need to execute every time an RPC comes or should only execute when addresses or
     * rule change.
     *
     * 确定此路由器是否需要在每次 RPC 到来时执行，还是仅在地址或规则更改时执行。
     *
     * @return true if the router need to execute every time.
     */
    boolean isRuntime();

    /**
     * To decide whether this router should take effect when none of the invoker can match the router rule, which
     * means the {@link #route(List, URL, Invocation)} would be empty. Most of time, most router implementation would
     * default this value to false.
     *
     * 决定当调用程序都不能匹配路由器规则时，此路由器是否应生效，这意味着该 route(List, URL, Invocation) 规则将为空。大多数情况下，大多数路由器实现会将此值默认为 false。
     * 返回：
     * 如果调用程序均不与当前路由器匹配，则执行 true
     * @return true to execute if none of invokers matches the current router
     */
    boolean isForce();

    /**
     * Router's priority, used to sort routers.
     * 路由器的优先级，用于对路由器进行排序
     *
     * @return router's priority
     */
    int getPriority();

    default void stop() {
        // do nothing by default
    }

    /**
     * 根据定义的  priority 对router进行排序
     * @param o the object to be compared.
     * @return
     */
    @Override
    default int compareTo(Router o) {
        if (o == null) {
            throw new IllegalArgumentException();
        }
        return Integer.compare(this.getPriority(), o.getPriority());
    }
}
