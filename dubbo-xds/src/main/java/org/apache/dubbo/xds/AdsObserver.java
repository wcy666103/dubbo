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
package org.apache.dubbo.xds;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.FrameworkExecutorRepository;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.xds.protocol.AbstractProtocol;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.grpc.stub.StreamObserver;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.REGISTRY_ERROR_REQUEST_XDS;

/**
 * 用于实现与其他支持xDS（eXtensible Discovery Service）协议的服务进行通信的组件。
 * xDS协议是Envoy代理用来动态发现和配置服务的一种机制，而AdsObserver类则是Dubbo框架内部实现这一机制的具体逻辑。
 *
 * 观察并处理 DiscoveryRequest 和 DiscoveryResponse 消息。
 */
public class AdsObserver {
    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(AdsObserver.class);
    private final ApplicationModel applicationModel;
    private final URL url;
    private final Node node;
    private volatile XdsChannel xdsChannel;

    private final Map<String, XdsListener> listeners = new ConcurrentHashMap<>();

    protected StreamObserver<DiscoveryRequest> requestObserver;

    private CompletableFuture<String> future = new CompletableFuture<>();

    private final Map<String, DiscoveryRequest> observedResources = new ConcurrentHashMap<>();

    public AdsObserver(URL url, Node node) {
        this.url = url;
        this.node = node;
        this.xdsChannel = new XdsChannel(url);
        this.applicationModel = url.getOrDefaultApplicationModel();
    }

    public <T> void addListener(AbstractProtocol<T> protocol) {
        listeners.put(protocol.getTypeUrl(), protocol);
    }

    /**
     * 整个方法的目的是初始化或复用一个StreamObserver来发送一个DiscoveryRequest给服务端，并且在主线程上等待响应完成。
     * @param discoveryRequest
     */
    public void request(DiscoveryRequest discoveryRequest) {
        if (requestObserver == null) {
//            如果是空的，说明这是第一次发送请求，因此需要创建一个新的StreamObserver实例
            requestObserver = xdsChannel.createDeltaDiscoveryRequest(new ResponseObserver(this, future));
        }
//      todo Observer的onNext方法跟Stream的onNext方法作用不一样
        //       将discoveryRequest发送给服务端。这触发了流式通信的开始，服务端将根据请求开始发送响应。
        requestObserver.onNext(discoveryRequest);
//        将发送的DiscoveryRequest存储在一个名为observedResources的Map中，键是typeUrl，这可能是为了跟踪已经发送过的请求，以便后续处理或调试。
        observedResources.put(discoveryRequest.getTypeUrl(), discoveryRequest);
        try {
            // TODO：This is to make the child thread receive the information.
            //  Maybe Using CountDownLatch would be better
            String name = Thread.currentThread().getName();
            if ("main".equals(name)) {
                future.get(600, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ResponseObserver implements StreamObserver<DiscoveryResponse> {
        private AdsObserver adsObserver;

        private CompletableFuture future;

        public ResponseObserver(AdsObserver adsObserver, CompletableFuture future) {
            this.adsObserver = adsObserver;
            this.future = future;
        }

        @Override
        public void onNext(DiscoveryResponse discoveryResponse) {
            logger.info("Receive message from server");
            if (future != null) {
                future.complete(null);
            }
            XdsListener xdsListener = adsObserver.listeners.get(discoveryResponse.getTypeUrl());
            xdsListener.process(discoveryResponse);
            adsObserver.requestObserver.onNext(buildAck(discoveryResponse));
        }

        protected DiscoveryRequest buildAck(DiscoveryResponse response) {
            // for ACK
            return DiscoveryRequest.newBuilder()
                    .setNode(adsObserver.node)
                    .setTypeUrl(response.getTypeUrl())
                    .setVersionInfo(response.getVersionInfo())
                    .setResponseNonce(response.getNonce())
                    .addAllResourceNames(adsObserver
                            .observedResources
                            .get(response.getTypeUrl())
                            .getResourceNamesList())
                    .build();
        }

        @Override
        public void onError(Throwable throwable) {
            logger.error(REGISTRY_ERROR_REQUEST_XDS, "", "", "xDS Client received error message! detail:", throwable);
            adsObserver.triggerReConnectTask();
        }

        @Override
        public void onCompleted() {
            logger.info("xDS Client completed");
            adsObserver.triggerReConnectTask();
        }
    }

    private void triggerReConnectTask() {
        ScheduledExecutorService scheduledFuture = applicationModel
                .getFrameworkModel()
                .getBeanFactory()
                .getBean(FrameworkExecutorRepository.class)
                .getSharedScheduledExecutor();
        scheduledFuture.schedule(this::recover, 3, TimeUnit.SECONDS);
    }

    private void recover() {
        try {
            xdsChannel = new XdsChannel(url);
            if (xdsChannel.getChannel() != null) {
                // Child thread not need to wait other child thread.
                requestObserver = xdsChannel.createDeltaDiscoveryRequest(new ResponseObserver(this, null));
                observedResources.values().forEach(requestObserver::onNext);
                return;
            } else {
                logger.error(
                        REGISTRY_ERROR_REQUEST_XDS,
                        "",
                        "",
                        "Recover failed for xDS connection. Will retry. Create channel failed.");
            }
        } catch (Exception e) {
            logger.error(REGISTRY_ERROR_REQUEST_XDS, "", "", "Recover failed for xDS connection. Will retry.", e);
        }
        triggerReConnectTask();
    }

    public void destroy() {
        this.xdsChannel.destroy();
    }
}
