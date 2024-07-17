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
package org.apache.dubbo.xds.protocol.impl;

import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.xds.AdsObserver;
import org.apache.dubbo.xds.listener.CdsListener;
import org.apache.dubbo.xds.protocol.AbstractProtocol;
import org.apache.dubbo.xds.resource.XdsCluster;
import org.apache.dubbo.xds.resource.XdsEndpoint;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;
import io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.REGISTRY_ERROR_RESPONSE_XDS;

//用于处理xDS协议中的Cluster类型的数据。提供了订阅和解析资源的能力，并提供了相应的解析方法。
public class CdsProtocol extends AbstractProtocol<Cluster> {
    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(CdsProtocol.class);

    public void setUpdateCallback(Consumer<Set<String>> updateCallback) {
        this.updateCallback = updateCallback;
    }

//    java.util.function
    private Consumer<Set<String>> updateCallback;

    /**
     * ，Cluster类型的数据是Envoy代理用来定义其连接池和负载均衡策略的关键配置。
     * Cluster描述了Envoy如何与一组后端服务进行通信，包括后端服务的位置、健康检查设置、负载均衡算法等。
     * @param adsObserver
     * @param node
     * @param checkInterval
     * @param applicationModel
     *
     * Cluster配置的作用是告诉Envoy如何与特定的服务或服务组进行交互。这包括但不限于：
     * 指定后端服务的地址和端口。
     * 配置负载均衡策略，如轮询、最少连接数、随机选择等。
     * 设置健康检查参数，以确保后端服务的可用性。
     * 定义超时和重试机制。
     * 指定TLS/SSL设置，如果通信需要加密的话。
     */
    public CdsProtocol(AdsObserver adsObserver, Node node, int checkInterval, ApplicationModel applicationModel) {
        super(adsObserver, node, checkInterval, applicationModel);
        List<CdsListener> ldsListeners =
                applicationModel.getExtensionLoader(CdsListener.class).getActivateExtensions();
        ldsListeners.forEach(this::registerListen);
    }

    @Override
    public String getTypeUrl() {
        return "type.googleapis.com/envoy.config.cluster.v3.Cluster";
    }

    public void subscribeClusters() {
        subscribeResource(null);
    }

    //    @Override
    //    protected Map<String, String> decodeDiscoveryResponse(DiscoveryResponse response) {
    //        if (getTypeUrl().equals(response.getTypeUrl())) {
    //            Set<String> set = response.getResourcesList().stream()
    //                    .map(CdsProtocol::unpackCluster)
    //                    .filter(Objects::nonNull)
    //                    .map(Cluster::getName)
    //                    .collect(Collectors.toSet());
    //            updateCallback.accept(set);
    //            // Map<String, ListenerResult> listenerDecodeResult = new ConcurrentHashMap<>();
    //            // listenerDecodeResult.put(emptyResourceName, new ListenerResult(set));
    //            // return listenerDecodeResult;
    //        }
    //        return new HashMap<>();
    //    }

    /**
     * 用于解析DiscoveryResponse并返回一个Map，其中包含了Cluster的名称和对应的Cluster对象。
     * @param response
     * @return
     */
    @Override
    protected Map<String, Cluster> decodeDiscoveryResponse(DiscoveryResponse response) {
        if (getTypeUrl().equals(response.getTypeUrl())) {
            return response.getResourcesList().stream()
                    .map(CdsProtocol::unpackCluster)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Cluster::getName, Function.identity()));
        }
        return Collections.emptyMap();
    }

    private ClusterLoadAssignment unpackClusterLoadAssignment(Any any) {
        try {
            return any.unpack(ClusterLoadAssignment.class);
        } catch (InvalidProtocolBufferException e) {
            logger.error(REGISTRY_ERROR_RESPONSE_XDS, "", "", "Error occur when decode xDS response.", e);
            return null;
        }
    }

    public XdsCluster parseCluster(ClusterLoadAssignment cluster) {
        XdsCluster xdsCluster = new XdsCluster();

        xdsCluster.setName(cluster.getClusterName());

//        todo 对于cluster 套cluster的这种情况没有支持
        List<XdsEndpoint> xdsEndpoints = cluster.getEndpointsList().stream()
                .flatMap(e -> e.getLbEndpointsList().stream())
                .map(LbEndpoint::getEndpoint)
                .map(this::parseEndpoint)
                .collect(Collectors.toList());

        xdsCluster.setXdsEndpoints(xdsEndpoints);

        return xdsCluster;
    }

    public XdsEndpoint parseEndpoint(io.envoyproxy.envoy.config.endpoint.v3.Endpoint endpoint) {
        XdsEndpoint xdsEndpoint = new XdsEndpoint();
        xdsEndpoint.setAddress(endpoint.getAddress().getSocketAddress().getAddress());
        xdsEndpoint.setPortValue(endpoint.getAddress().getSocketAddress().getPortValue());
        return xdsEndpoint;
    }

    private static Cluster unpackCluster(Any any) {
        try {
            return any.unpack(Cluster.class);
        } catch (InvalidProtocolBufferException e) {
            logger.error(REGISTRY_ERROR_RESPONSE_XDS, "", "", "Error occur when decode xDS response.", e);
            return null;
        }
    }

    private static HttpConnectionManager unpackHttpConnectionManager(Any any) {
        try {
            if (!any.is(HttpConnectionManager.class)) {
                return null;
            }
            return any.unpack(HttpConnectionManager.class);
        } catch (InvalidProtocolBufferException e) {
            logger.error(REGISTRY_ERROR_RESPONSE_XDS, "", "", "Error occur when decode xDS response.", e);
            return null;
        }
    }
}
