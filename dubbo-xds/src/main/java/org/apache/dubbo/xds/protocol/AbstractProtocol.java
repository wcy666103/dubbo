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
package org.apache.dubbo.xds.protocol;

import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashMapUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.xds.AdsObserver;
import org.apache.dubbo.xds.XdsListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;

// 所有 xDS的静态实现
public abstract class AbstractProtocol<T> implements XdsProtocol, XdsListener {

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(AbstractProtocol.class);

    protected AdsObserver adsObserver;

//    io.envoyproxy.envoy.config.core.v3.Node
    protected final Node node;

    private final int checkInterval;

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    protected final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();

    protected final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    protected Set<String> observeResourcesName;

    public static final String emptyResourceName = "emptyResourcesName";
    private final ReentrantLock resourceLock = new ReentrantLock();

    protected Map<Set<String>, List<Consumer<Map<String, T>>>> consumerObserveMap = new ConcurrentHashMap<>();

    public Map<Set<String>, List<Consumer<Map<String, T>>>> getConsumerObserveMap() {
        return consumerObserveMap;
    }

    protected Map<String, T> resourcesMap = new ConcurrentHashMap<>();

    protected List<XdsResourceListener<T>> resourceListeners = new CopyOnWriteArrayList<>();

    protected ApplicationModel applicationModel;

    public AbstractProtocol(AdsObserver adsObserver, Node node, int checkInterval, ApplicationModel applicationModel) {
        this.adsObserver = adsObserver;
        this.node = node;
        this.checkInterval = checkInterval;
        this.applicationModel = applicationModel;
        adsObserver.addListener(this);
    }

    public void registerListen(XdsResourceListener<T> listener) {
        this.resourceListeners.add(listener);
    }

    /**
     * Abstract method to obtain Type-URL from sub-class
     *
     * @return Type-URL of xDS
     */
    public abstract String getTypeUrl();

    public boolean isCacheExistResource(Set<String> resourceNames) {
        for (String resourceName : resourceNames) {
            if ("".equals(resourceName)) {
                continue;
            }
            if (!resourcesMap.containsKey(resourceName)) {
                return false;
            }
        }
        return true;
    }

    public T getCacheResource(String resourceName) {
        if (resourceName == null || resourceName.length() == 0) {
            return null;
        }
        return resourcesMap.get(resourceName);
    }

    @Override
    public void subscribeResource(Set<String> resourceNames) {
        resourceNames = resourceNames == null ? Collections.emptySet() : resourceNames;

        if (!resourceNames.isEmpty() && isCacheExistResource(resourceNames)) {
            getResourceFromCache(resourceNames);
        } else {
            getResourceFromRemote(resourceNames);
        }
    }

    private Map<String, T> getResourceFromCache(Set<String> resourceNames) {
        return resourceNames.stream()
                .filter(o -> !StringUtils.isEmpty(o))
                .collect(Collectors.toMap(k -> k, this::getCacheResource));
    }

//    从remote获取资源
    public Map<String, T> getResourceFromRemote(Set<String> resourceNames) {
        try {
            resourceLock.lock();
            CompletableFuture<Map<String, T>> future = new CompletableFuture<>();
            observeResourcesName = resourceNames;
            Set<String> consumerObserveResourceNames = new HashSet<>();
            if (resourceNames.isEmpty()) {
                consumerObserveResourceNames.add(emptyResourceName);
            } else {
                consumerObserveResourceNames = resourceNames;
            }

            Consumer<Map<String, T>> futureConsumer = future::complete;
            try {
                writeLock.lock();
                ConcurrentHashMapUtils.computeIfAbsent(
                                (ConcurrentHashMap<Set<String>, List<Consumer<Map<String, T>>>>) consumerObserveMap,
                                consumerObserveResourceNames,
                                key -> new ArrayList<>())
                        .add(futureConsumer);
            } finally {
                writeLock.unlock();
            }

            Set<String> resourceNamesToObserve = new HashSet<>(resourceNames);
            resourceNamesToObserve.addAll(resourcesMap.keySet());
            adsObserver.request(buildDiscoveryRequest(resourceNamesToObserve));
            logger.info("Send xDS Observe request to remote. Resource count: " + resourceNamesToObserve.size()
                    + ". Resource Type: " + getTypeUrl());
        } finally {
            resourceLock.unlock();
        }
        return Collections.emptyMap();
    }

    protected DiscoveryRequest buildDiscoveryRequest(Set<String> resourceNames) {
        return DiscoveryRequest.newBuilder()
                .setNode(node)
                .setTypeUrl(getTypeUrl())
                .addAllResourceNames(resourceNames)
                .build();
    }

    //    protected abstract Map<String, T> decodeDiscoveryResponse(DiscoveryResponse response);

    protected abstract Map<String, T> decodeDiscoveryResponse(DiscoveryResponse response);

    @Override
    public final void process(DiscoveryResponse discoveryResponse) {
        //        Map<String, T> newResult = decodeDiscoveryResponse(discoveryResponse);
        Map<String, T> oldResource = resourcesMap;
        // discoveryResponseListener(oldResource, newResult);

        Map<String, T> newResult = decodeDiscoveryResponse(discoveryResponse);
//        唤醒所有的 resourceListener ，调用 onpudate方法
        resourceListeners.forEach(l -> l.onResourceUpdate(new ArrayList<>(newResult.values())));
//        更新 resourceMap
        resourcesMap = newResult;
    }

    private void discoveryResponseListener(Map<String, T> oldResult, Map<String, T> newResult) {
        Set<String> changedResourceNames = new HashSet<>();
        oldResult.forEach((key, origin) -> {
            if (!Objects.equals(origin, newResult.get(key))) {
                changedResourceNames.add(key);
            }
        });
        newResult.forEach((key, origin) -> {
            if (!Objects.equals(origin, oldResult.get(key))) {
                changedResourceNames.add(key);
            }
        });
        if (changedResourceNames.isEmpty()) {
            return;
        }

        logger.info("Receive resource update notification from xds server. Change resource count: "
                + changedResourceNames.stream() + ". Type: " + getTypeUrl());

        // call once for full data
        try {
            readLock.lock();
            for (Map.Entry<Set<String>, List<Consumer<Map<String, T>>>> entry : consumerObserveMap.entrySet()) {
                if (entry.getKey().stream().noneMatch(changedResourceNames::contains)) {
                    // none update
                    continue;
                }

                Map<String, T> dsResultMap =
                        entry.getKey().stream().collect(Collectors.toMap(k -> k, v -> newResult.get(v)));
                entry.getValue().forEach(o -> o.accept(dsResultMap));
            }
        } finally {
            readLock.unlock();
        }
    }
}
