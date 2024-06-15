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
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.constants.LoggerCodeConstants;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.router.RouterSnapshotSwitcher;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.apache.dubbo.rpc.cluster.router.state.StateRouter;
import org.apache.dubbo.rpc.cluster.router.state.StateRouterFactory;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.apache.dubbo.rpc.model.ScopeModelUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.apache.dubbo.rpc.cluster.Constants.ROUTER_KEY;

/**
 * Router chain
 * 就是用来管理 SingleRouterChain的，SingleRouterChain的创建不仅需要传递 StateRouter还有Router列表
 *
 * 会在 org.apache.dubbo.registry.integration.DynamicDirectory 进行绑定
 */
public class RouterChain<T> {
    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(RouterChain.class);

//    类初始化的时候最少需要两个 SingleRouterChain参数，current会默认指向main
//    这个router chain说的是appscriptstaterouter\appstaterouter\providerappstaterouter\tagstaterouter\standardmeshrulerouter\mockinvokersselector，是大的方面的
    private volatile SingleRouterChain<T> mainChain;
    private volatile SingleRouterChain<T> backupChain;
    private volatile SingleRouterChain<T> currentChain;

//    启动的时候完成的，在这里进行构建 org.apache.dubbo.registry.integration.RegistryProtocol
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> RouterChain<T> buildChain(Class<T> interfaceClass, URL url) {
        SingleRouterChain<T> chain1 = buildSingleChain(interfaceClass, url);
        SingleRouterChain<T> chain2 = buildSingleChain(interfaceClass, url);
        return new RouterChain<>(new SingleRouterChain[] {chain1, chain2});
    }

    /**
     * 初始化的时候构建了这个，url是consumer的url
     * @param interfaceClass
     * @param url consumer://10.12.37.62/org.apache.dubbo.springboot.demo.DemoService?application=dubbo-springboot-demo-consumer&background=false&dubbo=2.0.2&executor-management-mode=isolation&file-cache=true&interface=org.apache.dubbo.springboot.demo.DemoService&methods=sayHello,sayHelloAsync&pid=21380&release=3.2.13-SNAPSHOT&side=consumer&sticky=false&timestamp=1718285838978&unloadClusterRelated=false
     * @return
     * @param <T>
     */
    public static <T> SingleRouterChain<T> buildSingleChain(Class<T> interfaceClass, URL url) {
        ModuleModel moduleModel = url.getOrDefaultModuleModel();

//  拿到对应的router规则对应的factory
        List<RouterFactory> extensionFactories =
                moduleModel.getExtensionLoader(RouterFactory.class).getActivateExtension(url, ROUTER_KEY);

//      根据factory拿到对应的 Router
        List<Router> routers = extensionFactories.stream()
                .map(factory -> factory.getRouter(url))
//                排序，这个是有优先级的！
                .sorted(Router::compareTo)
                .collect(Collectors.toList());

//        StateRouter也要拿出来 ， Router就是为了兼容用的
        List<StateRouter<T>> stateRouters =
//                根据class来获取所有扩展对象，就是所有激活了 实现factory的都会获取到，所以说应该是包含所有factory实例的
//                appscriptstaterouter\appstaterouter\providerappstaterouter\tagstaterouter\standardmeshrulerouter\mockinvokersselector，没有其他的了
                moduleModel.getExtensionLoader(StateRouterFactory.class).getActivateExtension(url, ROUTER_KEY).stream()
//                       interfaceClass =  org.apache.dubbo.springboot.demo.DemoService
                        .map(factory -> factory.getRouter(interfaceClass, url))
                        .collect(Collectors.toList());

        System.err.println("buildSingleChain:====" + "==构建singlechain==");
        System.err.println(url);

        boolean shouldFailFast = Boolean.parseBoolean(
                ConfigurationUtils.getProperty(moduleModel, Constants.SHOULD_FAIL_FAST_KEY, "true"));

        RouterSnapshotSwitcher routerSnapshotSwitcher =
                ScopeModelUtil.getFrameworkModel(moduleModel).getBeanFactory().getBean(RouterSnapshotSwitcher.class);

        return new SingleRouterChain<>(routers, stateRouters, shouldFailFast, routerSnapshotSwitcher);
    }

    public RouterChain(SingleRouterChain<T>[] chains) {
        if (chains.length != 2) {
            throw new IllegalArgumentException("chains' size should be 2.");
        }
        this.mainChain = chains[0];
        this.backupChain = chains[1];
        this.currentChain = this.mainChain;
    }

//    原子性：AtomicReference提供了一些原子方法，可以在多线程环境下原子性地操作引用对象。
//
//线程安全：由于AtomicReference的操作是原子性的，它可以确保在并发环境中正确处理引用对象的读取和更新，避免了竞态条件。
//
//引用对象的原子性操作：AtomicReference的原子方法如get、set、compareAndSet等，针对引用对象进行操作，而不是对象内部的属性或字段。
    private final AtomicReference<BitList<Invoker<T>>> notifyingInvokers = new AtomicReference<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ReadWriteLock getLock() {
        return lock;
    }

    public SingleRouterChain<T> getSingleChain(URL url, BitList<Invoker<T>> availableInvokers, Invocation invocation) {
        System.out.println("getSingleChain.url = " + url);
        // If current is in:
        // 1. `setInvokers` is in progress
        // 2. Most of the invocation should use backup chain => currentChain == backupChain
        // 3. Main chain has been update success => notifyingInvokers.get() != null
        //     If `availableInvokers` is created from origin invokers => use backup chain
        //     If `availableInvokers` is created from newly invokers  => use main chain
        BitList<Invoker<T>> notifying = notifyingInvokers.get();
        if (notifying != null //正在使用
                && currentChain == backupChain
                && availableInvokers.getOriginList() == notifying.getOriginList()) {
            return mainChain;
        }
        return currentChain;
    }

    /**
     * @deprecated use {@link RouterChain#getSingleChain(URL, BitList, Invocation)} and {@link SingleRouterChain#route(URL, BitList, Invocation)} instead
     * 被 getSingleChain 和 SingleRouterChain#route方法取代了
     */
    @Deprecated
    public List<Invoker<T>> route(URL url, BitList<Invoker<T>> availableInvokers, Invocation invocation) {
        return getSingleChain(url, availableInvokers, invocation).route(url, availableInvokers, invocation);
    }

    /**
     * Notify router chain of the initial addresses from registry at the first time.
     * Notify whenever addresses in registry change.
     *
     * 在第一时间通知路由器链来自注册表的初始地址。每当注册表中的地址发生更改时，都会通知。
     */
    public synchronized void setInvokers(BitList<Invoker<T>> invokers, Runnable switchAction) {
        try {
            // Lock to prevent directory continue list  本类中的一个属性，进行切换currentChain时候用
            lock.writeLock().lock();

            // Switch to back up chain. Will update main chain first.
            currentChain = backupChain;
        } finally {
            // Release lock to minimize the impact for each newly created invocations as much as possible.
            // Should not release lock until main chain update finished. Or this may cause long hang.
            lock.writeLock().unlock();
        }

        // Refresh main chain.
        // No one can request to use main chain. `currentChain` is backup chain. `route` method cannot access main
        // chain.
        try {
            // Lock main chain to wait all invocation end
            // To wait until no one is using main chain.
//            SingleRouterChain类中的一个属性，进行set chain的时候使用
            mainChain.getLock().writeLock().lock();

            // refresh
            mainChain.setInvokers(invokers);
        } catch (Throwable t) {
            logger.error(LoggerCodeConstants.INTERNAL_ERROR, "", "", "Error occurred when refreshing router chain.", t);
            throw t;
        } finally {
            // Unlock main chain
            mainChain.getLock().writeLock().unlock();
        }

        // Set the reference of newly invokers to temp variable.
        // Reason: The next step will switch the invokers reference in directory, so we should check the
        // `availableInvokers`
        //         argument when `route`. If the current invocation use newly invokers, we should use main chain to
        // route, and
        //         this can prevent use newly invokers to route backup chain, which can only route origin invokers now.
        notifyingInvokers.set(invokers);

        // Switch the invokers reference in directory.
        // Cannot switch before update main chain or after backup chain update success. Or that will cause state
        // inconsistent.
//        runable方法开始运行
        switchAction.run();

//        将当前版本切换，并且备份版本也进行更新
        try {
            // Lock to prevent directory continue list
            // The invokers reference in directory now should be the newly one and should always use the newly one once
            // lock released.
            lock.writeLock().lock();

            // Switch to main chain. Will update backup chain later.
            currentChain = mainChain;

            // Clean up temp variable.
            // `availableInvokers` check is useless now, because `route` method will no longer receive any
            // `availableInvokers` related
            // with the origin invokers. The getter of invokers reference in directory is locked now, and will return
            // newly invokers
            // once lock released.
            notifyingInvokers.set(null);
        } finally {
            // Release lock to minimize the impact for each newly created invocations as much as possible.
            // Will use newly invokers and main chain now.
            lock.writeLock().unlock();
        }

        // Refresh main chain.
        // No one can request to use main chain. `currentChain` is main chain. `route` method cannot access backup
        // chain.
        try {
            // Lock main chain to wait all invocation end
            backupChain.getLock().writeLock().lock();

            // refresh
            backupChain.setInvokers(invokers);
        } catch (Throwable t) {
            logger.error(LoggerCodeConstants.INTERNAL_ERROR, "", "", "Error occurred when refreshing router chain.", t);
            throw t;
        } finally {
            // Unlock backup chain
            backupChain.getLock().writeLock().unlock();
        }
    }

    public synchronized void destroy() {
        // 1. destroy another
        backupChain.destroy();

        // 2. switch
        lock.writeLock().lock();
        currentChain = backupChain;
        lock.writeLock().unlock();

        // 4. destroy
        mainChain.destroy();
    }

    public void addRouters(List<Router> routers) {
        mainChain.addRouters(routers);
        backupChain.addRouters(routers);
    }

    public SingleRouterChain<T> getCurrentChain() {
        return currentChain;
    }

    public List<Router> getRouters() {
        return currentChain.getRouters();
    }

    public StateRouter<T> getHeadStateRouter() {
        return currentChain.getHeadStateRouter();
    }

    @Deprecated
    public List<StateRouter<T>> getStateRouters() {
        return currentChain.getStateRouters();
    }
}
